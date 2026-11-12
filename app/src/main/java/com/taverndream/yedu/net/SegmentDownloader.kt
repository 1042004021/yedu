package com.taverndream.yedu.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * aria2 式的多连接分块下载：
 * - 文件按固定大小切成小块，多个 worker 动态领取；
 * - 某一路慢不会拖住整份文件，其余 worker 会继续消化后面的块；
 * - 每个块独立断点续传，杀进程后重新进入可接着下。
 *
 * 不直接嵌入 aria2 二进制，避免 GPL、ABI 和额外进程管理成本。
 */
object SegmentDownloader {

    /** 块太大时一个慢连接会拖尾，太小又增加请求开销；8MB 在手机网络下比较均衡。 */
    private const val BLOCK_SIZE = 8L * 1024 * 1024
    private const val MAX_RETRY = 20
    private const val LAYOUT = "blocks-v2"

    data class Probe(val total: Long, val rangeSupported: Boolean)

    /** 用 1 字节 Range 探测大小，同时判断服务器是否支持分段。 */
    fun probe(client: OkHttpClient, url: String): Probe {
        val req = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        return client.newCall(req).execute().use { resp ->
            val range = resp.header("Content-Range")
            if (resp.code == 206 && range != null) {
                val total = range.substringAfterLast('/').toLongOrNull() ?: -1L
                if (total > 0) return@use Probe(total, true)
            }
            val length = resp.body?.contentLength() ?: -1L
            Probe(if (length > 0) length else -1L, false)
        }
    }

    /**
     * 下载到 workDir 的 b00000.part、b00001.part…，返回按顺序排列的分块文件。
     * 调用方按顺序拼接即可得到完整文件。
     */
    suspend fun download(
        client: OkHttpClient,
        url: String,
        workDir: File,
        total: Long,
        connections: Int,
        onProgress: (Long, Long) -> Unit,
    ): List<File> = coroutineScope {
        val blockCount = ((total + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt().coerceAtLeast(1)
        val marker = File(workDir, "total")
        val layout = File(workDir, "layout")
        if (!marker.exists() || marker.readText().trim() != total.toString() ||
            !layout.exists() || layout.readText().trim() != LAYOUT
        ) {
            // 旧版固定分段（0.part、1.part…）不能直接复用，整体清掉重来
            workDir.deleteRecursively()
            workDir.mkdirs()
            marker.writeText(total.toString())
            layout.writeText(LAYOUT)
        }

        val blocks = (0 until blockCount).map { index ->
            File(workDir, String.format(Locale.US, "b%05d.part", index))
        }
        val done = AtomicLong(0L)
        blocks.forEach { if (it.exists()) done.addAndGet(it.length()) }
        onProgress(done.get(), total)

        val next = AtomicInteger(0)
        val workerCount = minOf(connections.coerceIn(1, 32), blockCount)
        (0 until workerCount).map {
            async(Dispatchers.IO) {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= blockCount) break
                    val start = index.toLong() * BLOCK_SIZE
                    val end = minOf(start + BLOCK_SIZE, total)
                    segment(
                        client = client,
                        url = url,
                        file = blocks[index],
                        start = start,
                        end = end,
                        done = done,
                        total = total,
                        onProgress = onProgress,
                    )
                }
            }
        }.awaitAll()
        blocks
    }

    private suspend fun segment(
        client: OkHttpClient,
        url: String,
        file: File,
        start: Long,
        end: Long,
        done: AtomicLong,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val expected = end - start
        var attempt = 0
        while (true) {
            var have = if (file.exists()) file.length() else 0L
            if (have > expected) {
                done.addAndGet(-have)
                file.delete()
                have = 0L
            }
            if (have == expected) return@withContext
            try {
                val req = Request.Builder().url(url)
                    .header("Range", "bytes=${start + have}-${end - 1}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code != 206) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("空响应")
                    var written = have
                    var last = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(file, true).use { out ->
                            val buf = ByteArray(128 * 1024)
                            while (written < expected) {
                                val n = input.read(
                                    buf,
                                    0,
                                    minOf(buf.size.toLong(), expected - written).toInt(),
                                )
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                written += n
                                done.addAndGet(n.toLong())
                                val now = System.currentTimeMillis()
                                if (now - last > 400) {
                                    last = now
                                    onProgress(done.get(), total)
                                }
                            }
                        }
                    }
                    onProgress(done.get(), total)
                }
                if (file.length() == expected) return@withContext
                throw IllegalStateException("连接提前结束")
            } catch (t: Throwable) {
                attempt++
                if (attempt > MAX_RETRY) throw t
                delay(1000L * minOf(attempt, 5))
            }
        }
    }
}