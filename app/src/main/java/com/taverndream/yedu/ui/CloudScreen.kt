package com.taverndream.yedu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taverndream.yedu.GalgameViewModel
import com.taverndream.yedu.data.CloudWork
import java.util.Locale

@Composable
fun CloudScreen(vm: GalgameViewModel, onBack: () -> Unit) {
    var serverUrl by remember { mutableStateOf(vm.settings.cloudBaseUrl) }
    var query by remember { mutableStateOf("") }
    var includePack by remember { mutableStateOf(true) }
    var author by remember { mutableStateOf(vm.settings.cloudAuthor) }

    LaunchedEffect(Unit) {
        if (vm.settings.cloudBaseUrl.isNotBlank() && vm.cloudWorks.isEmpty()) {
            vm.refreshCloudWorks()
        }
    }

    GalgameBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Paper)
                }
                Text(
                    "云端作品库",
                    color = Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("云服务器", vm.cloudServerStatus)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.10:8787") },
                    supportingText = { Text("支持 IP:端口，或 Cloudflare Tunnel 的 https 域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryAction(
                        text = if (vm.cloudTesting) "连接中…" else "保存并测试",
                        onClick = { vm.testCloudServer(serverUrl) },
                        enabled = !vm.cloudTesting,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryAction(
                        text = "刷新云端",
                        onClick = { vm.refreshCloudWorks(query) },
                        enabled = !vm.cloudLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("上传当前剧本", if (vm.hasScript) vm.scriptTitle else "未选择")
                if (vm.hasScript) {
                    Text(
                        "${vm.nodeCount} 幕 · ${vm.imageCount} 图",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者名（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Checkbox(
                            checked = includePack,
                            onCheckedChange = { includePack = it },
                        )
                        Column(Modifier.weight(1f)) {
                            Text("包含图片包", color = Paper, fontSize = 13.sp)
                            Text(
                                "把本地 images/ 打成 pack.zip 一起上传；没有图片时只传剧本",
                                color = Muted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    PrimaryAction(
                        text = when {
                            vm.cloudBusy && vm.cloudDownloadingId == null ->
                                vm.cloudProgress ?: "上传中…"
                            else -> "上传到云作品库"
                        },
                        onClick = {
                            vm.settings.cloudAuthor = author
                            vm.uploadCurrentScript(includePack, author)
                        },
                        enabled = !vm.cloudBusy && vm.settings.cloudBaseUrl.isNotBlank(),
                    )
                } else {
                    Text(
                        "先在本地导入并选择一个剧本，再来上传。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                vm.cloudProgress?.let {
                    if (!vm.cloudBusy) Text(it, color = AmberSoft, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("所有云端作品", "${vm.cloudWorks.size} 个")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索标题 / 作者 / 标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryAction(
                    text = "搜索",
                    onClick = { vm.refreshCloudWorks(query) },
                    enabled = !vm.cloudLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (vm.cloudLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Amber,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在读取云端作品…", color = Muted, fontSize = 12.sp)
                    }
                } else if (vm.cloudWorks.isEmpty()) {
                    Text(
                        "还没有云端作品。上传当前剧本后会出现在这里。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                } else {
                    vm.cloudWorks.forEach { work ->
                        CloudWorkCard(
                            work = work,
                            downloading = vm.cloudDownloadingId == work.id,
                            enabled = !vm.cloudBusy,
                            onDownload = { vm.downloadCloudWork(work) },
                        )
                    }
                }
            }

            vm.cloudError?.let { message ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFF35181C),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, Color(0x66FF9E9E)),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("云端操作失败", color = Color(0xFFFFC1C1), fontWeight = FontWeight.Bold)
                        Text(message, color = Color(0xFFFFD7D7), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CloudWorkCard(
    work: CloudWork,
    downloading: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.035f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(118.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF11151E)),
                contentAlignment = Alignment.Center,
            ) {
                if (work.coverUrl != null) {
                    BlurredFitImageUrl(
                        url = work.coverUrl,
                        contentDescription = work.title,
                        modifier = Modifier.fillMaxSize(),
                        blurRadius = 16.dp,
                        dim = 0.20f,
                    )
                } else {
                    Text("暂无封面", color = Muted, fontSize = 10.sp)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    work.title.ifBlank { "未命名作品" },
                    color = Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    work.author.ifBlank { "匿名作者" },
                    color = Muted,
                    fontSize = 10.sp,
                )
                if (work.tags.isNotEmpty()) {
                    Text(
                        work.tags.take(4).joinToString(" · "),
                        color = AmberSoft,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Text(
                    "${work.nodeCount} 幕 · ${work.imageCount} 图 · ${formatBytes(work.sizeBytes)}",
                    color = Muted,
                    fontSize = 9.sp,
                )
                Text(
                    "下载 ${work.downloadCount} 次",
                    color = Muted,
                    fontSize = 9.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Amber,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("下载中…", color = AmberSoft, fontSize = 11.sp)
                    } else {
                        TextButton(
                            onClick = onDownload,
                            enabled = enabled,
                        ) {
                            Text("下载", color = if (enabled) Amber else Muted)
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) return String.format(Locale.US, "%.1fMB", mb)
    return "${bytes / 1024}KB"
}
