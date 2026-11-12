package com.taverndream.yedu.sd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Build
import com.taverndream.yedu.net.SegmentDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Enumeration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** 端侧 SD 模型：一个 zip（HuggingFace 上托管），解压到 filesDir/models/<id>/。 */
data class SdModel(
    val id: String,
    val name: String,
    val fileUri: String,
    val runOnCpu: Boolean,
    val useCpuClip: Boolean,
    val isSdxl: Boolean = false,
    val approxSize: String = "",
    /** 模型原生出图边长：SD1.5 是 512，SDXL 是 1024。 */
    val nativeSize: Int = 512,
    /** 文本嵌入维度，透传给后端的 --text_embedding_size。 */
    val textEmbeddingSize: Int = 768,
    /** SD2.1 用 v-prediction，需要 --use_v_pred。 */
    val useVpred: Boolean = false,
    /**
     * 传给后端的采样器。dpm（DPM++ 2M）是 SD1.5/2.1 的默认值；
     * SDXL 必须用 euler_a —— 实测 DPM++ 2M 在这套 QNN SDXL 图上会输出纯噪声。
     */
    val scheduler: String = "dpm",
    /** 非 zip 模型：远端相对路径 -> 本地文件名。为空时走 zip 流程。 */
    val remoteFiles: List<Pair<String, String>> = emptyList(),
)

object SdModelManager {

    private const val PORT = 8081

    /** 默认生图模型：列表里的写实人像首选。 */
    const val DEFAULT_ID = "realvisxl"

    /** 已从列表移除的旧模型；升级后清理一次本地文件。SD1.5 已重新接入，不再清理。 */
    private val LEGACY_IDS = listOf("sd21")

    private const val MIGRATION_PREFS = "sd_model_migration"
    private const val KEY_LEGACY_PURGED = "legacy_under_1b_purged"

    private val chipsets = mapOf(
        "SM8475" to "8gen1", "SM8450" to "8gen1",
        "SM8550" to "8gen2", "SM8550P" to "8gen2", "QCS8550" to "8gen2", "QCM8550" to "8gen2",
        "SM8650" to "8gen2", "SM8650P" to "8gen2",
        "SM8750" to "8gen2", "SM8750P" to "8gen2",
        "SM8850" to "8gen2", "SM8850P" to "8gen2",
        "SM8735" to "8gen2", "SM8845" to "8gen2",
    )

    fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "CPU"

    /** 与 local-dream 一致的芯片映射；非高通返回 null（不支持 NPU 模型）。 */
    fun suffix(): String? {
        chipsets[soc()]?.let { return it }
        if (!isQualcomm()) return null
        return if (soc().startsWith("SM")) "min" else null
    }

    fun isQualcomm(): Boolean {
        val soc = soc().uppercase()
        val prefixes = listOf(
            "SM", "QCS", "QCM", "CQ", "IPQ", "SXR", "AIC", "SSG",
            "SC", "SA", "SDM", "MSM", "QRB", "X1E", "X1P"
        )
        return prefixes.any { soc.startsWith(it) }
    }

    /** SDXL 条目模板：8gen3 包共用，1024 原生，必须 euler_a。 */
    private fun xl(id: String, name: String, file: String, size: String = "3.5GB") = SdModel(
        id = id,
        name = name,
        fileUri = "xororz/sdxl-qnn/resolve/main/$file",
        runOnCpu = false,
        useCpuClip = true,
        isSdxl = true,
        approxSize = size,
        nativeSize = 1024,
        textEmbeddingSize = 768,
        scheduler = "euler_a",
    )

    /** SD1.5 条目模板：约 1GB，512 原生，DPM++ 2M。 */
    private fun sd15(
        suffix: String,
        id: String,
        name: String,
        stem: String,
        size: String = "1.0GB",
    ) = SdModel(
        id = id,
        name = name,
        fileUri = "xororz/sd-qnn/resolve/main/${stem}_qnn2.28_$suffix.zip",
        runOnCpu = false,
        useCpuClip = true,
        isSdxl = false,
        approxSize = size,
        nativeSize = 512,
        textEmbeddingSize = 768,
        scheduler = "dpm",
    )

