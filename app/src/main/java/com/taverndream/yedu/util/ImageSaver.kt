package com.taverndream.yedu.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/** 把生成的特写/场景图保存到系统相册。 */
object ImageSaver {

    suspend fun saveToGallery(context: Context, source: File): String = withContext(Dispatchers.IO) {
        require(source.isFile) { "图片文件不存在" }
        val mime = if (source.extension.equals("png", ignoreCase = true)) {
            "image/png"
        } else {
            "image/jpeg"
        }
        val stamp = System.currentTimeMillis()
        val name = "galgame_${stamp}.${source.extension.lowercase(Locale.ROOT)}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, source, name, mime)
        } else {
            saveLegacy(context, source, name, mime)
        }
    }

    private fun saveWithMediaStore(
        context: Context,
        source: File,
        name: String,
        mime: String,
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Galgame",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法写入系统相册")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法打开相册输出流")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Pictures/Galgame/$name"
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        source: File,
        name: String,
        mime: String,
    ): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Galgame",
        ).apply { mkdirs() }
        val target = File(dir, name)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(mime),
            null,
        )
        return target.absolutePath
    }
}