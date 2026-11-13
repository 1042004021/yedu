package com.taverndream.yedu.data

/**
 * 按剧情图决定生图顺序。
 *
 * - 直线：沿 next 逐节点生成。
 * - 分支：每个分支轮流生成一张，先保证每个分支都有一张。
 * - 已经选择的分支：该分支按顺序优先生成；分支内部再遇到分支时继续轮流。
 * - 所有图片最终都会进入队列；未在剧情中引用的图片排在最后。
 */
object GalgameImagePlanner {

    fun plan(
        script: GalgameScript,
        history: List<GalgameHistoryEntry> = emptyList(),
        currentImageId: String? = null,
    ): List<String> {
        val byId = script.nodes.associateBy { it.id }
        if (byId.isEmpty()) return emptyList()
        val validImageIds = script.images.map { it.id }.filter { it.isNotBlank() }.toSet()
        val chosenAt = chosenBranches(history, byId)

        fun imagesOf(node: GalgameScriptNode): List<String> = buildList {
            if (node.image.isNotBlank()) add(node.image)
            node.lines.forEach { line ->
                if (line.image.isNotBlank()) add(line.image)
            }
        }.filter { it in validImageIds }.distinct()

        fun sequenceFrom(nodeId: String, visiting: Set<String>): List<String> {
            if (nodeId.isBlank() || nodeId in visiting) return emptyList()
            val node = byId[nodeId] ?: return emptyList()
            val nextVisiting = visiting + node.id
            val own = imagesOf(node)
            if (node.ending) return own

            if (node.choices.isNotEmpty()) {
                val branchIds = node.choices.map { it.next }.filter { it.isNotBlank() }
                val branches = branchIds.map { sequenceFrom(it, nextVisiting) }
                val chosenTarget = chosenAt[node.id]
                if (chosenTarget != null) {
                    val chosenIndex = node.choices.indexOfFirst { it.next == chosenTarget }
                    if (chosenIndex >= 0) {
                        val chosen = branches.getOrElse(chosenIndex) { emptyList() }
                        val others = branches.filterIndexed { index, _ -> index != chosenIndex }
                        return own + chosen + interleave(others)
                    }
                }
                return own + interleave(branches)
            }

            if (node.next.isNotBlank()) {
                return own + sequenceFrom(node.next, nextVisiting)
            }
            return own
        }

        val ordered = sequenceFrom(script.start, emptySet())
            .filter { it in validImageIds }
            .distinct()
            .toMutableList()

        // 没有在剧情里引用的图片也放进画廊和后台队列，排在最后。
        script.images.forEach { spec ->
            if (spec.id.isNotBlank() && spec.id !in ordered) ordered += spec.id
        }

        val current = currentImageId?.takeIf { it in ordered }
        return if (current != null) {
            listOf(current) + ordered.filter { it != current }
        } else {
            ordered
        }
    }

    /** 从历史节点里还原每个分支节点实际选择了哪条 next。 */
    private fun chosenBranches(
        history: List<GalgameHistoryEntry>,
        byId: Map<String, GalgameScriptNode>,
    ): Map<String, String> {
        if (history.size < 2) return emptyMap()
        val chosen = mutableMapOf<String, String>()
        history.zipWithNext().forEach { (from, to) ->
            val node = byId[from.nodeId] ?: return@forEach
            if (node.choices.any { it.next == to.nodeId }) {
                chosen[node.id] = to.nodeId
            }
        }
        return chosen
    }

    /** 多个分支轮流取下一张：A1, B1, C1, A2, B2... */
    private fun interleave(branches: List<List<String>>): List<String> {
        if (branches.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var index = 0
        while (true) {
            var any = false
            branches.forEach { branch ->
                branch.getOrNull(index)?.let {
                    result += it
                    any = true
                }
            }
            if (!any) break
            index++
        }
        return result
    }
}
