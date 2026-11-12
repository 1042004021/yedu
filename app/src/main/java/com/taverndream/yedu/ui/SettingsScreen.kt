package com.taverndream.yedu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taverndream.yedu.GalgameViewModel
import com.taverndream.yedu.sd.SdBackendService
import com.taverndream.yedu.sd.SdModel
import com.taverndream.yedu.sd.SdModelManager
import com.taverndream.yedu.sd.SdState
import com.taverndream.yedu.sd.UpscalerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GalgameViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = vm.settings
    var pathRefresh by remember { mutableStateOf(0) }
    val models = remember(vm.modelRevision, pathRefresh) { SdModelManager.models(context) }
    var selectedModelId by remember { mutableStateOf(s.sdModelId) }
    var steps by remember { mutableStateOf(s.sdSteps) }
    var cfg by remember { mutableStateOf(s.sdCfg) }
    var negative by remember { mutableStateOf(s.sdNegative) }
    var styleTags by remember { mutableStateOf(s.styleTags) }
    var upscale by remember { mutableStateOf(s.sdUpscale) }
    var mirror by remember { mutableStateOf(s.hfMirror) }
    var threads by remember { mutableStateOf(s.sdDownloadThreads) }
    var showModelPicker by remember { mutableStateOf(false) }

    val modelState by SdModelManager.state.collectAsState()
    val upscalerState by UpscalerManager.state.collectAsState()
    val engineState by SdBackendService.state.collectAsState()
    val selectedModel = models.firstOrNull { it.id == selectedModelId }
    val selectedReady = selectedModel != null && SdModelManager.isDownloaded(context, selectedModel)
    val upscalerReady = remember(vm.upscalerRevision) { UpscalerManager.isDownloaded(context) }

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
                    "设置",
                    color = Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionCard {
                SectionTitle("生图模型", if (selectedReady) "已就绪" else "未下载")
                Text(
                    "设备：${SdModelManager.soc()}    模型后缀：${SdModelManager.suffix() ?: "不支持 NPU"}",
                    color = Muted,
                    fontSize = 11.sp,
                )
                if (models.isEmpty()) {
                    Text(
                        "当前设备没有可用的端侧 NPU 模型。Galgame 仍可播放文字，但无法在 APK 内生成图片。",
                        color = Rose,
                        fontSize = 12.sp,
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.045f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    selectedModel?.name ?: "未选择模型",
                                    color = Paper,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    selectedModel?.approxSize.orEmpty() + if (selectedReady) " · 已下载" else " · 需要下载",
                                    color = if (selectedReady) Amber else Muted,
                                    fontSize = 11.sp,
                                )
                            }
                            TextButton(onClick = { showModelPicker = true }) {
                                Text("选择", color = Amber)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedModel?.let { vm.downloadSdModel(it.id) } },
                            enabled = selectedModel != null && !selectedReady &&
                                modelState !is SdModelManager.State.Downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                        ) {
                            Text(if (selectedReady) "模型已就绪" else "下载模型")
                        }
                        if (selectedReady) {
                            TextButton(onClick = { selectedModel?.let { vm.deleteSdModel(it) } }) {
                                Text("删除", color = Rose)
                            }
                        }
                    }
                    if (modelState is SdModelManager.State.Downloading) {
                        val st = modelState as SdModelManager.State.Downloading
                        LinearProgressIndicator(
                            progress = { st.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Amber,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                        Text(
                            "${(st.progress * 100).toInt()}%  ${st.bytes / 1048576}MB / ${st.total / 1048576}MB",
                            color = Muted,
                            fontSize = 10.sp,
                        )
                    }
                    (modelState as? SdModelManager.State.Failed)?.let {
                        Text("下载失败：${it.message}", color = Rose, fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            when (engineState) {
                                is SdState.Running -> "生图引擎运行中"
                                is SdState.Starting -> "生图引擎启动中…"
                                is SdState.Error -> "引擎错误：${(engineState as SdState.Error).message}"
                                else -> "生图引擎未启动"
                            },
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        TextButton(
                            onClick = {
                                if (engineState is SdState.Running) vm.stopSdEngine()
                                else if (selectedReady) vm.startSdEngine(selectedModelId)
                            },
                            enabled = selectedReady || engineState is SdState.Running,
                        ) {
                            Text(
                                if (engineState is SdState.Running) "停止" else "预热引擎",
                                color = Amber,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ModelPathsCard(
                refreshKey = pathRefresh,
                onRefresh = { pathRefresh++ },
            )

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("出图参数")
                Text("生图步数 $steps（越大越细，越慢）", color = Paper, fontSize = 13.sp)
                Slider(
                    value = steps.toFloat(),
                    onValueChange = {
                        steps = it.toInt()
                        s.sdSteps = steps
                    },
                    valueRange = 8f..40f,
                    steps = 31,
                )
                Text("提示词引导 CFG ${"%.1f".format(cfg)}", color = Paper, fontSize = 13.sp)
                Slider(
                    value = cfg,
                    onValueChange = {
                        cfg = it
                        s.sdCfg = it
                    },
                    valueRange = 1f..15f,
                )
                OutlinedTextField(
                    value = styleTags,
                    onValueChange = {
                        styleTags = it
                        s.styleTags = it
                    },
                    label = { Text("画风提示词（英文）") },
                    supportingText = { Text("每张图都会追加；留空则不追加") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = negative,
                    onValueChange = {
                        negative = it
                        s.sdNegative = it
                    },
                    label = { Text("负面提示词") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("4x 超分", color = Paper, fontSize = 13.sp)
                        Text("SD1.5 的 512 图会自动超分", color = Muted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = upscale,
                        onCheckedChange = {
                            upscale = it
                            s.sdUpscale = it
                        },
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("超分模型", color = Paper, fontSize = 13.sp)
                        Text(if (upscalerReady) "已就绪（约 8MB）" else "未下载（约 8MB）", color = Muted, fontSize = 10.sp)
                    }
                    when (val st = upscalerState) {
                        is UpscalerManager.State.Downloading -> Text(
                            "${(st.progress * 100).toInt()}%",
                            color = Amber,
                            fontSize = 11.sp,
                        )
                        else -> if (upscalerReady) {
                            TextButton(onClick = { vm.deleteUpscaler() }) {
                                Text("删除", color = Rose)
                            }
                        } else {
                            TextButton(onClick = { vm.downloadUpscaler() }) {
                                Text("下载", color = Amber)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("下载")
                OutlinedTextField(
                    value = mirror,
                    onValueChange = {
                        mirror = it
                        s.hfMirror = it
                    },
                    label = { Text("模型下载源") },
                    supportingText = { Text("默认 aifasthub.com；海外可改回 huggingface.co") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("下载连接数 $threads（1–16 路并发）", color = Paper, fontSize = 13.sp)
                Slider(
                    value = threads.toFloat(),
                    onValueChange = {
                        threads = it.toInt()
                        s.sdDownloadThreads = threads
                    },
                    valueRange = 1f..16f,
                    steps = 14,
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("关于")
                Text("夜读 1.0.0 · 独立 Galgame 引擎", color = Paper, fontSize = 13.sp)
                Text(
                    "只播放 JSON 剧本，不调用 LLM 写剧情；图片由本机端侧模型按 JSON 清单生成。",
                    color = Muted,
                    fontSize = 11.sp,
                )
                Text(
                    "剧情、分支、人物和图片提示词都在 JSON 里；换剧本只需重新导入一个文件。",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showModelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = InkSoft,
            contentColor = Paper,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp),
            ) {
                Text(
                    "选择生图模型",
                    color = Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text("SD1.5 更快更省内存；SDXL 细节更好但需要更多内存。", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    item {
                        ModelGroupHeader("SD1.5 · 轻量快速")
                    }
                    items(models.filter { !it.isSdxl }, key = { it.id }) { model ->
                        ModelPickerRow(
                            model = model,
                            selected = selectedModelId == model.id,
                            ready = SdModelManager.isDownloaded(context, model),
                            onClick = {
                                selectedModelId = model.id
                                s.sdModelId = model.id
                                showModelPicker = false
                            },
                        )
                    }
                    item {
                        ModelGroupHeader("SDXL · 高清细节")
                    }
                    items(models.filter { it.isSdxl }, key = { it.id }) { model ->
                        ModelPickerRow(
                            model = model,
                            selected = selectedModelId == model.id,
                            ready = SdModelManager.isDownloaded(context, model),
                            onClick = {
                                selectedModelId = model.id
                                s.sdModelId = model.id
                                showModelPicker = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModelGroupHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        color = AmberSoft,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ModelPickerRow(
    model: SdModel,
    selected: Boolean,
    ready: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.name,
                color = if (selected) Amber else Paper,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                listOf(model.approxSize, if (ready) "已下载" else "未下载").filter { it.isNotBlank() }.joinToString(" · "),
                color = Muted,
                fontSize = 10.sp,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "已选择", tint = Amber, modifier = Modifier.size(17.dp))
        } else if (ready) {
            Text("已下载", color = Muted, fontSize = 9.sp)
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
}