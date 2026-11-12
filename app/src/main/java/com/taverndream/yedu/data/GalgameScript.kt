package com.taverndream.yedu.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * 单文件 Galgame 剧本。
 *
 * 节点只引用图片 ID；真正的生图提示词统一放在文件末尾的 [images] 中。
 * APK 负责解析、渲染，并在后台按 [images] 逐张生成缺图。
 */
@Serializable
data class GalgameScript(
    val format: String = "taverndream.galgame/1",
    val id: String = "",
    val title: String = "",
    val start: String = "",
    val nodes: List<GalgameScriptNode> = emptyList(),
    /** 放在 JSON 最后：图片 ID、提示词和生成参数。 */
    val images: List<GalgameImageSpec> = emptyList(),
)

@Serializable
data class GalgameScriptNode(
    val id: String = "",
    val title: String = "",
    val location: String = "",
    val time: String = "",
    val narration: String = "",
    val lines: List<GalgameLine> = emptyList(),
    /** 本节点默认显示的图片 ID；单句可用 [GalgameLine.image] 覆盖。 */
    val image: String = "",
    val choices: List<GalgameChoice> = emptyList(),
    /** 没有选项时点击“继续”跳转的节点。 */
    val next: String = "",
    val ending: Boolean = false,
    /** 进入节点时应用一次的效果。 */
    val effects: GalgameEffects = GalgameEffects(),
)

@Serializable
data class GalgameImageSpec(
    val id: String = "",
    val prompt: String = "",
    val negative: String = "",
    /** scene / event_cg；仅用于界面标记，不影响生图。 */
    val kind: String = "scene",
    /** 1:1、4:3、3:4 等。 */
    val ratio: String = "3:4",
    val seed: Long? = null,
)

@Serializable
data class GalgameHistoryEntry(
    val nodeId: String = "",
    val choiceText: String = "",
)

@Serializable
data class GalgameSave(
    val scriptId: String = "",
    val nodeId: String = "",
    val stateJson: String = "{}",
    val history: List<GalgameHistoryEntry> = emptyList(),
    val lineIndex: Int = 0,
)

/** 解析、规范化与校验 JSON 剧本。 */
object GalgameScriptCodec {
    private val json = GalgameJson.json

    fun parse(raw: String): GalgameScript? =
        runCatching { json.decodeFromString<GalgameScript>(raw) }.getOrNull()

    fun encode(script: GalgameScript): String = json.encodeToString(script)

    fun encodeSave(save: GalgameSave): String = json.encodeToString(save)

    fun parseSave(raw: String): GalgameSave? =
        runCatching { json.decodeFromString<GalgameSave>(raw) }.getOrNull()

    /**
     * 规范化并返回错误列表。存在错误时调用方不应加载剧本。
     */
    fun validate(raw: String): Result {
        val script = parse(raw) ?: return Result(null, listOf("JSON 解析失败，请检查格式"))
        val errors = mutableListOf<String>()
        val normalizedNodes = script.nodes.mapIndexed { index, node ->
            node.copy(
                id = node.id.trim().ifBlank { "node_${index + 1}" },
                title = node.title.trim(),
                lines = node.lines
                    .map { it.copy(speaker = it.speaker.trim(), text = it.text.trim(), image = it.image.trim()) }
                    .filter { it.text.isNotBlank() },
                choices = node.choices
                    .mapIndexed { choiceIndex, choice ->
                        choice.copy(
                            id = choice.id.trim().ifBlank { "choice_${choiceIndex + 1}" },
                            text = choice.text.trim(),
                            next = choice.next.trim(),
                        )
                    }
                    .filter { it.text.isNotBlank() },
                image = node.image.trim(),
                next = node.next.trim(),
            )
        }
        val normalized = script.copy(
            id = script.id.trim().ifBlank { "local" },
            title = script.title.trim().ifBlank { "未命名 Galgame" },
            start = script.start.trim(),
            nodes = normalizedNodes,
            images = script.images.mapIndexed { index, image ->
                image.copy(
                    id = image.id.trim().ifBlank { "image_${index + 1}" },
                    prompt = image.prompt.trim(),
                    negative = image.negative.trim(),
                    kind = image.kind.trim().ifBlank { "scene" },
                    ratio = image.ratio.trim().ifBlank { "3:4" },
                )
            },
        )

        if (normalized.nodes.isEmpty()) errors += "nodes 不能为空"
        if (normalized.start.isBlank()) errors += "start 不能为空"
        val nodeIds = normalized.nodes.map { it.id }
        if (nodeIds.size != nodeIds.toSet().size) errors += "节点 id 重复"
        val imageIds = normalized.images.map { it.id }
        if (imageIds.size != imageIds.toSet().size) errors += "图片 id 重复"
        if (normalized.start.isNotBlank() && normalized.start !in nodeIds) {
            errors += "start 指向不存在的节点：${normalized.start}"
        }
        val imageIdSet = imageIds.toSet()
        val startNode = normalized.nodes.firstOrNull { it.id == normalized.start }
        if (startNode != null &&
            startNode.image.isBlank() &&
            startNode.lines.none { it.image.isNotBlank() }
        ) {
            errors += "start 节点 ${startNode.id} 至少需要一张 image（节点或单句）"
        }
        normalized.images.forEach { image ->
            if (image.prompt.isBlank()) errors += "图片 ${image.id} 缺少 prompt"
        }
        normalized.nodes.forEach { node ->
            if (node.image.isNotBlank() && node.image !in imageIdSet) {
                errors += "节点 ${node.id} 引用了不存在的图片：${node.image}"
            }
            node.lines.forEachIndexed { index, line ->
                if (line.image.isNotBlank() && line.image !in imageIdSet) {
                    errors += "节点 ${node.id} 第 ${index + 1} 句引用了不存在的图片：${line.image}"
                }
            }
            val choiceIds = node.choices.map { it.id }
            if (choiceIds.size != choiceIds.toSet().size) errors += "节点 ${node.id} 的 choice id 重复"
            node.choices.forEach { choice ->
                if (choice.next.isBlank()) {
                    errors += "节点 ${node.id} 的选项 ${choice.id} 缺少 next"
                } else if (choice.next !in nodeIds) {
                    errors += "节点 ${node.id} 的选项 ${choice.id} 指向不存在的节点：${choice.next}"
                }
            }
            if (node.next.isNotBlank() && node.next !in nodeIds) {
                errors += "节点 ${node.id} 的 next 指向不存在的节点：${node.next}"
            }
            if (!node.ending && node.next.isBlank() && node.choices.isEmpty()) {
                errors += "节点 ${node.id} 既没有 next、没有选项，也没有标记 ending"
            }
        }
        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        if (normalized.start in nodeIds) queue.add(normalized.start)
        val byId = normalized.nodes.associateBy { it.id }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!reachable.add(id)) continue
            val node = byId[id] ?: continue
            if (node.next.isNotBlank()) queue.add(node.next)
            node.choices.forEach { choice -> if (choice.next.isNotBlank()) queue.add(choice.next) }
        }
        normalized.nodes.filter { it.id !in reachable }.forEach {
            errors += "节点 ${it.id} 从 start 不可达"
        }
        return Result(normalized, errors.distinct())
    }

    data class Result(
        val script: GalgameScript?,
        val errors: List<String>,
    ) {
        val ok: Boolean get() = script != null && errors.isEmpty()
    }
}