    /**
     * 当前可下载的端侧生图模型：
     * - SD1.5：约 1GB，512 原生，适合中低端机型和快速出图；
     * - SDXL：约 3.3GB，1024 原生，适合 8gen3 及以上。
     *
     * 暂不列入：SD2.1、dmd2 少步蒸馏版（需 4–8 步低 CFG）、noobai_vpred（v-pred）、
     * RealisticVisionHyper（需 8 步 / CFG 1.5，当前全局参数不适合）、
     * 用户明确不要的兽人模型 CrossKemono，以及官方 SDXL Base。
     */
    fun models(context: Context): List<SdModel> {
        // 非高通设备没有可用的 NPU 模型
        val s = suffix() ?: return emptyList()
        return listOf(
            // ---- SD1.5 · 写实 ----
            sd15(s, "absolute_reality", "AbsoluteReality（写实人像）", "AbsoluteReality"),
            sd15(s, "chilloutmix", "ChilloutMix（亚洲写实人像）", "ChilloutMix"),
            sd15(s, "majicmix", "MajicMix Realistic V7（写实人像）", "MajicmixRealisticV7"),

            // ---- SD1.5 · 半写实 / 通用 ----
            sd15(s, "dreamshaper", "DreamShaper V8（通用插画）", "DreamShaperV8"),
            sd15(s, "realisianv6", "Realisian V6（半写实人像）", "RealisianV6"),
            sd15(s, "neverendingdream", "NeverEnding Dream V122（梦境插画）", "NeverEndingDreamV122"),
            sd15(s, "unstableillusion", "Unstable Illusion（幻想动漫）", "unStableIllusion"),
            sd15(s, "hyperspirev5", "HyperSpire V5（奇幻插画）", "HyperSpireV5"),

            // ---- SD1.5 · 二次元 ----
            sd15(s, "anythingv5", "Anything V5（二次元通用）", "AnythingV5"),
            sd15(s, "meinamix", "MeinaMix V12（精致二次元）", "MeinaMixV12"),
            sd15(s, "nai_anime_v2", "Nai Anime V2（动漫角色）", "NaiAnimeV2"),
            sd15(s, "mistoonanimev3", "Mistoon Anime V3（日系动漫）", "MistoonAnimeV3"),
            sd15(s, "counterfeitv30", "Counterfeit V3.0（干净线条动漫）", "counterfeitV30"),
            sd15(s, "darksushiv4", "DarkSushi V4（暗色动漫）", "DarkSushiV4"),
            sd15(s, "cuteyukimix", "CuteYukiMix（甜美二次元）", "CuteYukiMix"),
            sd15(s, "qteamix", "QteaMix（Q 版可爱）", "QteaMix"),
            sd15(s, "sweetmixv22", "SweetMix V22 Flat（平涂插画）", "SweetMixV22Flat"),
            sd15(s, "superinvinciblev2", "SuperInvincible V2（二次元）", "superinvinciblev2"),

            // ---- SDXL · 写实 ----
            xl("realvisxl", "RealVisXL V5（写实人像）", "realvis_xl_v5_qnn2.28_8gen3.zip", "3.3GB"),
            xl("cyberrealistic", "CyberRealistic v10（摄影写实）", "cyber_realistic_v10_qnn2.28_8gen3.zip"),
            xl("epicrealism", "epiCRealism（电影感写实）", "epic_realism_qnn2.28_8gen3.zip", "3.3GB"),
            xl("dreamshaperxl", "DreamShaper XL（通用奇幻插画）", "dreamshaper_qnn2.28_8gen3.zip"),
            xl("gonzalomo", "GonzaloMo v7（摄影写实）", "gonzalomo_v7_qnn2.28_8gen3.zip"),
            xl("intorealism", "IntoRealism Ultra v11（高细节写实）", "intorealism_ultra_v11_qnn2.28_8gen3.zip"),
            xl("juggernaut", "Juggernaut XL（通用写实）", "juggernaut_qnn2.28_8gen3.zip"),
            xl("miaomiaorealskin", "Miaomiao RealSkin v1.4（真人皮肤）", "miaomiao_realskin_v1.4_qnn2.28_8gen3.zip", "3.2GB"),
            xl("perfectionreal", "Perfection Realistic v8（干净写实）", "perfection_realistic_v8_qnn2.28_8gen3.zip"),

            // ---- 半写实 ----
            xl("perfectdeliberate", "PerfectDeliberate v9（半写实/二次元通用）", "perfect_deliberate_v9_qnn2.28_8gen3.zip"),
            xl("chenkinnoob", "Chenkinnoob v0.5（2.5D 半写实）", "chenkinnoob_v0.5_qnn2.28_8gen3.zip", "3.2GB"),

            // ---- 二次元 ----
            xl("illustrious", "Illustrious v16（SDXL 高清二次元）", "illustrious_v16_qnn2.28_8gen3.zip"),
            xl("illustrious17", "Illustrious v17（二次元升级）", "illustrious_v17_qnn2.28_8gen3.zip"),
            xl("illustrij", "Illustrij v21（二次元混模）", "illustrij_v21_qnn2.28_8gen3.zip"),
            xl("prefectillustrious", "Prefect Illustrious v8（二次元精修）", "prefect_illustrious_v8_qnn2.28_8gen3.zip"),
            xl("animagine", "Animagine XL v4（动漫角色 tag）", "animagine_v4_qnn2.28_8gen3.zip"),
            xl("anikawa", "Anikawa v4（日系动漫）", "anikawa_v4_qnn2.28_8gen3.zip"),
            xl("counterfeit", "Counterfeit v2.5（干净线条二次元）", "counterfeit_v2.5_qnn2.28_8gen3.zip"),
            xl("lemonsugar", "LemonSugarMix v3（柔和可爱）", "lemonsugarmix_v3_qnn2.28_8gen3.zip"),
            xl("miaomiaoharem", "Miaomiao Harem v2（二次元美少女）", "miaomiao_harem_v2_qnn2.28_8gen3.zip", "3.2GB"),
            xl("novaorange", "Nova Orange Rex v1（鲜艳动漫）", "nova_orange_rex_v1_qnn2.28_8gen3.zip"),
            xl("novaanime18", "NovaAnime v18（通用二次元）", "novaanime_v18_qnn2.28_8gen3.zip"),
            xl("novaanime19", "NovaAnime v19（二次元升级）", "novaanime_v19_qnn2.28_8gen3.zip"),
            xl("pppanimix", "PPP Animix v20（插画动漫）", "pppanimix_v20_qnn2.28_8gen3.zip"),
            xl("raehoshi", "Raehoshi v10（柔和唯美）", "raehoshi_v10_qnn2.28_8gen3.zip"),
            xl("rinblend", "Rin Anime Blend v4（动漫混模）", "rin_anime_blend_v4_qnn2.28_8gen3.zip"),
            xl("rinpopcute", "Rin AnimePopCute v4（可爱流行）", "rin_animepopcute_v4_qnn2.28_8gen3.zip"),
            xl("rinfeather", "Rin Featherfall v4（奇幻动漫）", "rin_featherfall_v4_qnn2.28_8gen3.zip"),
            xl("rinflanime", "Rin Flanime v4（平涂动漫）", "rin_flanime_v4_qnn2.28_8gen3.zip"),
            xl("ponydiffusion", "PonyDiffusion v6XL（Pony 系，需 score 标签）", "ponydiffusion_v6xl_qnn2.28_8gen3.zip"),

            // ---- 成人向写实 ----
            xl("reedxxx", "Reed XXX v14（成人向写实）", "reed_xxx_v14_qnn2.28_8gen3.zip"),
        )
    }

