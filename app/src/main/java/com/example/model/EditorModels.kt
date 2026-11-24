package com.example.model

import java.util.UUID

enum class AspectRatioOption(
    val label: String,
    val shortLabel: String,
    val ratio: Float,
    val exportWidth: Int,
    val exportHeight: Int
) {
    PORTRAIT_9_16("9:16 Reels", "9:16", 9f / 16f, 720, 1280),
    CLASSIC_4_5("4:5 Insta", "4:5", 4f / 5f, 720, 900),
    SQUARE_1_1("1:1 Kvadrat", "1:1", 1f, 720, 720),
    LANDSCAPE_16_9("16:9 Kino", "16:9", 16f / 9f, 1280, 720)
}

enum class TransitionType(val label: String, val badgeCode: String) {
    NONE("Kesish", "CUT"),
    CROSSFADE("Yumshoq", "MIX"),
    ZOOM_IN("Zoom +", "Z+"),
    ZOOM_OUT("Zoom -", "Z-"),
    SLIDE_LEFT("Chapga", "SL"),
    SLIDE_RIGHT("O'ngga", "SR"),
    FLASH_BEAT("Chaqnash", "FL"),
    GLITCH_SHAKE("Glitch", "GL")
}

enum class MotionCategory(val label: String) {
    ALL("Barchasi (50)"),
    ZOOM("Zoom"),
    SLIDE("Siljish"),
    ROTATE("Aylanish"),
    BEAT_LIGHT("Ritm & Nur"),
    GLITCH_FX("Glitch & FX")
}

enum class MotionEffect(
    val label: String,
    val category: MotionCategory
) {
    // 1..10: Zoom & Static
    NONE("Statik (Asl)", MotionCategory.ZOOM),
    KEN_BURNS_IN("Sekin Zoom +", MotionCategory.ZOOM),
    KEN_BURNS_OUT("Sekin Zoom -", MotionCategory.ZOOM),
    FAST_ZOOM_IN("Tez Yaqinlashish", MotionCategory.ZOOM),
    FAST_ZOOM_OUT("Tez Uzoqlashish", MotionCategory.ZOOM),
    ZOOM_PUNCH("Zarbali Zoom", MotionCategory.ZOOM),
    ELASTIC_ZOOM("Elastik Zoom", MotionCategory.ZOOM),
    DRAMATIC_PUSH("Kino Yaqinlashish", MotionCategory.ZOOM),
    DRAMATIC_PULL("Kino Uzoqlashish", MotionCategory.ZOOM),
    BREATHE_ZOOM("Nafas Zoom", MotionCategory.ZOOM),

    // 11..20: Pan & Slide
    PAN_LEFT("Chapga Surish", MotionCategory.SLIDE),
    PAN_RIGHT("O'ngga Surish", MotionCategory.SLIDE),
    PAN_UP("Yuqoriga Surish", MotionCategory.SLIDE),
    PAN_DOWN("Pastga Surish", MotionCategory.SLIDE),
    DIAGONAL_NW_SE("Burchakdan Pastga", MotionCategory.SLIDE),
    DIAGONAL_NE_SW("O'ngdan Chapga Qiya", MotionCategory.SLIDE),
    SLIDE_IN_LEFT("Chapdan Kirish", MotionCategory.SLIDE),
    SLIDE_IN_RIGHT("O'ngdan Kirish", MotionCategory.SLIDE),
    SLIDE_IN_BOTTOM("Pastdan Chiqish", MotionCategory.SLIDE),
    SLIDE_IN_TOP("Tepadan Tushish", MotionCategory.SLIDE),

    // 21..30: Rotate & 3D Tilt
    ROTATE_CW_SLOW("O'ngga Aylanish", MotionCategory.ROTATE),
    ROTATE_CCW_SLOW("Chapga Aylanish", MotionCategory.ROTATE),
    SPIN_IN_CW("Aylanib Kirish +", MotionCategory.ROTATE),
    SPIN_IN_CCW("Aylanib Kirish -", MotionCategory.ROTATE),
    SWING_PENDULUM("Mayatnik Tebranish", MotionCategory.ROTATE),
    TILT_3D_LEFT("3D Chapga Og'ish", MotionCategory.ROTATE),
    TILT_3D_RIGHT("3D O'ngga Og'ish", MotionCategory.ROTATE),
    VORTEX_TWIST("Girdob Burilish", MotionCategory.ROTATE),
    ROCK_N_ROLL("Yengil Silkinish", MotionCategory.ROTATE),
    FLOAT_ORBIT("Suzuvchi Orbita", MotionCategory.ROTATE),

    // 31..40: Beat, Fade & Light
    BEAT_PULSE("Ritmda Urish", MotionCategory.BEAT_LIGHT),
    HEARTBEAT_DOUBLE("Qo'sh Yurak Zarbasi", MotionCategory.BEAT_LIGHT),
    FLASH_IN("Chaqnash Kirish", MotionCategory.BEAT_LIGHT),
    STROBE_PULSE("Stroboskop Zarba", MotionCategory.BEAT_LIGHT),
    NEON_GLOW_SWEEP("Neon Nur O'tishi", MotionCategory.BEAT_LIGHT),
    FADE_IN_SOFT("Yumshoq Paydo", MotionCategory.BEAT_LIGHT),
    FADE_OUT_SOFT("Yumshoq So'nish", MotionCategory.BEAT_LIGHT),
    FADE_IN_OUT("Kirish va Chiqish", MotionCategory.BEAT_LIGHT),
    VIGNETTE_FOCUS("Kino Fokus", MotionCategory.BEAT_LIGHT),
    SPOTLIGHT_REVEAL("Projektor Nuri", MotionCategory.BEAT_LIGHT),

    // 41..50: Glitch, Shake & FX
    GLITCH_SHAKE("Glitch Silkinish", MotionCategory.GLITCH_FX),
    CAMERA_SHAKE_HEAVY("Kuchli Titratish", MotionCategory.GLITCH_FX),
    MICRO_HANDHELD("Qo'l Kamerasi", MotionCategory.GLITCH_FX),
    RGB_SPLIT_PULSE("RGB Rang Ajralishi", MotionCategory.GLITCH_FX),
    CURTAIN_HORIZONTAL("Parda Gorizontal", MotionCategory.GLITCH_FX),
    CURTAIN_VERTICAL("Parda Vertikal", MotionCategory.GLITCH_FX),
    IRIS_CIRCLE_IN("Doira Ochilishi", MotionCategory.GLITCH_FX),
    BOUNCE_DROP("Sakrab Tushish", MotionCategory.GLITCH_FX),
    WHIP_ZOOM_BLUR("Tezkor Qamchi Zoom", MotionCategory.GLITCH_FX),
    CINEMA_LETTERBOX("Kino Ramka Kirishi", MotionCategory.GLITCH_FX)
}

