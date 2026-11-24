package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.ClipItem
import com.example.model.FilterPreset
import com.example.model.MontageProject
import com.example.model.MotionEffect
import com.example.model.TextAnimationStyle
import com.example.model.TransitionType
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MontageBitmapCache(private val context: Context) {
    private val cache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

    fun getBitmap(clip: ClipItem, maxDim: Int = 1280): Bitmap? {
        val key = clip.imagePath ?: ("res:" + (clip.sampleDrawableName ?: ""))
        if (key.isBlank() || key == "res:") return null

        cache[key]?.let { if (!it.isRecycled) return it }

        val bmp = if (!clip.imagePath.isNullOrBlank()) {
            decodeFileDownsampled(File(clip.imagePath), maxDim)
        } else {
            val resId = clip.sampleDrawableName?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            } ?: 0
            if (resId != 0) decodeResourceDownsampled(resId, maxDim) else null
        }

        if (bmp != null) {
            if (cache.size >= 14) {
                val eldestKey = cache.keys.firstOrNull()
                if (eldestKey != null) {
                    cache.remove(eldestKey)?.recycle()
                }
            }
            cache[key] = bmp
        }
        return bmp
    }

    private fun decodeFileDownsampled(file: File, maxDim: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun decodeResourceDownsampled(resId: Int, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeResource(context.resources, resId, opts)
    }

    private fun computeInSampleSize(w: Int, h: Int, maxDim: Int): Int {
        var sample = 1
        while (w / sample > maxDim * 2 || h / sample > maxDim * 2) {
            sample *= 2
        }
        return sample
    }

    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }
}

