package com.taverndream.yedu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.taverndream.yedu.GalgameGalleryItem

@Composable
fun GalleryDialog(
    items: List<GalgameGalleryItem>,
    onRegenerate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var preview by remember { mutableStateOf<GalgameGalleryItem?>(null) }
    var editing by remember { mutableStateOf<GalgameGalleryItem?>(null) }
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
                            "画廊",
                            color = Paper,
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "已生成 ${items.count { it.path != null }} / ${items.size} 张",
                            color = Muted,
                            fontSize = 10.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Paper)
                    }
                }

                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个剧本还没有图片清单", color = Muted, fontSize = 13.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            GalleryCard(
                                item = item,
                                onClick = { if (item.path != null) preview = item },
                                onEdit = { editing = item },
                            )
                        }
                    }
                }
            }
        }
    }

    preview?.let { item ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { preview = null },
                contentAlignment = Alignment.Center,
            ) {
                BlurredFitImage(
                    path = item.path,
                    contentDescription = item.id,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    blurRadius = 34.dp,
                    dim = 0.42f,
                )
                IconButton(
                    onClick = { preview = null },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(10.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭预览", tint = Color.White)
                }
                TextButton(
                    onClick = {
                        preview = null
                        editing = item
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
                ) {
                    Text("编辑提示词并重绘", color = Amber, fontSize = 14.sp)
                }
            }
        }
    }

    editing?.let { item ->
        PromptEditDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { prompt ->
                editing = null
                onRegenerate(item.id, prompt)
            },
        )
    }
}

@Composable
private fun GalleryCard(
    item: GalgameGalleryItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val generated = item.path != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = generated, onClick = onClick),
        color = if (generated) Panel else Color(0xFF1A1D24),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (generated) Amber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White.copy(alpha = if (generated) 0f else 0.025f)),
                contentAlignment = Alignment.Center,
            ) {
                if (generated) {
                    BlurredFitImage(
                        path = item.path,
                        contentDescription = item.id,
                        modifier = Modifier.fillMaxSize(),
                        blurRadius = 18.dp,
                        dim = 0.18f,
                    )
                } else {
                    Column(
                        modifier = Modifier.alpha(0.42f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (item.kind == "event_cg") "EVENT CG" else "SCENE",
                            color = Muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Text("未生成", color = Muted, fontSize = 12.sp)
                        Text(item.id, color = Muted, fontSize = 9.sp, maxLines = 1)
                    }
                }
                if (generated && item.kind == "event_cg") {
                    Text(
                        "EVENT CG",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                            .background(Color.Black.copy(alpha = 0.52f), MaterialTheme.shapes.small)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Amber,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(start = 9.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        item.id,
                        color = if (generated) Paper else Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.prompt.isNotBlank()) {
                        Text(
                            item.prompt,
                            color = Muted.copy(alpha = if (generated) 0.7f else 0.55f),
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑提示词",
                        tint = AmberSoft,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}


@Composable
private fun PromptEditDialog(
    item: GalgameGalleryItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var prompt by remember(item.id, item.prompt) { mutableStateOf(item.prompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑提示词", color = Paper) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.id, color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 8,
                    label = { Text("Prompt") },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "保存后会删除当前图片并立即重绘这一张。",
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(prompt.trim()) },
                enabled = prompt.isNotBlank(),
            ) { Text("保存并重绘", color = if (prompt.isNotBlank()) Amber else Muted) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
        },
    )
}
