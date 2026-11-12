package com.taverndream.yedu.image

import android.content.Context
import android.graphics.Bitmap
import com.taverndream.yedu.data.GalgameSettings
import com.taverndream.yedu.sd.ImageLog
import com.taverndream.yedu.sd.ImageLogEntry
import com.taverndream.yedu.sd.SdBackendService
import com.taverndream.yedu.sd.SdClient
import com.taverndream.yedu.sd.SdImage
import com.taverndream.yedu.sd.SdModelManager
import com.taverndream.yedu.sd.SdPrompts
import com.taverndream.yedu.sd.UpscalerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * JSON 剧本生图器：只认 JSON 里的完整 prompt，不调用 LLM、不读写世界书。
 * 仍然复用端侧 SD 后端和模型管理，保证独立 APK 可以离线出图。
 */
class GalgameImageGenerator(
    private val context: Context,
    private val settings: GalgameSettings,
) {
    private val sdClient = SdClient()

    private val activeModelId: String
        get() = SdBackendService.activeModelId() ?: settings.sdModelId

    suspend fun generate(
        prompt: String,
        negative: String = "",
        ratio: String = "3:4",
        seed: Long? = null,
        onProgress: (String) -> Unit,
    ): File {
        require(prompt.isNotBlank()) { "图片提示词为空" }
        val finalPrompt = SdPrompts.compose(
            anchors = "",
            style = settings.styleTags,
            scene = prompt,
            maxTokens = 100,
            prefix = SdModelManager.promptPrefix(activeModelId),
            quality = SdModelManager.promptQuality(activeModelId),
        )
        android.util.Log.i("TD-GalgameSD", "image prompt=$finalPrompt")
        return generateFile(
            prompt = finalPrompt,
            negative = negative.ifBlank { settings.sdNegative },
            onProgress = onProgress,
            seed = seed ?: freshSeed(),
            ratio = ratio,
        )
    }

    private suspend fun generateFile(
        prompt: String,
        negative: String,
        onProgress: (String) -> Unit,
        seed: Long,
        ratio: String,
    ): File {
        val model = SdModelManager.models(context)
            .firstOrNull { it.id == activeModelId }
            ?: error("生图模型未找到，请到设置里选择模型")
        if (!SdModelManager.isDownloaded(context, model)) {
            error("生图模型未下载，请到设置里下载")
        }
        if (model.isSdxl) {
            val availMb = withContext(Dispatchers.IO) { availableMemoryMb() }
            if (availMb in 1 until 3000) {
                error("可用内存仅 ${availMb}MB，SDXL 至少需要 3GB，请先清理后台再试")
            }
        }
        if (!withContext(Dispatchers.IO) { sdClient.healthy() }) {
            onProgress("启动生图引擎…")
            SdBackendService.start(context, model.id)
            val waitSec = if (model.isSdxl) 240 else 90
            var waited = 0
            while (waited < waitSec) {
                delay(1000)
                waited++
                if (withContext(Dispatchers.IO) { sdClient.healthy() }) break
            }
            if (!withContext(Dispatchers.IO) { sdClient.healthy() }) {
                error("生图后端启动超时")
            }
        }
        val size = model.nativeSize
        withContext(Dispatchers.IO) {
            ImageLog.append(
                context,
                ImageLogEntry(
                    source = "galgame_json",
                    model = model.id,
                    prompt = prompt,
                    negative = negative,
                    seed = seed,
                ),
            )
        }
        val img = withContext(Dispatchers.IO) {
            sdClient.generate(
                prompt = prompt,
                negative = negative,
                steps = settings.sdSteps,
                cfg = settings.sdCfg,
                width = size,
                height = size,
                seed = seed,
                scheduler = model.scheduler,
            ) { step, total, _ ->
                onProgress("生成中 $step/$total")
            }
        }
        onProgress(
            if (settings.sdUpscale && img.width <= 640 && UpscalerManager.isDownloaded(context)) {
                "超分中…"
            } else {
                "保存中…"
            }
        )
        return withContext(Dispatchers.IO) { renderFinal(img, ratio) }
    }

    private fun renderFinal(img: SdImage, ratio: String): File {
        val (tw, th) = targetSize(img.width, img.height, ratio)
        val cropped = cropRgb(img, tw, th)
        val base = rgbToBitmap(cropped)
        val useUp = settings.sdUpscale && cropped.width <= 640 &&
            UpscalerManager.isDownloaded(context)
        val out = if (useUp) {
            runCatching {
                sdClient.upscale(
                    rgb = cropped.bytes,
                    width = cropped.width,
                    height = cropped.height,
                    upscalerPath = UpscalerManager.file(context).absolutePath,
                )
            }.getOrElse {
                android.util.Log.w("TD-GalgameSD", "超分失败，退回原尺寸：${it.message}")
                base
            }
        } else {
            base
        }
        if (out !== base) base.recycle()
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        out.recycle()
        return f
    }

    private fun availableMemoryMb(): Long = runCatching {
        File("/proc/meminfo").readLines()
            .firstOrNull { it.startsWith("MemAvailable:") }
            ?.filter { it.isDigit() }
            ?.toLongOrNull()
            ?.div(1024) ?: -1L
    }.getOrDefault(-1L)

    private fun freshSeed(): Long = System.nanoTime() and 0x7FFFFFFFL

    private fun targetSize(w: Int, h: Int, ratio: String): Pair<Int, Int> = when (ratio) {
        "4:3" -> w to (w * 3 / 4)
        "3:4" -> (h * 3 / 4) to h
        else -> minOf(w, h) to minOf(w, h)
    }

    private fun cropRgb(img: SdImage, tw: Int, th: Int): SdImage {
        if (tw == img.width && th == img.height) return img
        val ch = img.channels.coerceAtLeast(3)
        val ox = (img.width - tw) / 2
        val oy = (img.height - th) / 2
        val out = ByteArray(tw * th * ch)
        val src = img.bytes
        var di = 0
        for (y in 0 until th) {
            val so = ((y + oy) * img.width + ox) * ch
            System.arraycopy(src, so, out, di, tw * ch)
            di += tw * ch
        }
        return SdImage(tw, th, ch, out)
    }

    private fun rgbToBitmap(img: SdImage): Bitmap {
        val w = img.width
        val h = img.height
        val ch = img.channels.coerceAtLeast(3)
        val src = img.bytes
        val px = IntArray(w * h)
        for (i in 0 until w * h) {
            val o = i * ch
            val a = if (ch >= 4) src[o + 3].toInt() and 0xFF else 0xFF
            val r = src[o].toInt() and 0xFF
            val g = src[o + 1].toInt() and 0xFF
            val b = src[o + 2].toInt() and 0xFF
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }
}