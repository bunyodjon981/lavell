package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCameraBack
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AspectRatioOption
import com.example.model.ClipItem
import com.example.model.ExportState
import com.example.model.FilterPreset
import com.example.model.MontageProject
import com.example.model.MotionEffect
import com.example.model.MusicTrack
import com.example.model.TextAnimationStyle
import com.example.model.TransitionType
import com.example.ui.components.ExportSheet
import com.example.ui.components.FilterGradeSheet
import com.example.ui.components.MontagePreviewCanvas
import com.example.ui.components.MotionDurationSheet
import com.example.ui.components.MusicStudioSheet
import com.example.ui.components.TextOverlaySheet
import com.example.ui.components.TimelineSection
import com.example.ui.components.TransitionPickerSheet
import com.example.ui.theme.CoralMagenta
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.GoldBeat
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditorScreen(
    project: MontageProject,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedClipIndex: Int?,
    exportState: ExportState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectClip: (Int?) -> Unit,
    onRenameProject: (String) -> Unit,
    onSetAspectRatio: (AspectRatioOption) -> Unit,
    onAddPhotos: () -> Unit,
    onReplaceClipPhoto: (Int) -> Unit,
    onUpdateClip: (Int, (ClipItem) -> ClipItem) -> Unit,
    onApplyFilterToAll: (FilterPreset) -> Unit,
    onApplyMotionToAll: (MotionEffect) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onDuplicateClip: (Int) -> Unit,
    onDeleteClip: (Int) -> Unit,
    onAddText: (String, TextAnimationStyle, Long) -> Unit,
    onDeleteText: (String) -> Unit,
    onSelectBuiltInMusic: (MusicTrack) -> Unit,
    onPickMusicFromPhone: () -> Unit,
    onUpdateMusicSettings: (Float, Long, Long) -> Unit,
    onRemoveMusic: () -> Unit,
    onExportMp4: (String) -> Unit,
    onExportLvl: (String) -> Unit,
    onResetExportState: () -> Unit,
    onShareFile: (String, String) -> Unit
) {
    BackHandler(onBack = onBack)

    var transitionClipIdx by remember { mutableStateOf<Int?>(null) }
    var showMusicSheet by remember { mutableStateOf(false) }
    var showFilterSheetForClipIdx by remember { mutableStateOf<Int?>(null) }
    var showMotionSheetForClipIdx by remember { mutableStateOf<Int?>(null) }
    var showTextSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRatioMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Editor Top Studio Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Orqaga",
                    tint = Color.White
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showRenameDialog = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Nomini o'zgartirish",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Surface(
                onClick = { showRatioMenu = true },
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, StudioOutline, RoundedCornerShape(10.dp))
            ) {
                Text(
                    text = project.aspectRatio.shortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanElectric,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    onResetExportState()
                    showExportSheet = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("editor_export_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = "Eksport",
                    tint = Color(0xFF001F26),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Eksport", color = Color(0xFF001F26), fontWeight = FontWeight.Bold)
            }
        }

        // Preview Viewport
        MontagePreviewCanvas(
            project = project,
            playheadMs = playheadMs,
            isPlaying = isPlaying,
            onTogglePlay = onTogglePlay,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // VN-Style Multi-Track Timeline
        TimelineSection(
            project = project,
            playheadMs = playheadMs,
            isPlaying = isPlaying,
            selectedClipIndex = selectedClipIndex,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onSelectClip = onSelectClip,
            onOpenTransition = { idx -> transitionClipIdx = idx },
            onAddPhotos = onAddPhotos,
            onOpenMusic = { showMusicSheet = true },
            onOpenText = { showTextSheet = true }
        )

        // Contextual Bottom Tool Dock (VN-style)
        Surface(
            color = StudioSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            val activeClipIdx = selectedClipIndex?.takeIf { it in project.clips.indices }
            val playheadClipIdx = remember(project.clips, playheadMs) {
                var cursor = 0L
                var idx = 0
                for (i in project.clips.indices) {
                    val dur = project.clips[i].durationMs.coerceAtLeast(200L)
                    if (playheadMs < cursor + dur || i == project.clips.lastIndex) {
                        idx = i
                        break
                    }
                    cursor += dur
                }
                idx
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeClipIdx == null) {
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.MotionPhotosOn,
                            label = "Animatsiya",
                            accent = CoralMagenta,
                            onClick = {
                                if (project.clips.isNotEmpty()) {
                                    onSelectClip(playheadClipIdx)
                                    showMotionSheetForClipIdx = playheadClipIdx
                                }
                            },
                            testTag = "tool_animation_50"
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.MusicNote,
                            label = "Musiqa",
                            accent = CyanElectric,
                            onClick = { showMusicSheet = true },
                            testTag = "tool_music"
                        )
                    }
                    if (project.clips.size > 1) {
                        item {
                            EditorToolButton(
                                icon = Icons.Rounded.SwapHoriz,
                                label = "O'tish",
                                accent = GoldBeat,
                                onClick = {
                                    transitionClipIdx = playheadClipIdx.coerceAtMost(project.clips.lastIndex - 1)
                                },
                                testTag = "tool_transition"
                            )
                        }
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.TextFields,
                            label = "Matn",
                            accent = CoralMagenta,
                            onClick = { showTextSheet = true },
                            testTag = "tool_text"
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.AddPhotoAlternate,
                            label = "Rasm +",
                            accent = CyanElectric,
                            onClick = onAddPhotos,
                            testTag = "tool_add_photo"
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.FilterVintage,
                            label = "Filtr",
                            accent = GoldBeat,
                            onClick = {
                                if (project.clips.isNotEmpty()) showFilterSheetForClipIdx = playheadClipIdx
                            },
                            testTag = "tool_filter"
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.AspectRatio,
                            label = "Nisbat",
                            accent = TextPrimary,
                            onClick = { showRatioMenu = true },
                            testTag = "tool_ratio"
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.FolderZip,
                            label = ".lvl Saqlash",
                            accent = GoldBeat,
                            onClick = {
                                onResetExportState()
                                showExportSheet = true
                            },
                            testTag = "tool_export_lvl"
                        )
                    }
                } else {
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.MotionPhotosOn,
                            label = "Animatsiya",
                            accent = CoralMagenta,
                            onClick = { showMotionSheetForClipIdx = activeClipIdx },
                            testTag = "tool_clip_animation_50"
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.PhotoCameraBack,
                            label = "Almashtir",
                            accent = CyanElectric,
                            onClick = { onReplaceClipPhoto(activeClipIdx) }
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.FilterVintage,
                            label = "Filtr",
                            accent = GoldBeat,
                            onClick = { showFilterSheetForClipIdx = activeClipIdx }
                        )
                    }
                    if (activeClipIdx < project.clips.lastIndex) {
                        item {
                            EditorToolButton(
                                icon = Icons.Rounded.SwapHoriz,
                                label = "O'tish",
                                accent = GoldBeat,
                                onClick = { transitionClipIdx = activeClipIdx }
                            )
                        }
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Nusxa",
                            accent = TextPrimary,
                            onClick = { onDuplicateClip(activeClipIdx) }
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            label = "Chapga",
                            accent = TextPrimary,
                            onClick = { onMoveClip(activeClipIdx, -1) }
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            label = "O'ngga",
                            accent = TextPrimary,
                            onClick = { onMoveClip(activeClipIdx, 1) }
                        )
                    }
                    item {
                        EditorToolButton(
                            icon = Icons.Rounded.DeleteOutline,
                            label = "O'chirish",
                            accent = MaterialTheme.colorScheme.error,
                            onClick = { onDeleteClip(activeClipIdx) }
                        )
                    }
                }
            }
        }
    }

    // --- Modal Sheets & Dialogs ---

    transitionClipIdx?.let { idx ->
        val clip = project.clips.getOrNull(idx)
        if (clip != null) {
            TransitionPickerSheet(
                clip = clip,
                onDismiss = { transitionClipIdx = null },
                onApply = { type: TransitionType, dur: Long ->
                    onUpdateClip(idx) { it.copy(transition = type, transitionDurationMs = dur) }
                }
            )
        }
    }

    if (showMusicSheet) {
        MusicStudioSheet(
            currentMusic = project.music,
            onDismiss = { showMusicSheet = false },
            onSelectBuiltIn = onSelectBuiltInMusic,
            onPickFromPhone = onPickMusicFromPhone,
            onUpdateSettings = onUpdateMusicSettings,
            onRemoveMusic = onRemoveMusic
        )
    }

    showFilterSheetForClipIdx?.let { idx ->
        val clip = project.clips.getOrNull(idx)
        if (clip != null) {
            FilterGradeSheet(
                clip = clip,
                onDismiss = { showFilterSheetForClipIdx = null },
                onApplyClip = { preset, br, ct, sat ->
                    onUpdateClip(idx) {
                        it.copy(filter = preset, brightness = br, contrast = ct, saturation = sat)
                    }
                },
                onApplyAll = onApplyFilterToAll
            )
        }
    }

    showMotionSheetForClipIdx?.let { idx ->
        val clip = project.clips.getOrNull(idx)
        if (clip != null) {
            MotionDurationSheet(
                clip = clip,
                onDismiss = { showMotionSheetForClipIdx = null },
                onUpdate = { motion: MotionEffect, dur: Long, zoom: Float ->
                    onUpdateClip(idx) {
                        it.copy(motion = motion, durationMs = dur, zoomScale = zoom)
                    }
                },
                onApplyMotionToAll = onApplyMotionToAll
            )
        }
    }

    if (showTextSheet) {
        TextOverlaySheet(
            texts = project.texts,
            onDismiss = { showTextSheet = false },
            onAddText = onAddText,
            onDeleteText = onDeleteText
        )
    }

    if (showExportSheet) {
        ExportSheet(
            defaultFileName = project.name,
            exportState = exportState,
            onDismiss = {
                onResetExportState()
                showExportSheet = false
            },
            onExportMp4 = onExportMp4,
            onExportLvl = onExportLvl,
            onShareFile = onShareFile
        )
    }

    if (showRenameDialog) {
        var nameInput by remember(project.name) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = StudioSurface,
            title = { Text("Loyiha nomi") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Nom kiriting") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameProject(nameInput)
                    showRenameDialog = false
                }) {
                    Text("Saqlash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }

    if (showRatioMenu) {
        AlertDialog(
            onDismissRequest = { showRatioMenu = false },
            containerColor = StudioSurface,
            title = { Text("Kadr nisbatini tanlang") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectRatioOption.entries.forEach { opt ->
                        val active = project.aspectRatio == opt
                        Surface(
                            onClick = {
                                onSetAspectRatio(opt)
                                showRatioMenu = false
                            },
                            color = if (active) CyanElectric.copy(alpha = 0.16f) else StudioSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (active) CyanElectric else StudioOutline.copy(0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(opt.label, color = Color.White, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${opt.exportWidth}×${opt.exportHeight}",
                                    color = if (active) CyanElectric else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRatioMenu = false }) {
                    Text("Yopish")
                }
            }
        )
    }
}

@Composable
private fun EditorToolButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        onClick = onClick,
        color = StudioSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}
