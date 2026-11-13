package com.taverndream.yedu.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GalgameImagePlannerTest {

    private fun node(
        id: String,
        image: String = "",
        next: String = "",
        choices: List<GalgameChoice> = emptyList(),
        ending: Boolean = false,
    ) = GalgameScriptNode(
        id = id,
        title = id,
        image = image,
        next = next,
        choices = choices,
        ending = ending,
    )

    private fun choice(id: String, next: String) = GalgameChoice(id = id, text = id, next = next)

    private fun script(vararg nodes: GalgameScriptNode) = GalgameScript(
        id = "s",
        title = "test",
        start = nodes.first().id,
        nodes = nodes.toList(),
        images = nodes.flatMap { n ->
            buildList {
                if (n.image.isNotBlank()) add(GalgameImageSpec(id = n.image, prompt = n.image))
                n.lines.forEach { line ->
                    if (line.image.isNotBlank()) add(GalgameImageSpec(id = line.image, prompt = line.image))
                }
            }
        }.distinctBy { it.id },
    )

    @Test
    fun linearStoryGeneratesInNodeOrder() {
        val plan = GalgameImagePlanner.plan(
            script(
                node("n1", image = "a", next = "n2"),
                node("n2", image = "b", next = "n3"),
                node("n3", image = "c", ending = true),
            )
        )

        assertEquals(listOf("a", "b", "c"), plan)
    }

    @Test
    fun branchesAreInterleavedOneImageAtATime() {
        val plan = GalgameImagePlanner.plan(
            script(
                node(
                    "n1", image = "a",
                    choices = listOf(choice("left", "n2"), choice("right", "n3")),
                ),
                node("n2", image = "b", next = "n4"),
                node("n3", image = "c", next = "n5"),
                node("n4", image = "d", ending = true),
                node("n5", image = "e", ending = true),
            )
        )

        assertEquals(listOf("a", "b", "c", "d", "e"), plan)
    }

    @Test
    fun chosenBranchIsPrioritizedThenOtherBranchesFollow() {
        val plan = GalgameImagePlanner.plan(
            script(
                node(
                    "n1", image = "a",
                    choices = listOf(choice("left", "n2"), choice("right", "n3")),
                ),
                node("n2", image = "b", next = "n4"),
                node("n3", image = "c", next = "n5"),
                node("n4", image = "d", ending = true),
                node("n5", image = "e", ending = true),
            ),
            history = listOf(
                GalgameHistoryEntry(nodeId = "n1"),
                GalgameHistoryEntry(nodeId = "n2", choiceText = "left"),
            ),
        )

        assertEquals(listOf("a", "b", "d", "c", "e"), plan)
    }

    @Test
    fun nestedBranchInsideChosenPathIsStillInterleaved() {
        val plan = GalgameImagePlanner.plan(
            script(
                node(
                    "n1", image = "a",
                    choices = listOf(choice("left", "n2"), choice("right", "n3")),
                ),
                node(
                    "n2", image = "b",
                    choices = listOf(choice("up", "n4"), choice("down", "n5")),
                ),
                node("n3", image = "c", ending = true),
                node("n4", image = "d", ending = true),
                node("n5", image = "e", ending = true),
            ),
            history = listOf(
                GalgameHistoryEntry(nodeId = "n1"),
                GalgameHistoryEntry(nodeId = "n2", choiceText = "left"),
            ),
        )

        assertEquals(listOf("a", "b", "d", "e", "c"), plan)
    }

    @Test
    fun currentImageIsFirstWithoutDuplicating() {
        val plan = GalgameImagePlanner.plan(
            script(
                node("n1", image = "a", next = "n2"),
                node("n2", image = "b", ending = true),
            ),
            currentImageId = "b",
        )

        assertEquals(listOf("b", "a"), plan)
    }
}
