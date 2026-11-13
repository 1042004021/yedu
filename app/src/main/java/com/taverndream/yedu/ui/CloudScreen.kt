package com.taverndream.yedu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taverndream.yedu.GalgameViewModel
import com.taverndream.yedu.data.CloudWork
import com.taverndream.yedu.data.GalgameSettings
import java.util.Locale

@Composable
fun CloudScreen(vm: GalgameViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (vm.settings.cloudBaseUrl.isNotBlank()) {
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
                    modifier = Modifier.weight(1f),
                    color = Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { showServerDialog = true }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "服务器设置",
                        tint = Muted,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "从云端下载作品到本地剧本库",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp),
            )

            Spacer(Modifier.height(14.dp))
            if (vm.settings.cloudBaseUrl.isBlank()) {
                SectionCard {
                    SectionTitle("云服务器", "未配置")
                    Text(
                        "点右上角齿轮配置服务器地址，然后刷新云端作品。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    SecondaryAction(
                        text = "打开服务器设置",
                        onClick = { showServerDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                SectionCard {
                    SectionTitle("所有云端作品", "${vm.cloudWorks.size} 个")
                    CloudSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        loading = vm.cloudLoading,
                        onSearch = { vm.refreshCloudWorks(query) },
                    )

                    if (vm.cloudLoading && vm.cloudWorks.isEmpty()) {
                        CloudLoadingSkeleton()
                    } else if (vm.cloudWorks.isEmpty()) {
                        Text(
                            "还没有云端作品。回到首页长按本地作品即可上传。",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    } else {
                        if (vm.cloudLoading) CloudRefreshStrip()
                        vm.cloudWorks.forEach { work ->
                            val downloading = vm.cloudDownloadingId == work.id
                            CloudWorkCard(
                                work = work,
                                downloading = downloading,
                                progress = if (downloading) vm.cloudProgress else null,
                                enabled = !vm.cloudBusy,
                                onDownload = { vm.downloadCloudWork(work) },
                            )
                        }
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

    if (showServerDialog) {
        CloudServerDialog(
            vm = vm,
            onDismiss = { showServerDialog = false },
        )
    }
}

@Composable
private fun CloudSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    onSearch: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "搜索标题 / 作者 / 标签",
                        color = Muted.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Paper, fontSize = 13.sp),
                    cursorBrush = SolidColor(Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            onSearch()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .pressableClickable(enabled = !loading) {
                        keyboard?.hide()
                        onSearch()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = Amber,
                    )
                } else {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = Amber,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudRefreshStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Amber,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
        Text("正在同步云端作品…", color = AmberSoft, fontSize = 10.sp)
    }
}

@Composable
private fun CloudLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "cloudSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cloudSkeletonPulse",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            CloudSkeletonRow(pulse)
        }
    }
    Text(
        "正在获取云端作品…",
        color = Muted,
        fontSize = 10.sp,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
    )
}

@Composable
private fun CloudSkeletonRow(pulse: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.035f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
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
                    .background(Color.White.copy(alpha = 0.02f + pulse * 0.05f)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBar(0.72f, 15.dp, pulse)
                SkeletonBar(0.34f, 9.dp, pulse)
                Spacer(Modifier.height(5.dp))
                SkeletonBar(0.92f, 8.dp, pulse)
                SkeletonBar(0.55f, 8.dp, pulse)
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: Dp,
    pulse: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.02f + pulse * 0.055f)),
    )
}

@Composable
private fun CloudServerDialog(
    vm: GalgameViewModel,
    onDismiss: () -> Unit,
) {
    var serverUrl by remember(vm.settings.cloudBaseUrl) {
        mutableStateOf(vm.settings.cloudBaseUrl)
    }

    AlertDialog(
        onDismissRequest = { if (!vm.cloudTesting) onDismiss() },
        title = { Text("云服务器设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "支持局域网 IP:端口，或 Cloudflare Tunnel 的 HTTPS 域名。",
                    color = Muted,
                    fontSize = 11.sp,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://yedu.example.com") },
                    supportingText = {
                        Text("不填协议时：局域网 IP 用 http，域名用 https")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { serverUrl = GalgameSettings.DEFAULT_CLOUD_BASE_URL },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("恢复默认地址", fontSize = 12.sp)
                }
                Text(vm.cloudServerStatus, color = Muted, fontSize = 11.sp)
                vm.cloudError?.let {
                    Text(it, color = Color(0xFFFFB4B4), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.testCloudServer(serverUrl) },
                enabled = !vm.cloudTesting,
            ) {
                if (vm.cloudTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = Amber,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (vm.cloudTesting) "连接中…" else "保存并测试")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !vm.cloudTesting) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun CloudWorkCard(
    work: CloudWork,
    downloading: Boolean,
    progress: String?,
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
                        Text(
                            progress ?: "下载中…",
                            color = AmberSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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