enum class FilterPreset(val label: String) {
    ORIGINAL("Asl"),
    WARM_SUNSET("Quyoshli"),
    TEAL_ORANGE("Kino Teal"),
    VIVID_NEON("Neon"),
    NOIR_BW("Oq-Qora"),
    VINTAGE_FILM("Retro")
}

enum class TextAnimationStyle(val label: String) {
    FADE_UP("Pastdan"),
    POP_SCALE("Sakrab"),
    GLOW_PULSE("Yorqin"),
    STATIC("Oddiy")
}

data class ClipItem(
    val id: String = UUID.randomUUID().toString(),
    val slotLabel: String = "Kadr",
    val imagePath: String? = null,
    val sampleDrawableName: String? = null,
    val durationMs: Long = 1600L,
    val transition: TransitionType = TransitionType.CROSSFADE,
    val transitionDurationMs: Long = 450L,
    val motion: MotionEffect = MotionEffect.NONE,
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val zoomScale: Float = 1f
)

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.78f,
    val fontSizeSp: Float = 24f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val hasBackdrop: Boolean = true,
    val animation: TextAnimationStyle = TextAnimationStyle.FADE_UP
)

data class MusicTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val bpm: Int,
    val durationMs: Long,
    val builtInPreset: String? = null,
    val audioFilePath: String? = null,
    val volume: Float = 0.9f,
    val trimStartMs: Long = 0L,
    val fadeInMs: Long = 350L,
    val fadeOutMs: Long = 700L,
    val beatMarksMs: List<Long> = emptyList()
)

data class TemplateSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val coverDrawableName: String,
    val accentColorHex: Long,
    val aspectRatio: AspectRatioOption,
    val bpm: Int,
    val clips: List<ClipItem>,
    val texts: List<TextOverlayItem>,
    val music: MusicTrack
) {
    val slotCount: Int get() = clips.size
    val totalDurationMs: Long get() = clips.sumOf { it.durationMs }
}

data class MontageProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Yangi Montaj",
    val templateId: String? = null,
    val aspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
    val clips: List<ClipItem> = emptyList(),
    val texts: List<TextOverlayItem> = emptyList(),
    val music: MusicTrack? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.durationMs }.coerceAtLeast(1000L)
}

sealed interface LvlLoadingState {
    data object Idle : LvlLoadingState
    data class Loading(
        val fileName: String,
        val stepLabel: String,
        val progress: Float
    ) : LvlLoadingState
    data class Error(val message: String) : LvlLoadingState
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(
        val formatLabel: String,
        val stepLabel: String,
        val progress: Float
    ) : ExportState
    data class Success(
        val formatLabel: String,
        val displayPath: String,
        val shareUriString: String?,
        val mimeType: String
    ) : ExportState
    data class Failed(val message: String) : ExportState
}
