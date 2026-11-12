package com.taverndream.yedu.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.taverndream.yedu.DisplayLine
import com.taverndream.yedu.GalgameViewModel
import com.taverndream.yedu.data.GalgameChoice
import com.taverndream.yedu.util.ImageSaver
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    vm: GalgameViewModel,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val path = pendingSave
        pendingSave = null
        if (granted && path != null) {
            scope.launch {
                runCatching { ImageSaver.saveToGallery(context, File(path)) }
                    .onSuccess { Toast.makeText(context, "已保存到系统相册", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun saveCurrentImage() {
        val path = vm.backgroundPath ?: return
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingSave = path
            saveLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch {
                runCatching { ImageSaver.saveToGallery(context, File(path)) }
                    .onSuccess { Toast.makeText(context, "已保存到系统相册", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    LaunchedEffect(vm.modelRevision) {
        if (vm.modelRevision > 0 && vm.hasScript) vm.retryImage()
    }

    val node = vm.currentNode
    val line = vm.displayLines.getOrNull(vm.lineIndex)
    val atEnd = vm.atEnd
    val choices = vm.choices
    val canAdvance = !vm.isEnding &&
        (vm.lineIndex < vm.displayLines.lastIndex || (choices.isEmpty() && node?.next?.isNotBlank() == true))

    Box(Modifier.fillMaxSize().background(Ink).clickable { if (canAdvance) vm.advance() }) {
        PlayerBackground(
            path = vm.backgroundPath,
            loading = vm.imageBusy && vm.backgroundPath == null,
            eventCg = vm.backgroundKind == "event_cg",
        )

        Column(Modifier.fillMaxSize()) {
            PlayerTopBar(
                title = node?.title?.ifBlank { vm.scriptTitle } ?: vm.scriptTitle,
                subtitle = listOfNotNull(
                    node?.location?.takeIf { it.isNotBlank() },
                    node?.time?.takeIf { it.isNotBlank() },
                ).joinToString("  ·  "),
                scene = ((node?.let { vm.history.size } ?: 0)).coerceAtLeast(1),
                onBack = onBack,
                menuOpen = menuOpen,
                onMenuOpen = { menuOpen = true },
                onMenuDismiss = { menuOpen = false },
                onRestart = {
                    menuOpen = false
                    vm.startNewGame()
                },
                onImport = {
                    menuOpen = false
                    onImport()
                },
                onSettings = {
                    menuOpen = false
                    onSettings()
                },
                onSaveImage = if (vm.backgroundPath != null) {
                    { menuOpen = false; saveCurrentImage() }
                } else null,
            )

            Spacer(Modifier.weight(1f))

            if (choices.isNotEmpty()) {
                ChoicePanel(choices = choices, onChoose = { vm.choose(it.id) })
            }

            when {
                vm.isEnding -> EndingPanel(
                    onRestart = { vm.startNewGame() },
                    onBack = onBack,
                )
                else -> DialoguePanel(
                    line = line,
                    atEnd = atEnd,
                    canAdvance = canAdvance,
                    imageBusy = vm.imageBusy,
                    scene = (vm.history.size).coerceAtLeast(1),
                    onLineClick = { if (canAdvance) vm.advance() },
                )
            }
        }

        ImageStatusPill(
            vm = vm,
            onSettings = onSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 62.dp),
        )
    }
}

@Composable
private fun PlayerBackground(
    path: String?,
    loading: Boolean,
    eventCg: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        if (path != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF241F31), Color(0xFF11131B), Ink)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Amber, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("正在绘制这一幕…", color = Muted, fontSize = 12.sp)
                    }
                } else {
                    Text("SCENE", color = Color.White.copy(alpha = 0.08f), fontSize = 72.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.48f),
                        0.26f to Color.Transparent,
                        0.58f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.92f),
                    )
                )
        )
        if (eventCg) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 58.dp, end = 16.dp),
                color = Color.Black.copy(alpha = 0.42f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Amber.copy(alpha = 0.35f)),
            ) {
                Text(
                    "EVENT CG",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Amber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    subtitle: String,
    scene: Int,
    onBack: () -> Unit,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRestart: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onSaveImage: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Paper)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Paper,
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf("SCENE $scene", subtitle).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = onMenuOpen) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Paper)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onMenuDismiss,
                containerColor = PanelStrong,
            ) {
                DropdownMenuItem(
                    text = { Text("从头开始", color = Paper) },
                    onClick = onRestart,
                )
                DropdownMenuItem(
                    text = { Text("更换剧本", color = Paper) },
                    onClick = onImport,
                )
                onSaveImage?.let {
                    DropdownMenuItem(
                        text = { Text("保存当前画面", color = Paper) },
                        onClick = it,
                    )
                }
                DropdownMenuItem(
                    text = { Text("设置", color = Paper) },
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun DialoguePanel(
    line: DisplayLine?,
    atEnd: Boolean,
    canAdvance: Boolean,
    imageBusy: Boolean,
    scene: Int,
    onLineClick: () -> Unit,
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clickable(onClick = onLineClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SCENE ${scene.toString().padStart(2, '0')}",
                    color = AmberSoft,
                    fontSize = 9.sp,
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.10f))
                )
                if (imageBusy) {
                    Text("绘制中", color = Muted, fontSize = 9.sp)
                }
            }

            val textScroll = rememberScrollState()
            LaunchedEffect(line?.text) { textScroll.scrollTo(0) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(textScroll),
            ) {
                Crossfade(targetState = line, animationSpec = tween(150), label = "galgame-line") { current ->
                    if (current == null) {
                        Text("这一幕没有台词，点击画面继续。", color = Muted, fontSize = 14.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!current.narration && current.speaker.isNotBlank()) {
                                Surface(
                                    color = Amber.copy(alpha = 0.14f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, Amber.copy(alpha = 0.28f)),
                                ) {
                                    Text(
                                        current.speaker,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                                        color = Amber,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                current.text,
                                color = if (current.narration) Muted else Paper,
                                fontSize = if (current.narration) 18.sp else 19.sp,
                                lineHeight = if (current.narration) 29.sp else 31.sp,
                                fontStyle = if (current.narration) FontStyle.Italic else FontStyle.Normal,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        !atEnd -> "点击继续"
                        canAdvance -> "点击画面进入下一幕"
                        else -> "选择你的行动"
                    },
                    color = Muted,
                    fontSize = 10.sp,
                )
                if (canAdvance) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AmberSoft,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoicePanel(choices: List<GalgameChoice>, onChoose: (GalgameChoice) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            "选择你的行动",
            color = Amber,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 238.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(choices, key = { _, choice -> choice.id }) { index, choice ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(choice) },
                    color = PanelStrong.copy(alpha = 0.96f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Text(
                            (index + 1).toString().padStart(2, '0'),
                            color = AmberSoft,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(choice.text, color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (choice.hint.isNotBlank()) {
                                Text(choice.hint, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = AmberSoft,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndingPanel(onRestart: () -> Unit, onBack: () -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("END", color = Amber, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text("故事到这里结束", color = Paper, fontFamily = FontFamily.Serif, fontSize = 23.sp)
            Text("可以重新开始，或回到标题换一个剧本。", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            PrimaryAction("重新开始", onRestart)
            TextButton(onClick = onBack) {
                Text("回到标题", color = Muted)
            }
        }
    }
}

@Composable
private fun ImageStatusPill(
    vm: GalgameViewModel,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = vm.imageError
    val progress = vm.imageProgress
    if (error == null && progress == null) return
    val needsModel = error != null && (
        error.contains("模型") || error.contains("设置") || error.contains("Base URL")
    )
    val isError = error != null && !needsModel
    Surface(
        modifier = modifier.clickable {
            if (needsModel) onSettings() else if (isError) vm.retryImage()
        },
        color = when {
            needsModel -> Color(0xCC161B26)
            isError -> Color(0xCC3A1518)
            else -> Color(0xCC111722)
        },
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            when {
                needsModel -> Amber.copy(alpha = 0.35f)
                isError -> Color(0x66FF8A8A)
                else -> Color.White.copy(alpha = 0.12f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when {
                needsModel -> {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = Amber, modifier = Modifier.size(13.dp))
                    Text("未配置生图模型 · 去设置", color = Amber, fontSize = 10.sp)
                }
                isError -> {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFFFFA3A3), modifier = Modifier.size(13.dp))
                    Text("配图失败，点此重试", color = Color(0xFFFFC3C3), fontSize = 10.sp)
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.4.dp, color = Amber)
                    Text(progress.orEmpty(), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}