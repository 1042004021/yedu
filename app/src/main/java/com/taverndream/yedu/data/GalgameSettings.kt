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

    companion object {
        const val DEFAULT_NEGATIVE =
            "lowres, bad anatomy, bad hands, worst quality, watermark, text,"
        const val DEFAULT_STYLE = "anime style, clean lineart, soft lighting"
    }
}