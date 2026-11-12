package com.taverndream.yedu.sd

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 一次实际出图的最终参数，便于排查画风、提示词和种子问题。 */
@Serializable
data class ImageLogEntry(
    val at: Long = System.currentTimeMillis(),
    val source: String = "",
    val model: String = "",
    val prompt: String = "",
    val negative: String = "",
    val seed: Long = 0L,
)

/** 生图日志：JSONL 追加到 filesDir/image_logs.jsonl，最多保留最近 200 条。 */
object ImageLog {

    private const val FILE_NAME = "image_logs.jsonl"
    private const val MAX_ENTRIES = 200
    private const val MAX_BYTES = 512 * 1024
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun append(context: Context, entry: ImageLogEntry) {
        synchronized(lock) {
            val f = file(context)
            if (f.length() > MAX_BYTES) {
                val kept = f.readLines().takeLast(MAX_ENTRIES / 2)
                f.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"))
            }
            f.appendText(json.encodeToString(entry) + "\n")
        }
    }

    /** 最新的在前。 */
    fun read(context: Context, limit: Int = 30): List<ImageLogEntry> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return synchronized(lock) {
            f.readLines().asReversed().mapNotNull { line ->
                runCatching { json.decodeFromString<ImageLogEntry>(line) }.getOrNull()
            }.take(limit.coerceAtLeast(1))
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            file(context).delete()
        }
    }
}