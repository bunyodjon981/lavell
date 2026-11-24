package com.example.io

import com.example.model.AspectRatioOption
import com.example.model.ClipItem
import com.example.model.FilterPreset
import com.example.model.MontageProject
import com.example.model.MotionEffect
import com.example.model.MusicTrack
import com.example.model.TextAnimationStyle
import com.example.model.TextOverlayItem
import com.example.model.TransitionType
import org.json.JSONArray
import org.json.JSONObject

object ProjectJsonCodec {

    fun toJson(
        project: MontageProject,
        clipAssetPaths: Map<String, String> = emptyMap(),
        embeddedAudioRelPath: String? = null
    ): String {
        val root = JSONObject().apply {
            put("format", "lavelle-v1")
            put("id", project.id)
            put("name", project.name)
            put("templateId", project.templateId ?: JSONObject.NULL)
            put("aspectRatio", project.aspectRatio.name)
            put("updatedAt", project.updatedAt)

            val clipsArr = JSONArray()
            project.clips.forEach { clip ->
                val obj = JSONObject().apply {
                    put("id", clip.id)
                    put("slotLabel", clip.slotLabel)
                    put("imagePath", clip.imagePath ?: JSONObject.NULL)
                    put("embeddedAsset", clipAssetPaths[clip.id] ?: JSONObject.NULL)
                    put("sampleDrawableName", clip.sampleDrawableName ?: JSONObject.NULL)
                    put("durationMs", clip.durationMs)
                    put("transition", clip.transition.name)
                    put("transitionDurationMs", clip.transitionDurationMs)
                    put("motion", clip.motion.name)
                    put("filter", clip.filter.name)
                    put("brightness", clip.brightness.toDouble())
                    put("contrast", clip.contrast.toDouble())
                    put("saturation", clip.saturation.toDouble())
                    put("zoomScale", clip.zoomScale.toDouble())
                }
                clipsArr.put(obj)
            }
            put("clips", clipsArr)

            val textsArr = JSONArray()
            project.texts.forEach { txt ->
                val obj = JSONObject().apply {
                    put("id", txt.id)
                    put("text", txt.text)
                    put("startMs", txt.startMs)
                    put("endMs", txt.endMs)
                    put("xFraction", txt.xFraction.toDouble())
                    put("yFraction", txt.yFraction.toDouble())
                    put("fontSizeSp", txt.fontSizeSp.toDouble())
                    put("colorArgb", txt.colorArgb)
                    put("hasBackdrop", txt.hasBackdrop)
                    put("animation", txt.animation.name)
                }
                textsArr.put(obj)
            }
            put("texts", textsArr)

            if (project.music != null) {
                val m = project.music
                val musicObj = JSONObject().apply {
                    put("id", m.id)
                    put("title", m.title)
                    put("artist", m.artist)
                    put("bpm", m.bpm)
                    put("durationMs", m.durationMs)
                    put("builtInPreset", m.builtInPreset ?: JSONObject.NULL)
                    put("audioFilePath", m.audioFilePath ?: JSONObject.NULL)
                    put("embeddedAudio", embeddedAudioRelPath ?: JSONObject.NULL)
                    put("volume", m.volume.toDouble())
                    put("trimStartMs", m.trimStartMs)
                    put("fadeInMs", m.fadeInMs)
                    put("fadeOutMs", m.fadeOutMs)
                    val beatsArr = JSONArray()
                    m.beatMarksMs.forEach { beatsArr.put(it) }
                    put("beatMarksMs", beatsArr)
                }
                put("music", musicObj)
            } else {
                put("music", JSONObject.NULL)
            }
        }
        return root.toString(2)
    }

    fun fromJson(
        jsonString: String,
        overrideProjectId: String? = null,
        resolveEmbeddedAsset: (String) -> String? = { null },
        resolveEmbeddedAudio: (String) -> String? = { null }
    ): MontageProject {
        val root = JSONObject(jsonString)
        val projectId = overrideProjectId ?: root.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
        val name = root.optString("name", "Lavelle Montaj")
        val templateId = root.optStringOrNull("templateId")
        val aspectRatio = enumOrDefault(root.optString("aspectRatio"), AspectRatioOption.PORTRAIT_9_16)
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())

