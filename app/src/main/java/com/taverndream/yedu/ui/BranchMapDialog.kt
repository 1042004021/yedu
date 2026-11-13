package com.taverndream.yedu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.taverndream.yedu.GalgameGraphNode

@Composable
fun BranchMapDialog(
    nodes: List<GalgameGraphNode>,
    currentNodeId: String?,
    onJump: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GalgameBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            "剧情拓谱",
                            color = Paper,
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "按剧情层级排列 · 点击节点跳转继续",
                            color = Muted,
                            fontSize = 10.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Paper)
                    }
                }

                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetaChip("当前", color = Amber)
                    MetaChip("已走过", color = AmberSoft)
                    MetaChip("未走过", color = Muted)
                }

                val groups = nodes
                    .groupBy { it.depth }
                    .toSortedMap()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    groups.forEach { (depth, layer) ->
                        item(key = "layer-$depth") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (depth == Int.MAX_VALUE) "不可达节点" else "第 ${depth + 1} 层",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    layer.forEach { node ->
                                        GraphNodeCard(
                                            node = node,
                                            isCurrent = node.id == currentNodeId,
                                            onClick = { onJump(node.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphNodeCard(
    node: GalgameGraphNode,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        isCurrent -> BorderStroke(1.dp, Amber)
        node.visited -> BorderStroke(1.dp, AmberSoft.copy(alpha = 0.55f))
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    }
    val background = if (isCurrent) Amber.copy(alpha = 0.13f) else Panel
    Surface(
        modifier = Modifier
            .width(164.dp)
            .height(150.dp),
        color = background,
        shape = MaterialTheme.shapes.medium,
        border = border,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    node.title,
                    modifier = Modifier.weight(1f),
                    color = Paper,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (node.ending) {
                    Text("END", color = Rose, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                buildString {
                    append(node.id)
                    if (node.imageCount > 0) append(" · ${node.imageCount} 图")
                },
                color = Muted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            node.edges.take(3).forEach { edge ->
                Text(
                    "→ ${edge.label}",
                    color = if (node.visited || isCurrent) AmberSoft else Muted.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (node.edges.size > 3) {
                Text("→ 还有 ${node.edges.size - 3} 个分支", color = Muted, fontSize = 8.sp)
            }
        }
    }
}
