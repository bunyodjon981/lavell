package com.example.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.MontageProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun getProjectDir(projectId: String): File {
        return File(projectsRoot, projectId).apply { mkdirs() }
    }

    suspend fun saveProject(project: MontageProject): MontageProject = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val dir = getProjectDir(updated.id)
        val file = File(dir, "project.json")
        file.writeText(ProjectJsonCodec.toJson(updated))
        updated
    }

    suspend fun loadAllProjects(): List<MontageProject> = withContext(Dispatchers.IO) {
        val dirs = projectsRoot.listFiles()?.filter { it.isDirectory } ?: return@withContext emptyList()
        dirs.mapNotNull { dir ->
            val file = File(dir, "project.json")
            if (!file.exists()) return@mapNotNull null
            try {
                ProjectJsonCodec.fromJson(file.readText())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        File(projectsRoot, projectId).deleteRecursively()
    }

    suspend fun importImageUriToProject(projectId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val mediaDir = File(getProjectDir(projectId), "media").apply { mkdirs() }
        val targetFile = File(mediaDir, "img_${UUID.randomUUID().toString().take(8)}.jpg")
        try {
            val bitmap = decodeDownsampledBitmap(uri, maxDimension = 1440) ?: return@withContext null
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun importAudioUriToProject(projectId: String, uri: Uri): ImportedAudioInfo? = withContext(Dispatchers.IO) {
        val audioDir = File(getProjectDir(projectId), "audio").apply { mkdirs() }
        val displayName = queryDisplayName(uri) ?: "Musiqa.mp3"
        val ext = displayName.substringAfterLast('.', "mp3").lowercase().take(5)
        val targetFile = File(audioDir, "track_${UUID.randomUUID().toString().take(8)}.$ext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val durationMs = readAudioDurationMs(targetFile).coerceAtLeast(5000L)
            val cleanTitle = displayName.substringBeforeLast('.')
            ImportedAudioInfo(
                filePath = targetFile.absolutePath,
                title = cleanTitle.ifBlank { "Musiqa" },
                durationMs = durationMs
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeDownsampledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (origW / sampleSize > maxDimension * 2 || origH / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= maxDimension) return decoded
        val scale = maxDimension.toFloat() / maxSide
        val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readAudioDurationMs(file: File): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 20_000L
        } catch (_: Exception) {
            20_000L
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
    }

    data class ImportedAudioInfo(
        val filePath: String,
        val title: String,
        val durationMs: Long
    )
}
