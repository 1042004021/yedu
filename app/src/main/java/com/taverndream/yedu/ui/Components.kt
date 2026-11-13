package com.taverndream.yedu.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/** 全 App 共用的午夜剧场背景，带一点舞台聚光而不是纯黑。 */
@Composable
fun GalgameBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161321), Color(0xFF0B0D14), Ink)
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Amber.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.12f),
                        radius = size.width * 0.72f,
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.82f, size.height * 0.12f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF5E4A7A).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.78f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.1f, size.height * 0.78f),
                )
            },
    ) {
        content()
    }
}

/**
 * 统一按压反馈：按下时轻微缩放，并保留 Material ripple。
 * 用在自绘按钮上，避免只有 Static Surface、没有触摸反馈。
 */
@Composable
fun Modifier.pressableClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

@Composable
fun PressableIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = Muted,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconPressScale",
    )
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val canClick = enabled && !loading
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .pressableClickable(enabled = canClick, onClick = onClick),
        color = if (canClick) Amber else Amber.copy(alpha = 0.35f),
        contentColor = Ink,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.8.dp,
                    color = Ink,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val canClick = enabled && !loading
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .pressableClickable(enabled = canClick, onClick = onClick),
        color = Color.White.copy(alpha = 0.05f),
        contentColor = Paper,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.6.dp,
                    color = Amber,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Panel,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
fun MetaChip(text: String, color: Color = Amber) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            color = Paper,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.let {
            Text(it, color = Muted, fontSize = 11.sp)
        }
    }
}

/**
 * 云端封面/图片：与本地 BlurredFitImage 相同的等比展示，只是 model 使用 URL。
 */
@Composable
fun BlurredFitImageUrl(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    dim: Float = 0.22f,
    cacheKey: String? = null,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.clipToBounds().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) return@Box
        val backgroundRequest = remember(url, cacheKey) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(cacheKey?.let { "$it:thumb" })
                .diskCacheKey(cacheKey)
                .size(256)
                .crossfade(180)
                .build()
        }
        val foregroundRequest = remember(url, cacheKey) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(cacheKey?.let { "$it:full" })
                .diskCacheKey(cacheKey)
                .crossfade(180)
                .build()
        }
        AsyncImage(
            model = backgroundRequest,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius) else Modifier),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        AsyncImage(
            model = foregroundRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}

/**
 * 生成图统一展示：背景用同一张图铺满并模糊，前景按原始比例完整显示，不裁剪。
 */
@Composable
fun BlurredFitImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 28.dp,
    dim: Float = 0.28f,
) {
    val file = path?.let { File(it) }?.takeIf { it.isFile }
    val context = LocalContext.current
    Box(modifier = modifier.clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        if (file == null) return@Box
        val fileVersion = "${file.absolutePath}:${file.lastModified()}"
        val backgroundRequest = remember(fileVersion) {
            ImageRequest.Builder(context)
                .data(file)
                .size(256)
                .memoryCacheKey("$fileVersion:bg")
                .crossfade(180)
                .build()
        }
        val foregroundRequest = remember(fileVersion) {
            ImageRequest.Builder(context)
                .data(file)
                .memoryCacheKey("$fileVersion:fg")
                .crossfade(180)
                .build()
        }
        AsyncImage(
            model = backgroundRequest,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius) else Modifier),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        AsyncImage(
            model = foregroundRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}

@Composable
fun IconTextButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pressableClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Amber else Muted, modifier = Modifier.padding(1.dp))
        Text(text, color = if (enabled) Paper else Muted, fontSize = 13.sp)
    }
}