    /** 生图模型的提示词风格；Pony 系需要 score_* 前缀，写实系不该带动漫质量词。 */
    enum class PromptStyle { REALISTIC, SEMI, ANIME, PONY }

    private val sd15Ids = setOf(
        "absolute_reality", "chilloutmix", "majicmix",
        "dreamshaper", "realisianv6", "neverendingdream", "unstableillusion", "hyperspirev5",
        "anythingv5", "meinamix", "nai_anime_v2", "mistoonanimev3", "counterfeitv30",
        "darksushiv4", "cuteyukimix", "qteamix", "sweetmixv22", "superinvinciblev2",
    )
    private val realisticIds = setOf(
        "absolute_reality", "chilloutmix", "majicmix",
        "realvisxl", "cyberrealistic", "epicrealism", "dreamshaperxl", "gonzalomo",
        "intorealism", "juggernaut", "miaomiaorealskin", "perfectionreal", "reedxxx",
    )
    private val semiIds = setOf(
        "dreamshaper", "realisianv6", "neverendingdream", "unstableillusion", "hyperspirev5",
        "perfectdeliberate", "chenkinnoob",
    )

    fun promptStyle(id: String): PromptStyle = when (id) {
        in realisticIds -> PromptStyle.REALISTIC
        in semiIds -> PromptStyle.SEMI
        "ponydiffusion" -> PromptStyle.PONY
        else -> PromptStyle.ANIME
    }

