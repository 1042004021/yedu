package com.taverndream.yedu.data

import kotlinx.serialization.Serializable

/** 云后端注册设备后保存的会话。 */
@Serializable
data class CloudDeviceSession(
    val deviceId: String = "",
    val token: String = "",
    val expiresAt: Long = 0L,
    val keyPrefix: String = "",
)

/** 上传作品时提交的元数据。 */
@Serializable
data class CloudWorkMetadata(
    val title: String,
    val description: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val nodeCount: Int = 0,
    val imageCount: Int = 0,
)

@Serializable
data class CloudDraft(
    val id: String,
    val title: String = "",
    val status: String = "draft",
    val keyPrefix: String = "",
    val createdAt: String = "",
)

@Serializable
data class CloudUploadDescriptor(
    val kind: String,
    val name: String,
    val size: Long,
    val contentType: String,
)

@Serializable
data class CloudUploadFile(
    val kind: String = "",
    val key: String = "",
    val name: String = "",
    val size: Long = 0L,
    val contentType: String = "",
    val mode: String = "put",
    val uploadUrl: String? = null,
    val uploadHost: String? = null,
    val uploadToken: String? = null,
    val method: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: Long = 0L,
)

@Serializable
data class CloudPresignRequest(val files: List<CloudUploadDescriptor>)

@Serializable
data class CloudPresignResponse(
    val workId: String,
    val files: List<CloudUploadFile> = emptyList(),
)

@Serializable
data class CloudWork(
    val id: String,
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null,
    val coverExpiresAt: Long? = null,
    val nodeCount: Int = 0,
    val imageCount: Int = 0,
    val sizeBytes: Long = 0L,
    val downloadCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val publishedAt: String? = null,
)

@Serializable
data class CloudWorksPage(
    val items: List<CloudWork> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class CloudDownloadFile(
    val key: String = "",
    val name: String = "",
    val url: String,
    val expiresAt: Long = 0L,
)

@Serializable
data class CloudDownloadResponse(
    val id: String,
    val title: String = "",
    val script: CloudDownloadFile,
    val pack: CloudDownloadFile? = null,
    val cover: CloudDownloadFile? = null,
)
