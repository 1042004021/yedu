package com.taverndream.yedu.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taverndream.yedu.sd.SdModelManager

/**
 * 模型目录设置：显示所有会被扫描的父目录，并处理 Android 11+ 的所有文件访问授权。
 * 用户把模型文件夹放到任意目录下的 <id>/unet.bin，App 就能直接读取，不必 adb 推 data。
 */
@Composable
fun ModelPathsCard(
    refreshKey: Int,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    var extraDirs by remember(refreshKey) { mutableStateOf(SdModelManager.extraDirsText(context)) }
    val access = remember(refreshKey) { SdModelManager.hasStorageAccess(context) }
    val roots = remember(refreshKey, extraDirs) { SdModelManager.rootDirs(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    SectionCard {
        SectionTitle("模型目录", if (access) "可读取" else "需要授权")
        Text(
            "把下载好的模型解压到下面任意一个父目录，文件夹名必须和模型 ID 一致。",
            color = Muted,
            fontSize = 11.sp,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.04f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                roots.forEachIndexed { index, root ->
                    Text(
                        "${index + 1}. ${root.absolutePath}",
                        color = if (index == 0) Amber else Paper,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Text(
            "示例：Download/Galgame/models/realvisxl/unet.bin\n" +
                "SD1.5 模型把整个解压目录放进去即可；下载的 zip 不用改名。",
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 16.sp,
        )
        OutlinedTextField(
            value = extraDirs,
            onValueChange = {
                extraDirs = it
                SdModelManager.setExtraDirsText(context, it)
            },
            label = { Text("自定义父目录") },
            supportingText = { Text("每行一个父目录，也可以分号分隔；父目录下面再放 <模型ID>/unet.bin") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!access) {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    )
                                }
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    },
                ) {
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "授权所有文件访问"
                        } else {
                            "授权存储读取"
                        },
                        color = Amber,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text("已获得存储读取权限", color = Amber, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) {
                Text("刷新目录", color = Muted)
            }
        }
    }
}