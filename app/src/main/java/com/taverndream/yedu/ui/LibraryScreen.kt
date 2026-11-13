package com.taverndream.yedu.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taverndream.yedu.GalgameLibraryEntry
import com.taverndream.yedu.GalgameViewModel

@Composable
fun LibraryScreen(
    vm: GalgameViewModel,
    onImport: () -> Unit,
    onContinue: () -> Unit,
    onSettings: () -> Unit,
) {
    GalgameBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "YEDU  /  VISUAL NOVEL",
                    color = AmberSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Muted)
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "夜读",
                color = Paper,
                fontFamily = FontFamily.Serif,
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "把故事写进 JSON，把画面留给夜晚。",
                color = Muted,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(30.dp))
            when {
                vm.loading -> LoadingCard()
                !vm.hasScripts -> EmptyLibraryCard(onImport = onImport)
                else -> ScriptLibraryList(
                    vm = vm,
                    onContinue = onContinue,
                    onImport = onImport,
                )
            }

            vm.error?.let { message ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = Color(0xFF35181C),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0x66FF9E9E),
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("无法载入剧本", color = Color(0xFFFFC1C1), fontWeight = FontWeight.Bold)
                        Text(message, color = Color(0xFFFFD7D7), fontSize = 12.sp)
                        TextButton(onClick = { vm.retry() }) {
                            Text("重试", color = Amber)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "JSON 驱动剧情与分支 · 图片在后台逐张生成 · 缺图不阻塞文字",
                modifier = Modifier.fillMaxWidth(),
                color = Muted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun LoadingCard() {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(92.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = Amber, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("正在打开剧本…", color = Paper, fontSize = 15.sp)
        }
    }
}

@Composable
private fun EmptyLibraryCard(onImport: () -> Unit) {
    SectionCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.04f), Color.Transparent)
                    ),
                    RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "01",
                    color = Amber.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Serif,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("还没有剧本", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "导入一个 JSON 文件。剧情、选项、分支和每张图的提示词都在里面。",
            color = Muted,
            fontSize = 13.sp,
        )
        PrimaryAction("导入 JSON 剧本", onImport)
        Text(
            "不需要新建世界，也不需要图片包。",
            modifier = Modifier.fillMaxWidth(),
            color = Muted.copy(alpha = 0.75f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScriptLibraryList(
    vm: GalgameViewModel,
    onContinue: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("剧本库", "${vm.library.size} 个 GAL")
        vm.library.forEach { entry ->
            ScriptLibraryCard(
                vm = vm,
                entry = entry,
                isCurrent = vm.currentScriptKey == entry.key,
                onOpen = {
                    vm.selectScript(entry.key)
                    onContinue()
                },
                onRestart = {
                    vm.selectScript(entry.key)
                    vm.startNewGame()
                    onContinue()
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        PrimaryAction("导入新剧本", onImport)
        Text(
            "每次导入都会保留为一个独立剧本，存档和图片分别存放。",
            modifier = Modifier.fillMaxWidth(),
            color = Muted.copy(alpha = 0.75f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScriptLibraryCard(
    vm: GalgameViewModel,
    entry: GalgameLibraryEntry,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onRestart: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.title,
                    color = Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MetaChip("${entry.nodeCount} 幕")
                    MetaChip("${entry.imageCount} 张图", color = Rose)
                    if (isCurrent && vm.imageBusy) MetaChip("绘制中", color = Amber)
                    if (isCurrent) MetaChip("当前", color = AmberSoft)
                }
            }
            Text("JSON", color = Muted.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (isCurrent && vm.imageBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = AmberSoft,
                )
                Text(vm.imageProgress ?: "正在准备图片…", color = Muted, fontSize = 11.sp)
            }
        }

        Text(
            entry.currentNodeTitle.takeIf { it.isNotBlank() }?.let { "上次停在：$it" }
                ?: "从第一幕开始",
            color = Muted,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(2.dp))
        PrimaryAction(if (entry.hasSave) "继续故事" else "开始故事", onOpen)
        SecondaryAction(
            text = "从头开始",
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
