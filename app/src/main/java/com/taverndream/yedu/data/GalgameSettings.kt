package com.taverndream.yedu.data

import android.content.Context
import android.content.SharedPreferences
import com.taverndream.yedu.sd.SdModelManager

/** 独立 Galgame APK 的本地设置，只保留播放器和端侧生图需要的字段。 */
class GalgameSettings(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("yedu_settings", Context.MODE_PRIVATE)

    var sdModelId: String
        get() = sp.getString("sdModelId", SdModelManager.DEFAULT_ID) ?: SdModelManager.DEFAULT_ID
        set(v) = sp.edit().putString("sdModelId", v).apply()

    var sdSteps: Int
        get() = sp.getInt("sdSteps", 20)
        set(v) = sp.edit().putInt("sdSteps", v.coerceIn(4, 40)).apply()

    var sdCfg: Float
        get() = sp.getFloat("sdCfg", 7.5f)
        set(v) = sp.edit().putFloat("sdCfg", v.coerceIn(1f, 20f)).apply()

    var sdNegative: String
        get() = sp.getString("sdNegative", DEFAULT_NEGATIVE) ?: DEFAULT_NEGATIVE
        set(v) = sp.edit().putString("sdNegative", v).apply()

    var styleTags: String
        get() = sp.getString("styleTags", DEFAULT_STYLE) ?: DEFAULT_STYLE
        set(v) = sp.edit().putString("styleTags", v).apply()

    var sdUpscale: Boolean
        get() = sp.getBoolean("sdUpscale", true)
        set(v) = sp.edit().putBoolean("sdUpscale", v).apply()

    var hfMirror: String
        get() = sp.getString("hfMirror", "https://aifasthub.com") ?: "https://aifasthub.com"
        set(v) = sp.edit().putString("hfMirror", v).apply()

    var sdDownloadThreads: Int
        get() = sp.getInt("sdDownloadThreads", 8).coerceIn(1, 16)
        set(v) = sp.edit().putInt("sdDownloadThreads", v.coerceIn(1, 16)).apply()

    var cloudBaseUrl: String
        get() {
            val saved = sp.getString("cloudBaseUrl", "") ?: ""
            // 旧版本曾把演示服务器作为默认值；升级后移除它，避免用户未配置也自动联网。
            if (saved.trimEnd('/') == LEGACY_DEFAULT_CLOUD_BASE_URL) {
                sp.edit()
                    .remove("cloudBaseUrl")
                    .remove("cloudWorksCache")
                    .apply()
                return ""
            }
            return saved
        }
        set(v) = sp.edit().putString("cloudBaseUrl", v).apply()

    /** 云端作品列表缓存，避免每次进入云端页都重新请求封面。 */
    var cloudWorksCache: String
        get() = sp.getString("cloudWorksCache", "") ?: ""
        set(v) = sp.edit().putString("cloudWorksCache", v).apply()

    var cloudDeviceId: String
        get() = sp.getString("cloudDeviceId", "") ?: ""
        set(v) = sp.edit().putString("cloudDeviceId", v).apply()

    var cloudToken: String
        get() = sp.getString("cloudToken", "") ?: ""
        set(v) = sp.edit().putString("cloudToken", v).apply()

    var cloudTokenExpiresAt: Long
        get() = sp.getLong("cloudTokenExpiresAt", 0L)
        set(v) = sp.edit().putLong("cloudTokenExpiresAt", v).apply()

    var cloudAuthor: String
        get() = sp.getString("cloudAuthor", "") ?: ""
        set(v) = sp.edit().putString("cloudAuthor", v).apply()

    fun clearCloudSession() {
        sp.edit()
            .remove("cloudDeviceId")
            .remove("cloudToken")
            .remove("cloudTokenExpiresAt")
            .apply()
    }

    companion object {
        private const val LEGACY_DEFAULT_CLOUD_BASE_URL = "https://yedu.104021.xyz"
        const val DEFAULT_NEGATIVE =
            "lowres, bad anatomy, bad hands, worst quality, watermark, text,"
        const val DEFAULT_STYLE = "anime style, clean lineart, soft lighting"
    }
}