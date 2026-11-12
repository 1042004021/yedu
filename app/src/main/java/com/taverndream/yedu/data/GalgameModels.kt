package com.taverndream.yedu.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GalgameLine(
    val speaker: String = "",
    val text: String = "",
    val expression: String = "",
    val pose: String = "",
    /** 这一句台词显示时切换到的图片 ID；空表示沿用节点图片。 */
    val image: String = "",
)

@Serializable
data class GalgameEffects(
    val affection: Map<String, Int> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
    val items: List<String> = emptyList(),
)

@Serializable
data class GalgameChoice(
    val id: String = "",
    val text: String = "",
    val hint: String = "",
    val effects: GalgameEffects = GalgameEffects(),
    /** 选择后跳转的节点 ID；JSON 剧本必填。 */
    val next: String = "",
)

@Serializable
data class GalgameNodePayload(
    val title: String = "",
    val location: String = "",
    val time: String = "",
    val narration: String = "",
    val lines: List<GalgameLine> = emptyList(),
    val choices: List<GalgameChoice> = emptyList(),
    val backgroundPrompt: String = "",
    /** scene=普通场景 CG；cg=关键事件 CG。 */
    val visualMode: String = "scene",
    /** 事件 CG 的动作/构图提示词，仅 cg 模式使用。 */
    val cgPrompt: String = "",
    val summary: String = "",
)

@Serializable
data class GalgameState(
    val affection: Map<String, Int> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
    val items: List<String> = emptyList(),
    val storyDay: Int = 1,
    val storyTime: String = "",
    val location: String = "",
)

fun GalgameState.withEffects(effects: GalgameEffects?): GalgameState {
    if (effects == null) return this
    val nextAffection = affection.toMutableMap()
    effects.affection.forEach { (name, delta) ->
        if (name.isNotBlank()) {
            nextAffection[name] = ((nextAffection[name] ?: 50) + delta).coerceIn(0, 100)
        }
    }
    val nextFlags = flags.toMutableMap()
    effects.flags.forEach { (key, value) ->
        if (key.isNotBlank()) nextFlags[key] = value
    }
    return copy(
        affection = nextAffection,
        flags = nextFlags,
        items = (items + effects.items).distinct(),
    )
}

object GalgameJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun parseState(raw: String?): GalgameState =
        runCatching { json.decodeFromString<GalgameState>(raw.orEmpty()) }
            .getOrDefault(GalgameState())

    fun encodeState(state: GalgameState): String = json.encodeToString(state)

    fun parseLines(raw: String?): List<GalgameLine> =
        runCatching { json.decodeFromString<List<GalgameLine>>(raw.orEmpty()) }
            .getOrDefault(emptyList())

    fun encodeLines(lines: List<GalgameLine>): String = json.encodeToString(lines)

    fun parseChoices(raw: String?): List<GalgameChoice> =
        runCatching { json.decodeFromString<List<GalgameChoice>>(raw.orEmpty()) }
            .getOrDefault(emptyList())

    fun encodeChoices(choices: List<GalgameChoice>): String = json.encodeToString(choices)
}