    /** 必须放在提示词最前面的 tag；目前只有 Pony 系需要。 */
    fun promptPrefix(id: String): String =
        if (promptStyle(id) == PromptStyle.PONY) {
            "score_9, score_8_up, score_7_up, score_6_up, score_5_up, score_4_up, source_anime"
        } else ""

    /** 告诉对话模型当前生图模型吃什么样的提示词。 */
    fun promptGuide(id: String): String {
        val family = if (id in sd15Ids) "SD1.5（512 原生，tag 保持短）" else "SDXL"
        return when (promptStyle(id)) {
            PromptStyle.REALISTIC ->
                "目标生图模型是写实向 $family，用摄影/自然语言 tag（如 raw photo, photorealistic, soft lighting），不要用 score_* 或动漫质量词。"
            PromptStyle.SEMI ->
                "目标生图模型是半写实 $family，可混用写实与动漫 tag，突出皮肤质感和光影，不要用 score_*。"
            PromptStyle.ANIME ->
                "目标生图模型是动漫向 $family，用 danbooru 风格 tag，可加 masterpiece, best quality，不要用 score_*。"
            PromptStyle.PONY ->
                "目标生图模型基于 Pony Diffusion V6 XL，必须用 danbooru tag，并在最前面包含 score_9, score_8_up, score_7_up, score_6_up, score_5_up, score_4_up, source_anime。"
        }
    }

    /** 拼在提示词末尾的质量词，按模型风格区分。 */
    fun promptQuality(id: String): String = when (promptStyle(id)) {
        PromptStyle.REALISTIC -> "photorealistic, raw photo, 8k uhd"
        else -> "masterpiece, best quality, highly detailed"
    }

    /**
     * 生图模型查找顺序：
     * 1. App 内部 files/models；
     * 2. App 专属外部目录 Android/data/<pkg>/files/models；
     * 3. 公共存储里的 Galgame / TavernDream / Download 目录；
     * 4. 用户在设置里追加的自定义父目录。
     *
     * 后三种在 Android 11+ 需要「所有文件访问」权限，Android 10 及以下需要读取存储权限。
     * 下载仍然写入内部目录；外部目录只读优先，方便用户自己拷模型。
     */
    fun rootDirs(context: Context): List<File> {
        val roots = mutableListOf<File>()
        roots += File(context.filesDir, "models")
        context.getExternalFilesDir(null)?.let { roots += File(it, "models") }
        val sd = Environment.getExternalStorageDirectory()
        roots += File(sd, "Yedu/models")
        roots += File(sd, "Galgame/models")
        roots += File(sd, "TavernDream/models")
        roots += File(sd, "Download/Yedu/models")
        roots += File(sd, "Download/Galgame/models")
        roots += File(sd, "Download/TavernDream/models")
        roots += extraDirs(context)
        return roots.distinctBy { it.absolutePath }
    }

    fun extraDirsText(context: Context): String =
        context.getSharedPreferences(PATHS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EXTRA_DIRS, "") ?: ""

    fun setExtraDirsText(context: Context, value: String) {
        context.getSharedPreferences(PATHS_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_EXTRA_DIRS, value).apply()
    }

    fun extraDirs(context: Context): List<File> =
        extraDirsText(context)
            .lineSequence()
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { File(it) }
            .toList()

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** 在任意根目录里找到已经放好的模型目录；只读检查，不创建目录。 */
    fun findDir(context: Context, id: String): File? =
        rootDirs(context)
            .map { File(it, id) }
            .firstOrNull { d ->
                File(d, "unet.bin").isFile || File(d, "unet.mnn").isFile
            }

