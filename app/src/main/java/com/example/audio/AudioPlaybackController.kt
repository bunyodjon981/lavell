package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.model.MusicTrack
import java.io.File

class AudioPlaybackController(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var preparedSourceKey: String? = null
    private var isPrepared = false

    suspend fun prepareIfNeeded(track: MusicTrack?): File? {
        if (track == null) {
            release()
            return null
        }

        val resolvedFile = resolveAudioFile(track) ?: return null
        val key = "${resolvedFile.absolutePath}_${resolvedFile.lastModified()}"
        if (key == preparedSourceKey && mediaPlayer != null && isPrepared) {
            applyVolume(track.volume)
            return resolvedFile
        }

        release()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(resolvedFile.absolutePath)
                isLooping = true
                prepare()
            }
            mediaPlayer = mp
            preparedSourceKey = key
            isPrepared = true
            applyVolume(track.volume)
        } catch (e: Exception) {
            Log.e("AudioPlayback", "Failed to prepare audio: ${e.message}")
            release()
        }
        return resolvedFile
    }

    suspend fun resolveAudioFile(track: MusicTrack): File? {
        track.audioFilePath?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0L) return f
        }
        val preset = track.builtInPreset ?: return null
        return SynthMusicGenerator.ensureBuiltInTrackFile(
            context = context,
            preset = preset,
            bpm = track.bpm,
            durationMs = track.durationMs.coerceAtLeast(15_000L)
        )
    }

    fun startAt(timelineMs: Long, track: MusicTrack?) {
        val mp = mediaPlayer ?: return
        if (!isPrepared || track == null) return
        try {
            val trackDuration = mp.duration.takeIf { it > 0 } ?: return
            val targetMs = ((timelineMs + track.trimStartMs) % trackDuration).toInt().coerceAtLeast(0)
            if (kotlin.math.abs(mp.currentPosition - targetMs) > 180) {
                mp.seekTo(targetMs)
            }
            applyVolume(track.volume)
            if (!mp.isPlaying) {
                mp.start()
            }
        } catch (e: Exception) {
            Log.w("AudioPlayback", "startAt error: ${e.message}")
        }
    }

    fun seekTo(timelineMs: Long, track: MusicTrack?) {
        val mp = mediaPlayer ?: return
        if (!isPrepared || track == null) return
        try {
            val trackDuration = mp.duration.takeIf { it > 0 } ?: return
            val targetMs = ((timelineMs + track.trimStartMs) % trackDuration).toInt().coerceAtLeast(0)
            mp.seekTo(targetMs)
        } catch (_: Exception) {
        }
    }

    fun applyVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        preparedSourceKey = null
        isPrepared = false
    }
}
