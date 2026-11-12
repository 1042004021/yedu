package com.taverndream.yedu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalgameScriptCodecTest {

    private val valid = """
        {
          "format": "taverndream.galgame/1",
          "id": "test-pack",
          "title": "测试剧本",
          "start": "n1",
          "nodes": [
            {
              "id": "n1",
              "title": "第一幕",
              "location": "402门口",
              "time": "夜晚",
              "narration": "走廊很安静。",
              "image": "scene_1",
              "lines": [
                {"speaker": "苏糖", "text": "你来了。", "image": "close_1"}
              ],
              "choices": [
                {"id": "a", "text": "敲门", "next": "n2", "effects": {"affection": {"苏糖": 2}}},
                {"id": "b", "text": "先听", "next": "n2"}
              ]
            },
            {
              "id": "n2",
              "title": "结局",
              "ending": true,
              "image": "scene_2",
              "lines": [{"speaker": "苏糖", "text": "门开了。"}]
            }
          ],
          "images": [
            {"id": "scene_1", "prompt": "旧公寓走廊，暖灯，雨夜"},
            {"id": "close_1", "prompt": "苏糖开门，情绪特写", "kind": "event_cg"},
            {"id": "scene_2", "prompt": "门口两人对视，中景"}
          ]
        }
    """.trimIndent()

    @Test
    fun validatesBranchingScriptAndKeepsImageReferences() {
        val result = GalgameScriptCodec.validate(valid)

        assertTrue(result.errors.joinToString("\n"), result.ok)
        assertNotNull(result.script)
        val script = result.script!!
        assertEquals("n1", script.start)
        assertEquals("close_1", script.nodes.first().lines.first().image)
        assertEquals("scene_1", script.nodes.first().image)
        assertEquals(3, script.images.size)
        assertEquals("event_cg", script.images[1].kind)
    }

    @Test
    fun allowsIntermediateNodesToInheritPreviousImage() {
        val raw = """
            {
              "id": "inherit",
              "title": "继承图",
              "start": "n1",
              "nodes": [
                {"id": "n1", "image": "scene_1", "next": "n2"},
                {"id": "n2", "next": "n3"},
                {"id": "n3", "ending": true}
              ],
              "images": [{"id": "scene_1", "prompt": "房间，暖灯"}]
            }
        """.trimIndent()

        val result = GalgameScriptCodec.validate(raw)

        assertTrue(result.errors.joinToString("\n"), result.ok)
        assertEquals("", result.script!!.nodes[1].image)
        assertEquals("", result.script!!.nodes[2].image)
    }

    @Test
    fun rejectsMissingImageAndMissingNext() {
        val bad = """
            {
              "id": "bad",
              "title": "坏剧本",
              "start": "n1",
              "nodes": [
                {
                  "id": "n1",
                  "image": "missing_image",
                  "choices": [{"id": "a", "text": "走", "next": "missing_node"}]
                }
              ],
              "images": []
            }
        """.trimIndent()

        val result = GalgameScriptCodec.validate(bad)

        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("missing_image") })
        assertTrue(result.errors.any { it.contains("missing_node") })
    }

    @Test
    fun saveRoundTripKeepsCurrentNodeAndHistory() {
        val save = GalgameSave(
            scriptId = "test-pack",
            nodeId = "n2",
            stateJson = GalgameJson.encodeState(GalgameState(storyDay = 2)),
            history = listOf(
                GalgameHistoryEntry("n1", ""),
                GalgameHistoryEntry("n2", "敲门"),
            ),
            lineIndex = 1,
        )

        val decoded = GalgameScriptCodec.parseSave(GalgameScriptCodec.encodeSave(save))

        assertEquals(save, decoded)
    }
}