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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    var showGraph by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
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
    val lineText = line?.text.orEmpty()
    val atEnd = vm.atEnd
    val choices = vm.choices
    val hasSpeaker = line != null && !line.narration && line.speaker.isNotBlank()
    val textStyle = TextStyle(
        fontSize = if (line?.narration == true) 18.sp else 19.sp,
        lineHeight = if (line?.narration == true) 29.sp else 31.sp,
        fontFamily = if (line?.narration == true) FontFamily.Serif else FontFamily.Default,
        fontStyle = if (line?.narration == true) FontStyle.Italic else FontStyle.Normal,
    )
    // 对话框高度固定；旁白留出标签空间，角色台词留出名字空间。
    val textMaxHeight = when {
        line?.narration == true -> 108.dp
        hasSpeaker -> 96.dp
        else -> 132.dp
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val textWidthPx = with(density) { (maxWidth - 40.dp).toPx().toInt().coerceAtLeast(1) }
        val textHeightPx = with(density) { textMaxHeight.toPx().toInt().coerceAtLeast(1) }
        val lineHeightPx = with(density) { textStyle.lineHeight.toPx() }.coerceAtLeast(1f)
        val maxLines = (textHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
        val pages = remember(lineText, line?.narration, hasSpeaker, textWidthPx, maxLines) {
            paginateText(lineText, textStyle, textWidthPx, maxLines, measurer)
        }
        var pageIndex by remember(lineText, line?.speaker, line?.narration, vm.lineIndex) {
            mutableStateOf(0)
        }
        LaunchedEffect(pages) {
            if (pageIndex > pages.lastIndex) pageIndex = pages.lastIndex
        }
        val pageText = pages.getOrElse(pageIndex) { "" }
        val hasMorePages = pageIndex < pages.lastIndex
        val canAdvance = hasMorePages || (!vm.isEnding &&
            (vm.lineIndex < vm.displayLines.lastIndex ||
                (choices.isEmpty() && node?.next?.isNotBlank() == true)))

        fun advanceOne() {
            if (pageIndex < pages.lastIndex) pageIndex++ else if (!vm.isEnding) vm.advance()
        }

        Box(Modifier.fillMaxSize().clickable { if (canAdvance) advanceOne() }) {
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
                onGraph = {
                    menuOpen = false
                    showGraph = true
                },
                onGallery = {
                    menuOpen = false
                    showGallery = true
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
                    pageText = pageText,
                    pageIndex = pageIndex,
                    pageCount = pages.size,
                    atEnd = atEnd,
                    canAdvance = canAdvance,
                    imageBusy = vm.imageBusy,
                    scene = (vm.history.size).coerceAtLeast(1),
                    onLineClick = { if (canAdvance) advanceOne() },
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

    if (showGraph) {
        BranchMapDialog(
            nodes = vm.graphNodes,
            currentNodeId = node?.id,
            onJump = { nodeId ->
                showGraph = false
                vm.jumpToNode(nodeId)
            },
            onDismiss = { showGraph = false },
        )
    }
    if (showGallery) {
        GalleryDialog(
            items = vm.galleryItems,
            onRegenerate = { imageId, prompt -> vm.regenerateImage(imageId, prompt) },
            onDismiss = { showGallery = false },
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
            BlurredFitImage(
                path = path,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 18.dp,
                dim = 0.30f,
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
    onGraph: () -> Unit,
    onGallery: () -> Unit,
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
                DropdownMenuItem(
                    text = { Text("剧情拓谱", color = Paper) },
                    onClick = onGraph,
                )
                DropdownMenuItem(
                    text = { Text("画廊", color = Paper) },
                    onClick = onGallery,
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

/** 按固定宽高把一段文字切成多页，避免对话框被长文本撑高。 */
private fun paginateText(
    text: String,
    style: TextStyle,
    widthPx: Int,
    maxLines: Int,
    measurer: TextMeasurer,
): List<String> {
    if (text.isBlank()) return listOf("")
    val pages = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val remaining = text.substring(start)
        val layout = measurer.measure(
            text = AnnotatedString(remaining),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = widthPx),
        )
        if (!layout.hasVisualOverflow) {
            pages += remaining.trimEnd()
            break
        }
        var end = layout.getLineEnd(maxLines - 1, visibleEnd = false)
        if (end <= 0) end = remaining.length
        if (end < remaining.length) {
            // 英文尽量在空格/换行处断页，中文没有空格时按行尾断。
            val breakAt = remaining.substring(0, end)
                .lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
            if (breakAt > 0 && end - breakAt <= 24) end = breakAt
            if (end > 0 && end < remaining.length &&
                Character.isLowSurrogate(remaining[end]) &&
                Character.isHighSurrogate(remaining[end - 1])
            ) {
                end--
            }
        }
        if (end <= 0) break
        val page = remaining.substring(0, end).trim()
        if (page.isNotEmpty()) pages += page
        start += end
        while (start < text.length && text[start].isWhitespace()) start++
    }
    return pages.ifEmpty { listOf("") }
}

@Composable
private fun DialoguePanel(
    line: DisplayLine?,
    pageText: String,
    pageIndex: Int,
    pageCount: Int,
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
                .height(250.dp)
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Crossfade(targetState = line, animationSpec = tween(150), label = "galgame-line") { current ->
                    if (current == null) {
                        Text("这一幕没有台词，点击画面继续。", color = Muted, fontSize = 14.sp)
                    } else if (current.narration) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = 0.035f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    "旁白  ·  NARRATION",
                                    color = AmberSoft.copy(alpha = 0.78f),
                                    fontSize = 8.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    pageText,
                                    color = Paper.copy(alpha = 0.80f),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 18.sp,
                                    lineHeight = 29.sp,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current.speaker.isNotBlank()) {
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
                                pageText,
                                color = Paper,
                                fontSize = 19.sp,
                                lineHeight = 31.sp,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pageCount > 1) {
                    Text(
                        "第 ${pageIndex + 1} / $pageCount 页",
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        when {
                            pageIndex < pageCount - 1 -> "点击继续"
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