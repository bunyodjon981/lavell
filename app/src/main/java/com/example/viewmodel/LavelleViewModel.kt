package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlaybackController
import com.example.data.TemplateCatalog
import com.example.io.LvlPackageManager
import com.example.io.ProjectRepository
import com.example.model.AspectRatioOption
import com.example.model.ClipItem
import com.example.model.ExportState
import com.example.model.FilterPreset
import com.example.model.LvlLoadingState
import com.example.model.MontageProject
import com.example.model.MotionEffect
import com.example.model.MusicTrack
import com.example.model.TemplateSpec
import com.example.model.TextAnimationStyle
import com.example.model.TextOverlayItem
import com.example.model.TransitionType
import com.example.video.VideoExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class LavelleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val lvlManager = LvlPackageManager(application, repository)
    private val videoExporter = VideoExporter(application)
    private val audioController = AudioPlaybackController(application)

    private val _projects = MutableStateFlow<List<MontageProject>>(emptyList())
    val projects: StateFlow<List<MontageProject>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<MontageProject?>(null)
    val activeProject: StateFlow<MontageProject?> = _activeProject.asStateFlow()

    private val _pendingTemplate = MutableStateFlow<TemplateSpec?>(null)
    val pendingTemplate: StateFlow<TemplateSpec?> = _pendingTemplate.asStateFlow()

    private val _pendingSlotImages = MutableStateFlow<List<String?>>(emptyList())
    val pendingSlotImages: StateFlow<List<String?>> = _pendingSlotImages.asStateFlow()

    private val _pendingSlotProjectId = MutableStateFlow(UUID.randomUUID().toString())

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedClipIndex = MutableStateFlow<Int?>(null)
    val selectedClipIndex: StateFlow<Int?> = _selectedClipIndex.asStateFlow()

    private val _lvlLoadingState = MutableStateFlow<LvlLoadingState>(LvlLoadingState.Idle)
    val lvlLoadingState: StateFlow<LvlLoadingState> = _lvlLoadingState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _navigateToEditorSignal = MutableStateFlow(0)
    val navigateToEditorSignal: StateFlow<Int> = _navigateToEditorSignal.asStateFlow()

    private val _navigateToTemplateFillSignal = MutableStateFlow(0)
    val navigateToTemplateFillSignal: StateFlow<Int> = _navigateToTemplateFillSignal.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var playbackJob: Job? = null
    private var saveJob: Job? = null

    init {
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _projects.value = repository.loadAllProjects()
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun startTemplateFlow(template: TemplateSpec) {
        _pendingTemplate.value = template
        _pendingSlotProjectId.value = UUID.randomUUID().toString()
        _pendingSlotImages.value = List(template.slotCount) { null }
        _navigateToTemplateFillSignal.value += 1
    }

    fun fillTemplateSlot(slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            val path = repository.importImageUriToProject(_pendingSlotProjectId.value, uri)
            if (path == null) {
                _toastMessage.value = "Rasmni o'qishda xatolik"
                return@launch
            }
            val current = _pendingSlotImages.value.toMutableList()
            if (slotIndex in current.indices) {
                current[slotIndex] = path
                _pendingSlotImages.value = current
            }
        }
    }

    fun fillTemplateBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val current = _pendingSlotImages.value.toMutableList()
            if (current.isEmpty()) return@launch
            var uriIdx = 0
            for (slotIdx in current.indices) {
                val chosenUri = uris[uriIdx % uris.size]
                val path = repository.importImageUriToProject(_pendingSlotProjectId.value, chosenUri)
                if (path != null) {
                    current[slotIdx] = path
                }
                uriIdx++
            }
            _pendingSlotImages.value = current
        }
    }

    fun launchEditorFromPendingTemplate() {
        val tpl = _pendingTemplate.value ?: return
        val slotPaths = _pendingSlotImages.value
        val projectId = _pendingSlotProjectId.value

        val populatedClips = tpl.clips.mapIndexed { index, clip ->
            val pickedPath = slotPaths.getOrNull(index)
            clip.copy(
                id = UUID.randomUUID().toString(),
                imagePath = pickedPath
            )
        }
        val project = MontageProject(
            id = projectId,
            name = tpl.title,
            templateId = tpl.id,
            aspectRatio = tpl.aspectRatio,
            clips = populatedClips,
            texts = tpl.texts.map { it.copy(id = UUID.randomUUID().toString()) },
            music = tpl.music.copy(id = UUID.randomUUID().toString())
        )
        openProjectInEditor(project)
    }

    fun createBlankProjectFromPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            val clips = if (uris.isEmpty()) {
                listOf(
                    ClipItem(
                        slotLabel = "1 • Kadr",
                        sampleDrawableName = "img_tpl_golden_travel",
                        durationMs = 2000L,
                        transition = TransitionType.NONE,
                        motion = MotionEffect.NONE
                    )
                )
            } else {
                uris.mapIndexedNotNull { idx, uri ->
                    val path = repository.importImageUriToProject(projectId, uri) ?: return@mapIndexedNotNull null
                    ClipItem(
                        slotLabel = "${idx + 1} • Kadr",
                        imagePath = path,
                        durationMs = 2000L,
                        transition = TransitionType.NONE,
                        motion = MotionEffect.NONE,
                        filter = FilterPreset.ORIGINAL
                    )
                }
            }
            val project = MontageProject(
                id = projectId,
                name = "Yangi Montaj",
                aspectRatio = AspectRatioOption.PORTRAIT_9_16,
                clips = clips,
                texts = emptyList(),
                music = null
            )
            openProjectInEditor(project)
        }
    }

    fun openSavedProject(project: MontageProject) {
        openProjectInEditor(project)
    }

    fun deleteSavedProject(project: MontageProject) {
        viewModelScope.launch {
            repository.deleteProject(project.id)
            if (_activeProject.value?.id == project.id) {
                pausePlayback()
                _activeProject.value = null
            }
            refreshProjects()
        }
    }

    private fun openProjectInEditor(project: MontageProject) {
        pausePlayback()
        _activeProject.value = project
        _playheadMs.value = 0L
        _selectedClipIndex.value = null
        viewModelScope.launch {
            repository.saveProject(project)
            audioController.prepareIfNeeded(project.music)
            refreshProjects()
        }
        _navigateToEditorSignal.value += 1
    }

    fun openLvlFromUri(uri: Uri) {
        pausePlayback()
        viewModelScope.launch {
            try {
                val imported = lvlManager.importFromUri(uri) { fileName, step, progress ->
                    _lvlLoadingState.value = LvlLoadingState.Loading(fileName, step, progress)
                }
                _lvlLoadingState.value = LvlLoadingState.Idle
                openProjectInEditor(imported)
                _toastMessage.value = "${imported.name} muvaffaqiyatli ochildi!"
            } catch (e: Exception) {
                _lvlLoadingState.value = LvlLoadingState.Error(
                    e.message ?: "Lavelle (.lvl) faylini ochishda xatolik yuz berdi"
                )
            }
        }
    }

    fun dismissLvlError() {
        _lvlLoadingState.value = LvlLoadingState.Idle
    }

    // --- Playback Controls ---

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val project = _activeProject.value ?: return
        if (project.clips.isEmpty()) return
        val total = project.totalDurationMs.coerceAtLeast(1000L)
        if (_playheadMs.value >= total - 50L) {
            _playheadMs.value = 0L
        }
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            audioController.prepareIfNeeded(project.music)
            audioController.startAt(_playheadMs.value, project.music)
            var lastTick = System.currentTimeMillis()
            while (_isPlaying.value) {
                delay(33L)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick).coerceIn(0L, 120L)
                lastTick = now
                val currentProj = _activeProject.value ?: break
                val maxDur = currentProj.totalDurationMs.coerceAtLeast(1000L)
                val nextMs = _playheadMs.value + delta
                if (nextMs >= maxDur) {
                    _playheadMs.value = 0L
                    audioController.seekTo(0L, currentProj.music)
                    audioController.startAt(0L, currentProj.music)
                } else {
                    _playheadMs.value = nextMs
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        audioController.pause()
    }

    fun seekTo(ms: Long) {
        val project = _activeProject.value ?: return
        val clamped = ms.coerceIn(0L, project.totalDurationMs)
        _playheadMs.value = clamped
        audioController.seekTo(clamped, project.music)
    }

    // --- Editor Mutations ---

    fun selectClip(index: Int?) {
        _selectedClipIndex.value = index
    }

    fun renameProject(newName: String) {
        updateActiveProject { it.copy(name = newName.trim().ifBlank { "Lavelle Montaj" }) }
    }

    fun setAspectRatio(option: AspectRatioOption) {
        updateActiveProject { it.copy(aspectRatio = option) }
    }

    fun addPhotosToProject(uris: List<Uri>) {
        val proj = _activeProject.value ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newClips = uris.mapIndexedNotNull { idx, uri ->
                val path = repository.importImageUriToProject(proj.id, uri) ?: return@mapIndexedNotNull null
                ClipItem(
                    slotLabel = "${proj.clips.size + idx + 1} • Kadr",
                    imagePath = path,
                    durationMs = 2000L,
                    transition = TransitionType.NONE,
                    motion = MotionEffect.NONE
                )
            }
            if (newClips.isNotEmpty()) {
                updateActiveProject { it.copy(clips = it.clips + newClips) }
            }
        }
    }

    fun replaceClipPhoto(clipIndex: Int, uri: Uri) {
        val proj = _activeProject.value ?: return
        if (clipIndex !in proj.clips.indices) return
        viewModelScope.launch {
            val path = repository.importImageUriToProject(proj.id, uri) ?: return@launch
            updateActiveProject { current ->
                val list = current.clips.toMutableList()
                if (clipIndex in list.indices) {
                    list[clipIndex] = list[clipIndex].copy(imagePath = path)
                }
                current.copy(clips = list)
            }
        }
    }

    fun updateClip(clipIndex: Int, transform: (ClipItem) -> ClipItem) {
        updateActiveProject { current ->
            val list = current.clips.toMutableList()
            if (clipIndex in list.indices) {
                list[clipIndex] = transform(list[clipIndex])
            }
            current.copy(clips = list)
        }
    }

    fun applyFilterToAll(filter: FilterPreset) {
        updateActiveProject { current ->
            current.copy(clips = current.clips.map { it.copy(filter = filter) })
        }
    }

    fun applyMotionToAll(motion: MotionEffect) {
        updateActiveProject { current ->
            current.copy(clips = current.clips.map { it.copy(motion = motion) })
        }
    }

    fun moveClip(fromIndex: Int, direction: Int) {
        val toIndex = fromIndex + direction
        updateActiveProject { current ->
            val list = current.clips.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                _selectedClipIndex.value = toIndex
            }
            current.copy(clips = list)
        }
    }

    fun duplicateClip(index: Int) {
        updateActiveProject { current ->
            val list = current.clips.toMutableList()
            if (index in list.indices) {
                val copy = list[index].copy(id = UUID.randomUUID().toString())
                list.add(index + 1, copy)
                _selectedClipIndex.value = index + 1
            }
            current.copy(clips = list)
        }
    }

    fun deleteClip(index: Int) {
        updateActiveProject { current ->
            if (current.clips.size <= 1) {
                _toastMessage.value = "Kamida 1 ta kadr qolishi kerak"
                return@updateActiveProject current
            }
            val list = current.clips.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
                _selectedClipIndex.value = null
            }
            current.copy(clips = list)
        }
    }

    fun addTextOverlay(text: String, animation: TextAnimationStyle, colorArgb: Long) {
        val proj = _activeProject.value ?: return
        val start = _playheadMs.value.coerceIn(0L, (proj.totalDurationMs - 500L).coerceAtLeast(0L))
        val end = (start + 2400L).coerceAtMost(proj.totalDurationMs)
        val item = TextOverlayItem(
            text = text.trim(),
            startMs = start,
            endMs = end.coerceAtLeast(start + 600L),
            colorArgb = colorArgb,
            animation = animation
        )
        updateActiveProject { it.copy(texts = it.texts + item) }
    }

    fun updateTextOverlay(item: TextOverlayItem) {
        updateActiveProject { current ->
            current.copy(texts = current.texts.map { if (it.id == item.id) item else it })
        }
    }

    fun deleteTextOverlay(id: String) {
        updateActiveProject { current ->
            current.copy(texts = current.texts.filterNot { it.id == id })
        }
    }

    fun selectBuiltInMusic(track: MusicTrack) {
        val copy = track.copy(id = UUID.randomUUID().toString())
        updateActiveProject { it.copy(music = copy) }
        viewModelScope.launch {
            audioController.prepareIfNeeded(copy)
            if (_isPlaying.value) {
                audioController.startAt(_playheadMs.value, copy)
            }
        }
    }

    fun importCustomMusic(uri: Uri) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val info = repository.importAudioUriToProject(proj.id, uri)
            if (info == null) {
                _toastMessage.value = "Musiqa faylini yuklab bo'lmadi"
                return@launch
            }
            val track = MusicTrack(
                title = info.title,
                artist = "Telefon xotirasidan",
                bpm = 110,
                durationMs = info.durationMs,
                audioFilePath = info.filePath,
                beatMarksMs = emptyList()
            )
            updateActiveProject { it.copy(music = track) }
            audioController.prepareIfNeeded(track)
            if (_isPlaying.value) {
                audioController.startAt(_playheadMs.value, track)
            }
            _toastMessage.value = "\"${info.title}\" musiqasi qo'shildi!"
        }
    }

    fun updateMusicSettings(volume: Float, fadeInMs: Long, fadeOutMs: Long, trimStartMs: Long) {
        updateActiveProject { current ->
            val m = current.music ?: return@updateActiveProject current
            val updated = m.copy(
                volume = volume.coerceIn(0f, 1f),
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs,
                trimStartMs = trimStartMs
            )
            audioController.applyVolume(updated.volume)
            current.copy(music = updated)
        }
    }

    fun removeMusic() {
        updateActiveProject { it.copy(music = null) }
        audioController.release()
    }

    // --- Export MP4 & .lvl ---

    fun exportMp4(fileName: String) {
        val project = _activeProject.value ?: return
        pausePlayback()
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Exporting("Video (MP4)", "Tayyorlanmoqda…", 0.02f)
                val result = videoExporter.exportToMp4(project, fileName) { step, progress ->
                    _exportState.value = ExportState.Exporting("Video (MP4)", step, progress)
                }
                _exportState.value = ExportState.Success(
                    formatLabel = "MP4 Video",
                    displayPath = result.displayPath,
                    shareUriString = result.shareUriString,
                    mimeType = "video/mp4"
                )
            } catch (e: Exception) {
                _exportState.value = ExportState.Failed(
                    e.message ?: "Video eksport qilishda xatolik yuz berdi"
                )
            }
        }
    }

    fun exportLvl(fileName: String) {
        val project = _activeProject.value ?: return
        pausePlayback()
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Exporting("Lavelle (.lvl)", "Tayyorlanmoqda…", 0.05f)
                val result = lvlManager.exportToDownloads(project, fileName) { step, progress ->
                    _exportState.value = ExportState.Exporting("Lavelle (.lvl)", step, progress)
                }
                _exportState.value = ExportState.Success(
                    formatLabel = ".lvl Loyiha Fayli",
                    displayPath = result.displayPath,
                    shareUriString = result.shareUriString,
                    mimeType = "application/x-lavelle"
                )
            } catch (e: Exception) {
                _exportState.value = ExportState.Failed(
                    e.message ?: ".lvl faylini eksport qilishda xatolik yuz berdi"
                )
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    private fun updateActiveProject(transform: (MontageProject) -> MontageProject) {
        val current = _activeProject.value ?: return
        val updated = transform(current)
        _activeProject.value = updated
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350L)
            repository.saveProject(updated)
            refreshProjects()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        audioController.release()
    }
}