    /** 下载/解压目标：优先复用已存在的模型目录，否则使用内部目录。 */
    fun dir(context: Context, id: String): File =
        findDir(context, id) ?: File(context.filesDir, "models/$id").apply { if (!exists()) mkdirs() }

    fun isDownloaded(context: Context, model: SdModel): Boolean =
        findDir(context, model.id) != null

    /** 删除所有根目录里的同名模型及可能残留的缓存 zip。调用前必须确认引擎已停止。 */
    suspend fun delete(context: Context, model: SdModel) = withContext(Dispatchers.IO) {
        var deleted = false
        rootDirs(context).map { File(it, model.id) }.forEach { d ->
            if (d.exists()) {
                require(d.deleteRecursively()) { "删除失败，可能有文件正在使用：${d.absolutePath}" }
                deleted = true
            }
        }
        if (!deleted) {
            val internal = File(context.filesDir, "models/${model.id}")
            if (internal.exists()) require(internal.deleteRecursively()) { "删除失败" }
        }
        File(context.cacheDir, "${model.id}.zip").delete()
        File(context.cacheDir, "${model.id}.parts").deleteRecursively()
    }

    /**
     * 升级迁移：删除仍处于下架状态的 SD2.1 模型文件，只执行一次。
     * SD1.5 已重新接入，不再清理。只动 files/models/<id> 和 cacheDir 里的下载缓存。
     */
    suspend fun purgeLegacy(context: Context): Long = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_PURGED, false)) return@withContext 0L
        var freed = 0L
        var ok = true
        for (id in LEGACY_IDS) {
            val dir = File(context.filesDir, "models/$id")
            if (dir.exists()) {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (dir.deleteRecursively()) freed += size else ok = false
            }
            val zip = File(context.cacheDir, "$id.zip")
            if (zip.exists()) {
                val size = zip.length()
                if (zip.delete()) freed += size else ok = false
            }
            if (!File(context.cacheDir, "$id.parts").deleteRecursively()) ok = false
        }
        // 删不干净就不写标记，下次启动再试，避免旧文件永远留在手机上
        if (ok) prefs.edit().putBoolean(KEY_LEGACY_PURGED, true).apply()
        else android.util.Log.w("TD-SD", "旧模型清理未完全成功，下次启动重试")
        android.util.Log.i("TD-SD", "已清理下架旧模型：${freed / 1048576}MB")
        freed
    }

    private const val PATHS_PREFS = "model_paths"
    private const val KEY_EXTRA_DIRS = "extraDirs"

    sealed class State {
        data object Idle : State()
        data class Downloading(val progress: Float, val bytes: Long, val total: Long) : State()
        data object Extracting : State()
        data object Done : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** 单个文件最多重试几次（每次都会从断点续传）。 */
    private const val MAX_RETRY = 20

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        model: SdModel,
        mirror: String,
        threads: Int = 8,
    ) = withContext(Dispatchers.IO) {
        if (model.remoteFiles.isNotEmpty()) {
            downloadFiles(context, model, mirror)
            return@withContext
        }
        val url = mirror.trimEnd('/') + "/" + model.fileUri
        val zip = File(context.cacheDir, "${model.id}.zip")
        val parts = File(context.cacheDir, "${model.id}.parts")
        try {
            _state.value = State.Downloading(0f, 0, 0)
            // 旧版本可能留下单连接 zip；分段下载不再需要它
            zip.delete()
            val probe = SegmentDownloader.probe(client, url)
            if (!probe.rangeSupported || probe.total <= 0) {
                // 服务器不支持 Range：退回单连接，无法断点续传
                parts.deleteRecursively()
                zip.delete()
                downloadSingle(url, zip)
                zip.inputStream().use { extractZip(it, dir(context, model.id)) }
                zip.delete()
            } else {
                val segments = SegmentDownloader.download(client, url, parts, probe.total, threads) { done, total ->
                    _state.value = State.Downloading(
                        if (total > 0) done.toFloat() / total else 0f, done, total
                    )
                }
                _state.value = State.Extracting
                // 边拼边解压，避免再多占一份 3.5GB
                val lazyStreams = object : Enumeration<InputStream> {
                    private var index = 0
                    override fun hasMoreElements() = index < segments.size
                    override fun nextElement(): InputStream = segments[index++].inputStream()
                }
                SequenceInputStream(lazyStreams).use { extractZip(it, dir(context, model.id)) }
                parts.deleteRecursively()
            }
            _state.value = State.Done
        } catch (t: Throwable) {
            _state.value = State.Failed(t.message ?: "下载失败")
        }
    }

    private fun downloadSingle(url: String, zip: File) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("空响应")
            val total = body.contentLength()
            var done = 0L
            var last = 0L
            body.byteStream().use { input ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 400) {
                            last = now
                            _state.value = State.Downloading(
                                if (total > 0) done.toFloat() / total else 0f, done, total
                            )
                        }
                    }
                }
            }
        }
    }

    private fun extractZip(input: InputStream, dest: File) {
        dest.mkdirs()
        ZipInputStream(input.buffered()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val name = e.name.substringAfterLast('/')
                    if (name.isNotEmpty() && !name.startsWith(".") && !name.startsWith("__MACOSX")) {
                        FileOutputStream(File(dest, name)).use { o -> zis.copyTo(o) }
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }

    /** SD2.1 这类散文件模型：逐个下载到 models/<id>/，进度按文件数加权。 */
    private fun downloadFiles(context: Context, model: SdModel, mirror: String) {
        val dest = dir(context, model.id).apply { mkdirs() }
        val n = model.remoteFiles.size
        try {
            _state.value = State.Downloading(0f, 0, 0)
            model.remoteFiles.forEachIndexed { index, (remote, local) ->
                val url = mirror.trimEnd('/') + "/" + remote
                val target = File(dest, local)
                val tmp = File(dest, "$local.part")
                fetchResumable(url, local, tmp) { done, total ->
                    val inner = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _state.value = State.Downloading(
                        progress = ((index + inner) / n).coerceIn(0f, 1f),
                        bytes = done,
                        total = total,
                    )
                }
                if (tmp.length() <= 0L) throw IllegalStateException("$local 下载不完整")
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw IllegalStateException("$local 保存失败")
            }
            _state.value = State.Done
        } catch (t: Throwable) {
            _state.value = State.Failed(t.message ?: "下载失败")
        }
    }

    /**
     * 带断点续传的下载。
     *
     * aifasthub 的连接跑一段时间就会被服务端掐断（实测 20~160MB 不等），所以这里
     * 用 Range 从 .part 已有长度接着下，最多重试 [MAX_RETRY] 次；用户重新点下载时也能续上。
     */
    private fun fetchResumable(
        url: String,
        label: String,
        tmp: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        var attempt = 0
        var droppedToZero = false
        while (true) {
            val have = if (tmp.exists()) tmp.length() else 0L
            val req = Request.Builder().url(url).apply {
                if (have > 0) header("Range", "bytes=$have-")
            }.build()
            var total = 0L
            var wrote = have
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.code != 200 && resp.code != 206) {
                        throw IllegalStateException("HTTP ${resp.code}（$label）")
                    }
                    if (resp.code == 200 && have > 0) {
                        // 服务端不支持 Range，只能从头再来一次
                        tmp.delete()
                        if (droppedToZero) throw IllegalStateException("服务端不支持断点续传")
                        droppedToZero = true
                        return fetchResumable(url, label, tmp, onProgress)
                    }
                    val body = resp.body ?: throw IllegalStateException("空响应")
                    val len = body.contentLength()
                    total = if (len > 0) have + len else 0L
                    var last = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(tmp, true).use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val m = input.read(buf)
                                if (m <= 0) break
                                out.write(buf, 0, m)
                                wrote += m
                                val now = System.currentTimeMillis()
                                if (now - last > 400) {
                                    last = now
                                    onProgress(wrote, total)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                attempt++
                val finished = total > 0 && tmp.length() >= total
                if (finished || attempt > MAX_RETRY) throw t
                Thread.sleep(1000L * attempt)
                continue
            }
            if (total > 0 && tmp.length() < total) {
                attempt++
                if (attempt > MAX_RETRY) throw IllegalStateException("下载中断（$label）")
                Thread.sleep(1000L * attempt)
                continue
            }
            onProgress(wrote, total)
            return
        }
    }

    fun port(): Int = PORT
}