        val clips = mutableListOf<ClipItem>()
        val clipsArr = root.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until clipsArr.length()) {
            val c = clipsArr.getJSONObject(i)
            val embeddedAsset = c.optStringOrNull("embeddedAsset")
            val resolvedAssetPath = embeddedAsset?.let(resolveEmbeddedAsset)
            val storedPath = resolvedAssetPath ?: c.optStringOrNull("imagePath")

            clips.add(
                ClipItem(
                    id = c.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    slotLabel = c.optString("slotLabel", "Kadr ${i + 1}"),
                    imagePath = storedPath,
                    sampleDrawableName = c.optStringOrNull("sampleDrawableName"),
                    durationMs = c.optLong("durationMs", 1600L).coerceIn(400L, 12000L),
                    transition = enumOrDefault(c.optString("transition"), TransitionType.CROSSFADE),
                    transitionDurationMs = c.optLong("transitionDurationMs", 450L).coerceIn(150L, 1500L),
                    motion = enumOrDefault(c.optString("motion"), MotionEffect.KEN_BURNS_IN),
                    filter = enumOrDefault(c.optString("filter"), FilterPreset.ORIGINAL),
                    brightness = c.optDouble("brightness", 0.0).toFloat(),
                    contrast = c.optDouble("contrast", 1.0).toFloat(),
                    saturation = c.optDouble("saturation", 1.0).toFloat(),
                    zoomScale = c.optDouble("zoomScale", 1.0).toFloat()
                )
            )
        }

        val texts = mutableListOf<TextOverlayItem>()
        val textsArr = root.optJSONArray("texts") ?: JSONArray()
        for (i in 0 until textsArr.length()) {
            val t = textsArr.getJSONObject(i)
            texts.add(
                TextOverlayItem(
                    id = t.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    text = t.optString("text", ""),
                    startMs = t.optLong("startMs", 0L),
                    endMs = t.optLong("endMs", 2500L),
                    xFraction = t.optDouble("xFraction", 0.5).toFloat(),
                    yFraction = t.optDouble("yFraction", 0.78).toFloat(),
                    fontSizeSp = t.optDouble("fontSizeSp", 24.0).toFloat(),
                    colorArgb = t.optLong("colorArgb", 0xFFFFFFFFL),
                    hasBackdrop = t.optBoolean("hasBackdrop", true),
                    animation = enumOrDefault(t.optString("animation"), TextAnimationStyle.FADE_UP)
                )
            )
        }

        val musicObj = if (root.isNull("music")) null else root.optJSONObject("music")
        val music = musicObj?.let { m ->
            val embeddedAudio = m.optStringOrNull("embeddedAudio")
            val resolvedAudio = embeddedAudio?.let(resolveEmbeddedAudio) ?: m.optStringOrNull("audioFilePath")
            val beatsArr = m.optJSONArray("beatMarksMs") ?: JSONArray()
            val beats = buildList {
                for (b in 0 until beatsArr.length()) {
                    add(beatsArr.getLong(b))
                }
            }
            MusicTrack(
                id = m.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                title = m.optString("title", "Musiqa"),
                artist = m.optString("artist", "Lavelle"),
                bpm = m.optInt("bpm", 120),
                durationMs = m.optLong("durationMs", 20_000L),
                builtInPreset = m.optStringOrNull("builtInPreset"),
                audioFilePath = resolvedAudio,
                volume = m.optDouble("volume", 0.9).toFloat(),
                trimStartMs = m.optLong("trimStartMs", 0L),
                fadeInMs = m.optLong("fadeInMs", 350L),
                fadeOutMs = m.optLong("fadeOutMs", 700L),
                beatMarksMs = beats
            )
        }

        return MontageProject(
            id = projectId,
            name = name,
            templateId = templateId,
            aspectRatio = aspectRatio,
            clips = clips,
            texts = texts,
            music = music,
            updatedAt = updatedAt
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.takeIf { it.isNotBlank() && it != "null" }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T {
        return try {
            enumValueOf<T>(name)
        } catch (_: Exception) {
            default
        }
    }
}