class MontageFrameRenderer(
    private val context: Context,
    private val bitmapCache: MontageBitmapCache
) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0B13") }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val irisPath = Path()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 11, 11, 19)
    }
    private val displayTypeface: Typeface? = try {
        ResourcesCompat.getFont(context, R.font.space_grotesk)
    } catch (_: Exception) {
        Typeface.DEFAULT_BOLD
    }

    fun drawFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        project: MontageProject,
        timeMs: Long,
        maxBitmapDim: Int = 1080
    ) {
        canvas.drawRect(0f, 0f, width, height, bgPaint)
        val clips = project.clips
        if (clips.isEmpty()) {
            drawEmptyPlaceholder(canvas, width, height)
            return
        }

        val totalMs = project.totalDurationMs.coerceAtLeast(1L)
        val clampedTime = timeMs.coerceIn(0L, totalMs)

        var cursor = 0L
        var activeIndex = clips.lastIndex
        var clipStartMs = 0L
        for (i in clips.indices) {
            val dur = clips[i].durationMs.coerceAtLeast(200L)
            if (clampedTime < cursor + dur || i == clips.lastIndex) {
                activeIndex = i
                clipStartMs = cursor
                break
            }
            cursor += dur
        }

        val activeClip = clips[activeIndex]
        val activeLocalMs = (clampedTime - clipStartMs).coerceAtLeast(0L)
        val activeProgress = (activeLocalMs.toFloat() / activeClip.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)

        val nextClip = clips.getOrNull(activeIndex + 1)
        val transDur = activeClip.transitionDurationMs.coerceIn(150L, (activeClip.durationMs * 0.7f).toLong().coerceAtLeast(150L))
        val transStartLocal = (activeClip.durationMs - transDur).coerceAtLeast(0L)
        val inTransition = nextClip != null && activeClip.transition != TransitionType.NONE && activeLocalMs >= transStartLocal

        if (!inTransition) {
            drawClipTransformed(
                canvas = canvas,
                width = width,
                height = height,
                clip = activeClip,
                progress = activeProgress,
                alpha = 1f,
                extraScale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                maxBitmapDim = maxBitmapDim
            )
        } else {
            val t = ((activeLocalMs - transStartLocal).toFloat() / transDur.toFloat()).coerceIn(0f, 1f)
            val eased = easeInOutCubic(t)

            when (activeClip.transition) {
                TransitionType.NONE -> {
                    drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f, 1f, 0f, 0f, maxBitmapDim)
                }
                TransitionType.CROSSFADE -> {
                    drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f - eased, 1f, 0f, 0f, maxBitmapDim)
                    drawClipTransformed(canvas, width, height, nextClip, 0f, eased, 1f, 0f, 0f, maxBitmapDim)
                }
                TransitionType.ZOOM_IN -> {
                    drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f - eased, 1f + eased * 0.45f, 0f, 0f, maxBitmapDim)
                    drawClipTransformed(canvas, width, height, nextClip, 0f, eased, 0.85f + eased * 0.15f, 0f, 0f, maxBitmapDim)
                }
                TransitionType.ZOOM_OUT -> {
                    drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f - eased, 1f - eased * 0.25f, 0f, 0f, maxBitmapDim)
                    drawClipTransformed(canvas, width, height, nextClip, 0f, eased, 1.35f - eased * 0.35f, 0f, 0f, maxBitmapDim)
                }
                TransitionType.SLIDE_LEFT -> {
                    drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f, 1f, -width * eased, 0f, maxBitmapDim)
                    drawClipTransformed(canvas, width, height, nextClip, 0f, 1f, 1f, width * (1f - eased), 0f, maxBitmapDim)
                }
                TransitionType.SLIDE_RIGHT -> {
                    drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f, 1f, width * eased, 0f, maxBitmapDim)
                    drawClipTransformed(canvas, width, height, nextClip, 0f, 1f, 1f, -width * (1f - eased), 0f, maxBitmapDim)
                }
                TransitionType.FLASH_BEAT -> {
                    if (eased < 0.5f) {
                        drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f, 1f + eased * 0.12f, 0f, 0f, maxBitmapDim)
                    } else {
                        drawClipTransformed(canvas, width, height, nextClip, 0f, 1f, 1.08f - (eased - 0.5f) * 0.16f, 0f, 0f, maxBitmapDim)
                    }
                    val flashAlpha = (1f - abs(eased - 0.5f) * 2f).coerceIn(0f, 1f)
                    fxPaint.shader = null
                    fxPaint.color = Color.argb((flashAlpha * 215).toInt(), 255, 255, 255)
                    canvas.drawRect(0f, 0f, width, height, fxPaint)
                }
                TransitionType.GLITCH_SHAKE -> {
                    val shake = sin(eased * PI * 8.0).toFloat() * width * 0.03f * (1f - eased)
                    val shakeY = sin(eased * PI * 11.0).toFloat() * height * 0.015f * (1f - eased)
                    if (eased < 0.55f) {
                        drawClipTransformed(canvas, width, height, activeClip, activeProgress, 1f, 1.04f, shake, shakeY, maxBitmapDim)
                    } else {
                        drawClipTransformed(canvas, width, height, nextClip, 0f, 1f, 1.04f, -shake, -shakeY, maxBitmapDim)
                    }
                    if (eased in 0.25f..0.75f) {
                        fxPaint.shader = null
                        fxPaint.color = Color.argb(65, 0, 229, 255)
                        canvas.drawRect(0f, height * 0.28f, width, height * 0.34f, fxPaint)
                        fxPaint.color = Color.argb(65, 255, 77, 141)
                        canvas.drawRect(0f, height * 0.62f, width, height * 0.67f, fxPaint)
                    }
                }
            }
        }

        project.texts.forEach { item ->
            if (clampedTime in item.startMs..item.endMs && item.text.isNotBlank()) {
                val span = (item.endMs - item.startMs).coerceAtLeast(1L)
                val localProgress = ((clampedTime - item.startMs).toFloat() / span).coerceIn(0f, 1f)
                drawTextOverlay(canvas, width, height, item, localProgress)
            }
        }
    }

    private fun drawClipTransformed(
        canvas: Canvas,
        width: Float,
        height: Float,
        clip: ClipItem,
        progress: Float,
        alpha: Float,
        extraScale: Float,
        offsetX: Float,
        offsetY: Float,
        maxBitmapDim: Int
    ) {
        val bmp = bitmapCache.getBitmap(clip, maxBitmapDim)
        if (bmp == null) {
            val grad = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width, height,
                    Color.parseColor("#1D2240"),
                    Color.parseColor("#0D1528"),
                    Shader.TileMode.CLAMP
                )
                this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            }
            canvas.drawRect(offsetX, offsetY, offsetX + width, offsetY + height, grad)
            return
        }

        var motionScale = 1f
        var motionDx = 0f
        var motionDy = 0f
        var rotationDeg = 0f
        var skewX = 0f
        var skewY = 0f
        var motionAlpha = 1f

        val p = progress.coerceIn(0f, 1f)

        when (clip.motion) {
            MotionEffect.NONE -> Unit
            MotionEffect.KEN_BURNS_IN -> motionScale = 1.0f + p * 0.14f
            MotionEffect.KEN_BURNS_OUT -> motionScale = 1.14f - p * 0.14f
            MotionEffect.FAST_ZOOM_IN -> {
                val e = easeOutCubic((p / 0.45f).coerceIn(0f, 1f))
                motionScale = 0.85f + e * 0.25f
            }
            MotionEffect.FAST_ZOOM_OUT -> {
                val e = easeOutCubic((p / 0.45f).coerceIn(0f, 1f))
                motionScale = 1.32f - e * 0.32f
            }
            MotionEffect.ZOOM_PUNCH -> {
                val e = easeOutBack((p / 0.35f).coerceIn(0f, 1f))
                motionScale = 0.88f + e * 0.18f
            }
            MotionEffect.ELASTIC_ZOOM -> {
                val spring = 1f + sin(p * PI * 4.0).toFloat() * 0.08f * (1f - p)
                motionScale = 1.05f * spring
            }
            MotionEffect.DRAMATIC_PUSH -> motionScale = 1.0f + easeInOutCubic(p) * 0.28f
            MotionEffect.DRAMATIC_PULL -> motionScale = 1.28f - easeInOutCubic(p) * 0.28f
            MotionEffect.BREATHE_ZOOM -> motionScale = 1.04f + sin(p * PI * 2.0).toFloat() * 0.06f

            MotionEffect.PAN_LEFT -> {
                motionScale = 1.15f
                motionDx = (0.5f - p) * width * 0.12f
            }
            MotionEffect.PAN_RIGHT -> {
                motionScale = 1.15f
                motionDx = (p - 0.5f) * width * 0.12f
            }
            MotionEffect.PAN_UP -> {
                motionScale = 1.15f
                motionDy = (0.5f - p) * height * 0.10f
            }
            MotionEffect.PAN_DOWN -> {
                motionScale = 1.15f
                motionDy = (p - 0.5f) * height * 0.10f
            }
            MotionEffect.DIAGONAL_NW_SE -> {
                motionScale = 1.20f
                motionDx = (p - 0.5f) * width * 0.10f
                motionDy = (p - 0.5f) * height * 0.10f
            }
            MotionEffect.DIAGONAL_NE_SW -> {
                motionScale = 1.20f
                motionDx = (0.5f - p) * width * 0.10f
                motionDy = (p - 0.5f) * height * 0.10f
            }
            MotionEffect.SLIDE_IN_LEFT -> {
                val e = easeOutCubic((p / 0.35f).coerceIn(0f, 1f))
                motionDx = -(1f - e) * width
            }
            MotionEffect.SLIDE_IN_RIGHT -> {
                val e = easeOutCubic((p / 0.35f).coerceIn(0f, 1f))
                motionDx = (1f - e) * width
            }
            MotionEffect.SLIDE_IN_BOTTOM -> {
                val e = easeOutCubic((p / 0.35f).coerceIn(0f, 1f))
                motionDy = (1f - e) * height
            }
            MotionEffect.SLIDE_IN_TOP -> {
                val e = easeOutCubic((p / 0.35f).coerceIn(0f, 1f))
                motionDy = -(1f - e) * height
            }

            MotionEffect.ROTATE_CW_SLOW -> {
                motionScale = 1.18f
                rotationDeg = p * 8f
            }
            MotionEffect.ROTATE_CCW_SLOW -> {
                motionScale = 1.18f
                rotationDeg = -p * 8f
            }
            MotionEffect.SPIN_IN_CW -> {
                val e = easeOutCubic((p / 0.42f).coerceIn(0f, 1f))
                motionScale = 0.7f + e * 0.35f
                rotationDeg = (1f - e) * -90f
                motionAlpha = e
            }
            MotionEffect.SPIN_IN_CCW -> {
                val e = easeOutCubic((p / 0.42f).coerceIn(0f, 1f))
                motionScale = 0.7f + e * 0.35f
                rotationDeg = (1f - e) * 90f
                motionAlpha = e
            }
            MotionEffect.SWING_PENDULUM -> {
                motionScale = 1.18f
                rotationDeg = sin(p * PI * 2.0).toFloat() * 6f
            }
            MotionEffect.TILT_3D_LEFT -> {
                motionScale = 1.16f
                skewY = -0.06f * sin(p * PI).toFloat()
                motionDx = -width * 0.03f * sin(p * PI).toFloat()
            }
            MotionEffect.TILT_3D_RIGHT -> {
                motionScale = 1.16f
                skewY = 0.06f * sin(p * PI).toFloat()
                motionDx = width * 0.03f * sin(p * PI).toFloat()
            }
            MotionEffect.VORTEX_TWIST -> {
                val e = sin(p * PI).toFloat()
                motionScale = 1.05f + e * 0.25f
                rotationDeg = e * 14f
                skewX = e * 0.05f
            }
            MotionEffect.ROCK_N_ROLL -> {
                motionScale = 1.14f
                rotationDeg = sin(p * PI * 4.0).toFloat() * 3.5f
            }
            MotionEffect.FLOAT_ORBIT -> {
                motionScale = 1.15f
                motionDx = cos(p * PI * 2.0).toFloat() * width * 0.035f
                motionDy = sin(p * PI * 2.0).toFloat() * height * 0.025f
            }

            MotionEffect.BEAT_PULSE -> {
                val pulse = abs(sin(p * PI * 4.0).toFloat())
                motionScale = 1.02f + pulse * 0.08f
            }
            MotionEffect.HEARTBEAT_DOUBLE -> {
                val beat = (sin(p * PI * 6.0).toFloat()).coerceAtLeast(0f)
                motionScale = 1.02f + beat * 0.09f
            }
            MotionEffect.FLASH_IN -> {
                motionScale = 1.04f - p * 0.04f
            }
            MotionEffect.STROBE_PULSE -> {
                val strobe = abs(sin(p * PI * 6.0).toFloat())
                motionScale = 1.03f + strobe * 0.05f
            }
            MotionEffect.NEON_GLOW_SWEEP -> {
                motionScale = 1.06f
            }
            MotionEffect.FADE_IN_SOFT -> {
                motionAlpha = easeOutCubic((p / 0.45f).coerceIn(0f, 1f))
            }
            MotionEffect.FADE_OUT_SOFT -> {
                motionAlpha = easeOutCubic(((1f - p) / 0.45f).coerceIn(0f, 1f))
            }
            MotionEffect.FADE_IN_OUT -> {
                val fi = (p / 0.28f).coerceIn(0f, 1f)
                val fo = ((1f - p) / 0.28f).coerceIn(0f, 1f)
                motionAlpha = minOf(fi, fo)
            }
            MotionEffect.VIGNETTE_FOCUS -> {
                motionScale = 1.0f + p * 0.10f
            }
            MotionEffect.SPOTLIGHT_REVEAL -> {
                motionScale = 1.08f - p * 0.06f
            }

            MotionEffect.GLITCH_SHAKE -> {
                motionScale = 1.10f
                val burst = if ((p * 10f).toInt() % 3 == 0) 1f else 0.2f
                motionDx = sin(p * PI * 22.0).toFloat() * width * 0.028f * burst
                motionDy = cos(p * PI * 17.0).toFloat() * height * 0.014f * burst
            }
            MotionEffect.CAMERA_SHAKE_HEAVY -> {
                motionScale = 1.14f
                motionDx = sin(p * PI * 16.0).toFloat() * width * 0.03f
                motionDy = cos(p * PI * 13.0).toFloat() * height * 0.025f
                rotationDeg = sin(p * PI * 11.0).toFloat() * 2.5f
            }
            MotionEffect.MICRO_HANDHELD -> {
                motionScale = 1.08f
                motionDx = sin(p * PI * 5.0).toFloat() * width * 0.012f
                motionDy = cos(p * PI * 4.0).toFloat() * height * 0.010f
                rotationDeg = sin(p * PI * 3.0).toFloat() * 1.1f
            }
            MotionEffect.RGB_SPLIT_PULSE -> {
                motionScale = 1.06f + abs(sin(p * PI * 4.0).toFloat()) * 0.04f
            }
            MotionEffect.CURTAIN_HORIZONTAL -> {
                motionScale = 1.06f
            }
            MotionEffect.CURTAIN_VERTICAL -> {
                motionScale = 1.06f
            }
            MotionEffect.IRIS_CIRCLE_IN -> {
                motionScale = 1.10f - p * 0.08f
            }
            MotionEffect.BOUNCE_DROP -> {
                val e = easeOutBounce((p / 0.48f).coerceIn(0f, 1f))
                motionDy = -(1f - e) * height * 0.55f
            }
            MotionEffect.WHIP_ZOOM_BLUR -> {
                val e = easeOutCubic((p / 0.28f).coerceIn(0f, 1f))
                motionScale = 2.0f - e * 0.96f
                motionAlpha = (p / 0.15f).coerceIn(0f, 1f)
            }
            MotionEffect.CINEMA_LETTERBOX -> {
                motionScale = 1.04f + p * 0.08f
            }
        }

        val totalScale = clip.zoomScale.coerceIn(0.8f, 2.5f) * motionScale * extraScale
        val coverScale = maxOf(width / bmp.width.toFloat(), height / bmp.height.toFloat()) * totalScale

        val matrix = Matrix().apply {
            postTranslate(-bmp.width / 2f, -bmp.height / 2f)
            postScale(coverScale, coverScale)
            if (skewX != 0f || skewY != 0f) {
                postSkew(skewX, skewY)
            }
            if (rotationDeg != 0f) {
                postRotate(rotationDeg)
            }
            postTranslate(width / 2f + offsetX + motionDx, height / 2f + offsetY + motionDy)
        }

        val finalAlpha = (alpha * motionAlpha).coerceIn(0f, 1f)

        imagePaint.reset()
        imagePaint.isAntiAlias = true
        imagePaint.isFilterBitmap = true
        imagePaint.alpha = (finalAlpha * 255).toInt()
        imagePaint.colorFilter = buildColorFilter(clip)

        canvas.save()
        canvas.clipRect(0f, 0f, width, height)

        // Optional geometric reveal clipping
        when (clip.motion) {
            MotionEffect.CURTAIN_HORIZONTAL -> {
                val open = easeOutCubic((p / 0.40f).coerceIn(0f, 1f))
                val halfW = (width * 0.5f) * open
                canvas.clipRect(width * 0.5f - halfW, 0f, width * 0.5f + halfW, height)
            }
            MotionEffect.CURTAIN_VERTICAL -> {
                val open = easeOutCubic((p / 0.40f).coerceIn(0f, 1f))
                val halfH = (height * 0.5f) * open
                canvas.clipRect(0f, height * 0.5f - halfH, width, height * 0.5f + halfH)
            }
            MotionEffect.IRIS_CIRCLE_IN -> {
                val open = easeOutCubic((p / 0.45f).coerceIn(0f, 1f))
                val maxR = sqrt(width * width + height * height) * 0.55f
                irisPath.reset()
                irisPath.addCircle(width / 2f, height / 2f, (maxR * open).coerceAtLeast(4f), Path.Direction.CW)
                canvas.clipPath(irisPath)
            }
            else -> Unit
        }

        canvas.drawBitmap(bmp, matrix, imagePaint)

        // Post-draw FX overlays for light/glitch/cinema presets
        when (clip.motion) {
            MotionEffect.FLASH_IN -> {
                if (p < 0.32f) {
                    val a = ((1f - p / 0.32f) * 230 * finalAlpha).toInt().coerceIn(0, 255)
                    fxPaint.shader = null
                    fxPaint.color = Color.argb(a, 255, 255, 255)
                    canvas.drawRect(0f, 0f, width, height, fxPaint)
                }
            }
            MotionEffect.STROBE_PULSE -> {
                val flash = (sin(p * PI * 8.0).toFloat()).coerceAtLeast(0f)
                fxPaint.shader = null
                fxPaint.color = Color.argb((flash * 90 * finalAlpha).toInt(), 255, 255, 255)
                canvas.drawRect(0f, 0f, width, height, fxPaint)
            }
            MotionEffect.NEON_GLOW_SWEEP -> {
                val sweepX = (p * 1.6f - 0.3f) * width
                fxPaint.shader = LinearGradient(
                    sweepX - width * 0.25f, 0f, sweepX + width * 0.25f, height * 0.4f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(105, 0, 229, 255), Color.argb(85, 255, 77, 141), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width, height, fxPaint)
            }
            MotionEffect.VIGNETTE_FOCUS -> {
                val radius = maxOf(width, height) * 0.72f
                fxPaint.shader = RadialGradient(
                    width / 2f, height / 2f, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb(175, 5, 5, 10)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width, height, fxPaint)
            }
            MotionEffect.SPOTLIGHT_REVEAL -> {
                val open = easeOutCubic((p / 0.55f).coerceIn(0f, 1f))
                val radius = (maxOf(width, height) * (0.25f + 0.55f * open)).coerceAtLeast(10f)
                fxPaint.shader = RadialGradient(
                    width / 2f, height / 2f, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb(215, 0, 0, 0)),
                    floatArrayOf(0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width, height, fxPaint)
            }
            MotionEffect.RGB_SPLIT_PULSE -> {
                val shift = abs(sin(p * PI * 4.0).toFloat()) * 14f
                fxPaint.shader = null
                fxPaint.color = Color.argb(55, 0, 229, 255)
                canvas.drawRect(0f, 0f, shift, height, fxPaint)
                fxPaint.color = Color.argb(55, 255, 77, 141)
                canvas.drawRect(width - shift, 0f, width, height, fxPaint)
            }
            MotionEffect.GLITCH_SHAKE -> {
                if ((p * 12f).toInt() % 4 == 1) {
                    fxPaint.shader = null
                    fxPaint.color = Color.argb(70, 0, 229, 255)
                    canvas.drawRect(0f, height * 0.22f, width, height * 0.26f, fxPaint)
                    fxPaint.color = Color.argb(70, 255, 77, 141)
                    canvas.drawRect(0f, height * 0.71f, width, height * 0.75f, fxPaint)
                }
            }
            MotionEffect.CINEMA_LETTERBOX -> {
                val e = easeOutCubic((p / 0.35f).coerceIn(0f, 1f))
                val barH = height * 0.11f * e
                canvas.drawRect(0f, 0f, width, barH, letterboxPaint)
                canvas.drawRect(0f, height - barH, width, height, letterboxPaint)
            }
            else -> Unit
        }
        fxPaint.shader = null
        canvas.restore()
    }

    private fun buildColorFilter(clip: ClipItem): ColorMatrixColorFilter? {
        val cm = ColorMatrix()

        when (clip.filter) {
            FilterPreset.ORIGINAL -> Unit
            FilterPreset.WARM_SUNSET -> {
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.12f, 0f, 0f, 0f, 14f,
                    0f, 1.02f, 0f, 0f, 6f,
                    0f, 0f, 0.86f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.TEAL_ORANGE -> {
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.08f, 0.04f, -0.05f, 0f, 6f,
                    0f, 1.02f, 0.06f, 0f, 2f,
                    -0.06f, 0.08f, 1.10f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.VIVID_NEON -> {
                val sat = ColorMatrix().apply { setSaturation(1.38f) }
                cm.postConcat(sat)
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.08f, 0f, 0f, 0f, 4f,
                    0f, 1.04f, 0f, 0f, 0f,
                    0f, 0f, 1.14f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.NOIR_BW -> {
                val sat = ColorMatrix().apply { setSaturation(0f) }
                cm.postConcat(sat)
                val c = 1.22f
                val t = (-0.5f * c + 0.5f) * 255f
                cm.postConcat(ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.VINTAGE_FILM -> {
                val sat = ColorMatrix().apply { setSaturation(0.75f) }
                cm.postConcat(sat)
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.05f, 0.06f, 0f, 0f, 12f,
                    0f, 0.98f, 0.03f, 0f, 8f,
                    0f, 0f, 0.84f, 0f, 14f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }

        if (clip.saturation != 1f) {
            val sat = ColorMatrix().apply { setSaturation(clip.saturation) }
            cm.postConcat(sat)
        }
        if (clip.contrast != 1f || clip.brightness != 0f) {
            val c = clip.contrast
            val b = clip.brightness * 255f
            val translate = (-0.5f * c + 0.5f) * 255f + b
            cm.postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        return ColorMatrixColorFilter(cm)
    }

    private fun drawTextOverlay(
        canvas: Canvas,
        width: Float,
        height: Float,
        item: com.example.model.TextOverlayItem,
        localProgress: Float
    ) {
        val enterFrac = (localProgress / 0.18f).coerceIn(0f, 1f)
        val exitFrac = ((1f - localProgress) / 0.15f).coerceIn(0f, 1f)
        val alphaFrac = minOf(enterFrac, exitFrac)

        var yShift = 0f
        var scale = 1f
        when (item.animation) {
            TextAnimationStyle.FADE_UP -> {
                yShift = (1f - easeOutCubic(enterFrac)) * height * 0.04f
            }
            TextAnimationStyle.POP_SCALE -> {
                scale = 0.72f + 0.28f * easeOutBack(enterFrac)
            }
            TextAnimationStyle.GLOW_PULSE -> {
                scale = 1f + 0.04f * sin(localProgress * PI * 4.0).toFloat()
            }
            TextAnimationStyle.STATIC -> Unit
        }

        val scaleFactor = width / 360f
        textPaint.typeface = displayTypeface
        textPaint.textSize = item.fontSizeSp * scaleFactor
        textPaint.color = item.colorArgb.toInt()
        textPaint.alpha = (alphaFrac * 255).toInt().coerceIn(0, 255)
        textPaint.setShadowLayer(8f * scaleFactor, 0f, 2f * scaleFactor, Color.argb(180, 0, 0, 0))

        val cx = width * item.xFraction
        val cy = height * item.yFraction + yShift

        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        if (item.hasBackdrop) {
            val textWidth = textPaint.measureText(item.text)
            val fm = textPaint.fontMetrics
            val padH = 18f * scaleFactor
            val padV = 10f * scaleFactor
            val rect = RectF(
                cx - textWidth / 2f - padH,
                cy + fm.ascent - padV,
                cx + textWidth / 2f + padH,
                cy + fm.descent + padV
            )
            textBgPaint.alpha = (alphaFrac * 165).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, 14f * scaleFactor, 14f * scaleFactor, textBgPaint)
        }
        canvas.drawText(item.text, cx, cy, textPaint)
        canvas.restore()
    }

    private fun drawEmptyPlaceholder(canvas: Canvas, width: Float, height: Float) {
        textPaint.typeface = displayTypeface
        textPaint.textSize = (width / 360f) * 18f
        textPaint.color = Color.parseColor("#A0A5C0")
        canvas.drawText("Rasm qo'shing", width / 2f, height / 2f, textPaint)
    }

    private fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - ((-2f * t + 2f).let { it * it * it }) / 2f

    private fun easeOutCubic(t: Float): Float =
        1f - (1f - t).let { it * it * it }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val tm1 = t - 1f
        return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1
    }

    private fun easeOutBounce(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                val t = x - 1.5f / d1
                n1 * t * t + 0.75f
            }
            x < 2.5f / d1 -> {
                val t = x - 2.25f / d1
                n1 * t * t + 0.9375f
            }
            else -> {
                val t = x - 2.625f / d1
                n1 * t * t + 0.984375f
            }
        }
    }
}
