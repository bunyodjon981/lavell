package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.ClipItem
import com.example.model.MontageProject
import com.example.ui.theme.CoralMagenta
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.GoldBeat
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Composable
fun TimelineSection(
    project: MontageProject,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedClipIndex: Int?,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectClip: (Int?) -> Unit,
    onOpenTransition: (Int) -> Unit,
    onAddPhotos: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = project.totalDurationMs.coerceAtLeast(1000L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(1.dp, StudioOutline.copy(alpha = 0.4f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // Transport & Scrubber Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onSeek(0L) },
                modifier = Modifier
                    .size(40.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Boshiga qaytish",
                    tint = TextSecondary
                )
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyanElectric.copy(alpha = 0.16f))
                    .testTag("timeline_play_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "To'xtatish" else "Ijro",
                    tint = CyanElectric
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Slider(
                value = (playheadMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac -> onSeek((frac * totalMs).toLong()) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = CyanElectric,
                    activeTrackColor = CyanElectric,
                    inactiveTrackColor = StudioSurfaceElevated
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "${formatTimecode(playheadMs)} / ${formatTimecode(totalMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Music Track Lane (VN-style waveform + beat dots)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (project.music != null) Color(0xFF152538) else StudioSurfaceVariant
                )
                .border(
                    1.dp,
                    if (project.music != null) CyanElectric.copy(alpha = 0.4f) else StudioOutline.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onOpenMusic)
                .testTag("timeline_music_lane")
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (project.music != null) {
                val track = project.music
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = 48
                    val barW = size.width / (barCount * 1.6f)
                    val gap = (size.width - barCount * barW) / (barCount - 1).coerceAtLeast(1)
                    for (i in 0 until barCount) {
                        val hFrac = 0.25f + 0.65f * abs(kotlin.math.sin((i * 0.63f).toDouble())).toFloat()
                        val barH = size.height * 0.58f * hFrac
                        val x = i * (barW + gap)
                        drawRoundRect(
                            color = CyanElectric.copy(alpha = 0.28f),
                            topLeft = Offset(x, (size.height - barH) / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                    // Beat markers along timeline
                    track.beatMarksMs.forEach { beatMs ->
                        if (beatMs <= totalMs) {
                            val bx = (beatMs.toFloat() / totalMs.toFloat()) * size.width
                            drawCircle(
                                color = GoldBeat,
                                radius = 2.6.dp.toPx(),
                                center = Offset(bx, size.height - 6.dp.toPx())
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = CyanElectric,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${track.title} • ${track.bpm} BPM",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "+ Musiqa qo'shish (shablon yoki xotiradan)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Text Overlay Track Lane
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioSurfaceVariant)
                .clickable(onClick = onOpenText)
                .testTag("timeline_text_lane")
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.TextFields,
                contentDescription = null,
                tint = CoralMagenta,
                modifier = Modifier.size(15.dp)
            )
            if (project.texts.isEmpty()) {
                Text(
                    text = "+ Matn / Sarlavha qo'shish",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(project.texts.size) { idx ->
                        val item = project.texts[idx]
                        Surface(
                            color = CoralMagenta.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD8E4),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // VN-Style Photo Clips Track + Transition Junction Buttons
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(project.clips.size) { index ->
                val clip = project.clips[index]
                val isSelected = selectedClipIndex == index

                ClipThumbnailCard(
                    clip = clip,
                    index = index,
                    isSelected = isSelected,
                    onClick = { onSelectClip(if (isSelected) null else index) }
                )

                if (index < project.clips.lastIndex) {
                    TransitionJunctionButton(
                        badgeCode = clip.transition.badgeCode,
                        onClick = { onOpenTransition(index) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 66.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioSurfaceElevated)
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(CyanElectric.copy(0.6f), CoralMagenta.copy(0.6f))),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(onClick = onAddPhotos)
                        .testTag("timeline_add_clip_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Rasm qo'shish",
                        tint = CyanElectric,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipThumbnailCard(
    clip: ClipItem,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val widthDp = ((clip.durationMs / 1000f) * 46f).coerceIn(66f, 128f).dp

    Box(
        modifier = Modifier
            .size(width = widthDp, height = 66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSurfaceElevated)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) CyanElectric else StudioOutline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("timeline_clip_$index")
    ) {
        if (!clip.imagePath.isNullOrBlank()) {
            AsyncImage(
                model = File(clip.imagePath),
                contentDescription = clip.slotLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val resId = clip.sampleDrawableName?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            } ?: 0
            if (resId != 0) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = clip.slotLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC0B0B13))
                    )
                )
        )

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color(0x990B0B13), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )

        Text(
            text = String.format(Locale.US, "%.1fs", clip.durationMs / 1000f),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) CyanElectric else Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
        )
    }
}

@Composable
private fun TransitionJunctionButton(
    badgeCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .size(width = 30.dp, height = 34.dp)
            .border(1.dp, GoldBeat.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = "O'tish effekti",
                tint = GoldBeat,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = badgeCode,
                style = MaterialTheme.typography.labelSmall,
                color = GoldBeat
            )
        }
    }
}

fun formatTimecode(ms: Long): String {
    val totalTenths = (ms.coerceAtLeast(0L) / 100L)
    val tenths = totalTenths % 10
    val totalSecs = totalTenths / 10
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}
