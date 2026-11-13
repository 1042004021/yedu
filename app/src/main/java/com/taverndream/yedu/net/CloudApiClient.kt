package com.taverndream.yedu.net

import com.taverndream.yedu.data.CloudDeviceSession
import com.taverndream.yedu.data.CloudDownloadFile
import com.taverndream.yedu.data.CloudDownloadResponse
import com.taverndream.yedu.data.CloudDraft
import com.taverndream.yedu.data.CloudPresignRequest
import com.taverndream.yedu.data.CloudPresignResponse
import com.taverndream.yedu.data.CloudUploadDescriptor
import com.taverndream.yedu.data.CloudUploadFile
import com.taverndream.yedu.data.CloudWorkMetadata
import com.taverndream.yedu.data.CloudWorksPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class CloudApiException(
    val statusCode: Int,
    message: String,
) : Exception(message)

class CloudApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        require(value.isNotBlank()) { "服务器地址不能为空" }
        if (!value.contains("://")) {
            // 局域网地址默认 http；公网域名默认 https，避免把 Cloudflare/Tunnel 域名误当 http。
            val scheme = if (looksLikeLanHost(value)) "http" else "https"
            value = "$scheme://$value"
        }
        val parsed = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("服务器地址格式不正确")
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "服务器地址只支持 http 或 https"
        }
        return parsed.toString().trimEnd('/')
    }

    private fun looksLikeLanHost(value: String): Boolean {
        val authority = value.substringBefore('/').trim()
        val host = (if (authority.startsWith("[")) {
            authority.substringAfter("[").substringBefore("]")
        } else {
            authority.substringBefore(':')
        }).lowercase()
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            parts[0] == 127 ||
            (parts[0] == 169 && parts[1] == 254) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }

    suspend fun health(baseUrl: String) {
        val url = urlFor(baseUrl, "health")
        execute(Request.Builder().url(url).get().build())
    }

    suspend fun register(baseUrl: String): CloudDeviceSession {
        val body = "{}".toRequestBody(JSON_MEDIA)
        val url = urlFor(baseUrl, "api/v1/device/register")
        val raw = execute(Request.Builder().url(url).post(body).build())
        return decode(raw, "设备注册失败")
    }

    suspend fun refresh(baseUrl: String, token: String): CloudDeviceSession {
        val url = urlFor(baseUrl, "api/v1/device/refresh")
        val raw = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
        )
        return decode(raw, "设备令牌刷新失败")
    }

    suspend fun createWork(
        baseUrl: String,
        token: String,
        metadata: CloudWorkMetadata,
    ): CloudDraft {
        val url = urlFor(baseUrl, "api/v1/works")
        val raw = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(json.encodeToString(metadata).toRequestBody(JSON_MEDIA))
                .build()
        )
        return decode(raw, "创建云端作品失败")
    }

    suspend fun presign(
        baseUrl: String,
        token: String,
        workId: String,
        files: List<CloudUploadDescriptor>,
    ): CloudPresignResponse {
        val url = urlFor(baseUrl, "api/v1/works/$workId/presign")
        val body = json.encodeToString(CloudPresignRequest(files)).toRequestBody(JSON_MEDIA)
        val raw = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        )
        return decode(raw, "获取上传地址失败")
    }

    suspend fun finalize(
        baseUrl: String,
        token: String,
        workId: String,
        metadata: CloudWorkMetadata,
    ): com.taverndream.yedu.data.CloudWork {
        val url = urlFor(baseUrl, "api/v1/works/$workId/finalize")
        val raw = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(json.encodeToString(metadata).toRequestBody(JSON_MEDIA))
                .build()
        )
        return decode(raw, "发布云端作品失败")
    }

    suspend fun listWorks(
        baseUrl: String,
        page: Int = 1,
        pageSize: Int = 30,
        query: String = "",
    ): CloudWorksPage {
        val url = urlFor(baseUrl, "api/v1/works") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
            if (query.isNotBlank()) addQueryParameter("q", query)
        }
        val raw = execute(Request.Builder().url(url).get().build())
        return decode(raw, "读取云端作品列表失败")
    }

    suspend fun downloadInfo(baseUrl: String, workId: String): CloudDownloadResponse {
        val url = urlFor(baseUrl, "api/v1/works/$workId/download")
        val raw = execute(Request.Builder().url(url).get().build())
        return decode(raw, "获取下载地址失败")
    }

    suspend fun uploadFile(
        file: CloudUploadFile,
        bytes: ByteArray? = null,
        localFile: File? = null,
    ) {
        val mediaType = file.contentType.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaTypeOrNull()
        val body: RequestBody = when {
            localFile != null -> localFile.asRequestBody(mediaType)
            bytes != null -> bytes.toRequestBody(mediaType)
            else -> throw CloudApiException(500, "没有可上传的文件内容")
        }

        if (file.mode == "form") {
            val host = file.uploadHost?.takeIf { it.isNotBlank() }
                ?: throw CloudApiException(500, "服务端没有返回七牛上传地址")
            val token = file.uploadToken?.takeIf { it.isNotBlank() }
                ?: throw CloudApiException(500, "服务端没有返回七牛上传 Token")
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("token", token)
                .addFormDataPart("key", file.key)
                .addFormDataPart(
                    "file",
                    file.name.ifBlank { "file" },
                    body,
                )
                .build()
            execute(Request.Builder().url(host).post(multipart).build())
            return
        }

        val url = file.uploadUrl?.takeIf { it.isNotBlank() }
            ?: throw CloudApiException(500, "服务端没有返回上传地址")
        val builder = Request.Builder().url(url).put(body)
        file.headers.forEach { (name, value) ->
            // Content-Length 由 OkHttp 根据 body 自动计算，避免签名头不一致。
            if (!name.equals("Content-Length", ignoreCase = true)) {
                builder.header(name, value)
            }
        }
        execute(builder.build())
    }

    suspend fun downloadBytes(url: String, maxBytes: Long = 64L * 1024 * 1024): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw CloudApiException(response.code, "文件下载失败（${response.code}）")
                }
                val length = response.body?.contentLength() ?: -1L
                if (length > maxBytes) throw CloudApiException(413, "文件超过下载大小限制")
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.size > maxBytes) throw CloudApiException(413, "文件超过下载大小限制")
                bytes
            }
        }

    suspend fun downloadToFile(
        url: String,
        target: File,
        maxBytes: Long = 512L * 1024 * 1024,
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw CloudApiException(response.code, "文件下载失败（${response.code}）")
                }
                val length = response.body?.contentLength() ?: -1L
                if (length > maxBytes) throw CloudApiException(413, "文件超过下载大小限制")
                var total = 0L
                response.body?.byteStream()?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) throw CloudApiException(413, "文件超过下载大小限制")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            target
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    private fun urlFor(
        baseUrl: String,
        path: String,
        configure: HttpUrl.Builder.() -> Unit = {},
    ): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("服务器地址格式不正确")
        return base.newBuilder()
            .addPathSegments(path.trim('/'))
            .apply(configure)
            .build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    val root = json.parseToJsonElement(body)
                    root.jsonObject["error"]?.toString()?.trim('"')
                }.getOrNull()
                throw CloudApiException(
                    response.code,
                    message?.takeIf { it.isNotBlank() } ?: "服务器返回 ${response.code}",
                )
            }
            body
        }
    }

    private inline fun <reified T> decode(raw: String, fallback: String): T =
        runCatching { json.decodeFromString<T>(raw) }
            .getOrElse { throw CloudApiException(500, "$fallback：${it.message}") }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
