package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.MontageProject
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurfaceElevated
import com.example.video.MontageBitmapCache
import com.example.video.MontageFrameRenderer

@Composable
fun MontagePreviewCanvas(
    project: MontageProject,
    playheadMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmapCache = remember(context) { MontageBitmapCache(context) }
    val renderer = remember(context, bitmapCache) { MontageFrameRenderer(context, bitmapCache) }

    DisposableEffect(bitmapCache) {
        onDispose { bitmapCache.clear() }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val ratio = project.aspectRatio.ratio
        Box(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .aspectRatio(ratio, matchHeightConstraintsFirst = maxHeight * ratio <= maxWidth)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF07070D))
                .border(1.dp, StudioOutline.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                .clickable(onClick = onTogglePlay)
                .testTag("preview_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    renderer.drawFrame(
                        canvas = canvas.nativeCanvas,
                        width = size.width,
                        height = size.height,
                        project = project,
                        timeMs = playheadMs,
                        maxBitmapDim = 960
                    )
                }
            }

            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0B0B13))
                        .border(1.dp, CyanElectric.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Ijro etish",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = StudioSurfaceElevated.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = project.aspectRatio.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanElectric,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x880B0B13)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "To'xtatish",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
