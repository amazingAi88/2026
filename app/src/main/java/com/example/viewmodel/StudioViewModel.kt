package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiDubbingService
import com.example.audio.DubAudioPlaybackState
import com.example.audio.VoiceChangerAudioEngine
import com.example.data.DubbingRepository
import com.example.model.DubLine
import com.example.model.DubbingProject
import com.example.model.OriginalEmotion
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceGender
import com.example.model.VoiceStyleConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudioActionStatus(
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class FinalDubCompletionResult(
    val projectTitle: String,
    val savedLocation: String,
    val lineCount: Int,
    val finalizedLines: List<DubLine>
)

class StudioViewModel(
    private val repository: DubbingRepository,
    private val dubbingService: GeminiDubbingService,
    private val audioEngine: VoiceChangerAudioEngine,
    private val currentUserId: String,
    private val projectId: String
) : ViewModel() {

    val projectState: StateFlow<UiState<DubbingProject?>> = repository
        .observeProject(currentUserId, projectId)
        .map<DubbingProject?, UiState<DubbingProject?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing project $projectId", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load project."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val linesState: StateFlow<UiState<List<DubLine>>> = repository
        .observeLines(currentUserId, projectId)
        .map<List<DubLine>, UiState<List<DubLine>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing lines for $projectId", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load dialogue lines."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val audioState: StateFlow<DubAudioPlaybackState> = audioEngine.state

    private val _status = MutableStateFlow(StudioActionStatus())
    val status: StateFlow<StudioActionStatus> = _status.asStateFlow()

    private val _activeSubtitleLine = MutableStateFlow<DubLine?>(null)
    val activeSubtitleLine: StateFlow<DubLine?> = _activeSubtitleLine.asStateFlow()

    private val _finalDubResult = MutableStateFlow<FinalDubCompletionResult?>(null)
    val finalDubResult: StateFlow<FinalDubCompletionResult?> = _finalDubResult.asStateFlow()

    fun previewLine(line: DubLine) {
        viewModelScope.launch {
            _activeSubtitleLine.value = line
            audioEngine.previewLine(line).onFailure { err ->
                _status.value = StudioActionStatus(
                    error = err.localizedMessage ?: "Could not preview dubbed voice."
                )
            }
        }
    }

    fun previewFullDubAudio(lines: List<DubLine>) {
        viewModelScope.launch {
            audioEngine.previewFullDubAudio(viewModelScope, lines).onFailure { err ->
                _status.value = StudioActionStatus(
                    error = err.localizedMessage ?: "Dub audio preview failed."
                )
            }
        }
    }

    fun previewStyleAudition(style: VoiceStyleConfig, lines: List<DubLine>) {
        val baseLine = lines.firstOrNull() ?: DubLine(
            id = "audition_sample",
            speakerName = "Demo",
            hindiText = "नमस्ते! यह आपकी चुनी हुई हिन्दी डबिंग आवाज़ का प्रीव्यू है।",
            emotion = OriginalEmotion.EXCITED.name,
            emotionIntensity = 0.8
        )
        val styledSample = baseLine.copy(
            id = "audition_${baseLine.id}",
            voiceGender = style.gender.name,
            ageGroup = style.ageGroup.name,
            voiceEffect = style.effect.name,
            pitchMultiplier = style.pitchMultiplier.toDouble(),
            speedMultiplier = style.speedMultiplier.toDouble(),
            matchOriginalEmotion = style.matchOriginalEmotion
        )
        previewLine(styledSample)
    }

    fun toggleFullPreviewPause() {
        audioEngine.toggleFullPreviewPause()
    }

    fun seekFullPreview(positionMs: Long) {
        audioEngine.seekFullPreview(positionMs)
    }

    fun startSyncedScenePlayback(
        lines: List<DubLine>,
        getVideoPositionMs: () -> Long,
        isVideoPlaying: () -> Boolean
    ) {
        audioEngine.startSceneSyncPlayback(
            scope = viewModelScope,
            lines = lines,
            getVideoPositionMs = getVideoPositionMs,
            isVideoPlaying = isVideoPlaying,
            onLineTriggered = { triggered ->
                _activeSubtitleLine.value = triggered
            }
        )
    }

    fun stopAudio() {
        audioEngine.stopAll()
    }

    fun updateGlobalVoiceStyle(
        style: VoiceStyleConfig,
        originalAudioVolume: Double,
        applyToAllLines: Boolean,
        currentLines: List<DubLine>
    ) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            repository.updateProjectVoiceSettings(
                userId = currentUserId,
                projectId = projectId,
                style = style,
                originalAudioVolume = originalAudioVolume,
                applyToAllLines = applyToAllLines,
                existingLines = currentLines
            ).fold(
                onSuccess = {
                    _status.value = StudioActionStatus(
                        message = if (applyToAllLines) {
                            "आवाज़ स्टाइल सभी संवादों पर लागू किया गया (Applied to all lines)"
                        } else {
                            "प्रोजेक्ट आवाज़ स्टाइल सेव किया गया (Voice style saved)"
                        }
                    )
                },
                onFailure = { err ->
                    _status.value = StudioActionStatus(
                        error = err.localizedMessage ?: "Failed to update voice settings."
                    )
                }
            )
        }
    }

    fun updateLine(line: DubLine) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            repository.updateLine(currentUserId, projectId, line).fold(
                onSuccess = {
                    _status.value = StudioActionStatus(
                        message = "संवाद व आवाज़ अपडेट की गई (Line updated)"
                    )
                },
                onFailure = { err ->
                    _status.value = StudioActionStatus(
                        error = err.localizedMessage ?: "Failed to update line."
                    )
                }
            )
        }
    }

    fun saveHindiTextEdits(
        edits: Map<String, String>,
        onSaved: () -> Unit = {}
    ) {
        if (edits.isEmpty()) return
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            repository.saveBatchHindiTextEdits(currentUserId, projectId, edits).fold(
                onSuccess = { count ->
                    _status.value = StudioActionStatus(
                        message = "हिन्दी शब्दों के बदलाव सेव हो गए! ($count संवाद सेव)"
                    )
                    onSaved()
                },
                onFailure = { err ->
                    _status.value = StudioActionStatus(
                        error = err.localizedMessage ?: "Failed to save edited Hindi words."
                    )
                }
            )
        }
    }

    fun completeFinalHindiDubbing(
        context: Context,
        project: DubbingProject,
        lines: List<DubLine>,
        pendingEdits: Map<String, String>,
        onEditsSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (lines.isEmpty()) {
                _status.value = StudioActionStatus(error = "फाइनल डबिंग के लिए कम से कम एक संवाद होना ज़रूरी है।")
                return@launch
            }
            _status.value = StudioActionStatus(isBusy = true)
            try {
                if (pendingEdits.isNotEmpty()) {
                    repository.saveBatchHindiTextEdits(currentUserId, projectId, pendingEdits).getOrThrow()
                    onEditsSaved()
                }
                val finalizedLines = lines.map { line ->
                    val edited = pendingEdits[line.id]
                    if (edited != null) {
                        line.copy(hindiText = edited.trim().take(1000).ifBlank { "..." })
                    } else {
                        line
                    }
                }
                val wavBytes = audioEngine.buildCombinedDubWavBytes(finalizedLines)
                val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val fileName = "${com.example.data.ProjectFileExporter.sanitizedBaseName(project.title)}_hindi_dub_final.wav"
                    com.example.data.ProjectFileExporter.saveBytesToLocalMobileStorage(
                        context = context,
                        fileName = fileName,
                        mimeType = "audio/wav",
                        bytes = wavBytes
                    )
                }
                _status.value = StudioActionStatus(
                    message = "फाइनल हिन्दी ऑडियो डबिंग पूरी हो गई!"
                )
                _finalDubResult.value = FinalDubCompletionResult(
                    projectTitle = project.title,
                    savedLocation = savedPath,
                    lineCount = finalizedLines.size,
                    finalizedLines = finalizedLines
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = StudioActionStatus(
                    error = e.localizedMessage ?: "फाइनल हिन्दी डबिंग पूरी करने में त्रुटि हुई।"
                )
            }
        }
    }

    fun dismissFinalDubResult() {
        _finalDubResult.value = null
    }

    fun reDubAttachedMedia(
        context: Context,
        project: DubbingProject,
        preferredStyle: VoiceStyleConfig,
        autoDetectCharacterVoices: Boolean
    ) {
        val uriString = project.videoUriString
        if (uriString.isBlank()) {
            _status.value = StudioActionStatus(error = "पहले गैलरी या फ़ाइल मैनेजर से कोई वीडियो जोड़ें।")
            return
        }
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            val uri = Uri.parse(uriString)
            val info = com.example.media.inspectMediaUri(context, uri, fallbackMime = "video/mp4")
            dubbingService.analyzeAndDubMediaClip(
                context = context,
                uri = uri,
                mimeType = info.mimeType,
                durationMs = info.durationMs.takeIf { it > 0L } ?: project.durationMs,
                preferredStyle = preferredStyle,
                autoDetectCharacterVoices = autoDetectCharacterVoices
            ).fold(
                onSuccess = { gen ->
                    repository.replaceProjectLines(currentUserId, projectId, gen.lines).fold(
                        onSuccess = {
                            _status.value = StudioActionStatus(
                                message = gen.note ?: "वीडियो से ${gen.lines.size} हिन्दी संवाद फिर से तैयार हो गए!"
                            )
                        },
                        onFailure = { err ->
                            _status.value = StudioActionStatus(
                                error = err.localizedMessage ?: "Failed to save re-dubbed lines."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _status.value = StudioActionStatus(
                        error = err.localizedMessage ?: "AI हिन्दी डबिंग असफल रही।"
                    )
                }
            )
        }
    }

    fun reAdaptLineWithAi(
        line: DubLine,
        targetGender: VoiceGender,
        targetAgeGroup: VoiceAgeGroup,
        targetEmotion: OriginalEmotion,
        onAdaptedText: (String) -> Unit
    ) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            dubbingService.reAdaptLineForVoiceStyle(
                englishText = line.englishText,
                currentHindi = line.hindiText,
                targetGender = targetGender,
                targetAgeGroup = targetAgeGroup,
                targetEmotion = targetEmotion
            ).fold(
                onSuccess = { adapted ->
                    _status.value = StudioActionStatus(
                        message = "AI ने चयनित स्टाइल के अनुसार संवाद ढाला"
                    )
                    onAdaptedText(adapted)
                },
                onFailure = { err ->
                    _status.value = StudioActionStatus(
                        error = err.localizedMessage ?: "AI re-adaptation failed."
                    )
                }
            )
        }
    }

    fun attachVideoAndOptionallyAnalyze(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        durationMs: Long,
        reAnalyzeLines: Boolean,
        preferredStyle: VoiceStyleConfig,
        autoDetectCharacterVoices: Boolean
    ) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            repository.updateProjectMedia(
                userId = currentUserId,
                projectId = projectId,
                videoFileName = fileName,
                videoUriString = uri.toString(),
                durationMs = durationMs
            ).onFailure { err ->
                _status.value = StudioActionStatus(
                    error = err.localizedMessage ?: "Failed to attach video."
                )
                return@launch
            }

            if (reAnalyzeLines) {
                dubbingService.analyzeAndDubMediaClip(
                    context = context,
                    uri = uri,
                    mimeType = mimeType,
                    durationMs = durationMs,
                    preferredStyle = preferredStyle,
                    autoDetectCharacterVoices = autoDetectCharacterVoices
                ).fold(
                    onSuccess = { gen ->
                        repository.replaceProjectLines(currentUserId, projectId, gen.lines).fold(
                            onSuccess = {
                                _status.value = StudioActionStatus(
                                    message = gen.note ?: "AI ने क्लिप से ${gen.lines.size} हिन्दी संवाद बनाए"
                                )
                            },
                            onFailure = { err ->
                                _status.value = StudioActionStatus(
                                    error = err.localizedMessage ?: "Failed to save AI lines."
                                )
                            }
                        )
                    },
                    onFailure = { err ->
                        _status.value = StudioActionStatus(
                            error = err.localizedMessage ?: "AI analysis failed."
                        )
                    }
                )
            } else {
                _status.value = StudioActionStatus(message = "वीडियो क्लिप जोड़ी गई (Video attached)")
            }
        }
    }

    fun addManualLine(
        speakerName: String,
        englishText: String,
        hindiText: String,
        emotion: OriginalEmotion,
        style: VoiceStyleConfig,
        currentLines: List<DubLine>
    ) {
        viewModelScope.launch {
            val lastEnd = currentLines.maxOfOrNull { it.endMs } ?: 0L
            val newLine = DubLine(
                orderIndex = currentLines.size.toLong(),
                speakerName = speakerName.trim().ifBlank { "Speaker" },
                startMs = lastEnd + 300L,
                endMs = lastEnd + 3500L,
                englishText = englishText.trim(),
                hindiText = hindiText.trim().ifBlank { "..." },
                emotion = emotion.name,
                emotionIntensity = 0.75,
                voiceGender = style.gender.name,
                ageGroup = style.ageGroup.name,
                voiceEffect = style.effect.name,
                pitchMultiplier = style.pitchMultiplier.toDouble(),
                speedMultiplier = style.speedMultiplier.toDouble(),
                matchOriginalEmotion = style.matchOriginalEmotion
            )
            repository.addLine(
                userId = currentUserId,
                projectId = projectId,
                line = newLine,
                newTotalCount = (currentLines.size + 1).toLong()
            ).onFailure { err ->
                _status.value = StudioActionStatus(
                    error = err.localizedMessage ?: "Could not add dialogue line."
                )
            }
        }
    }

    fun deleteLine(lineId: String, currentTotalCount: Int) {
        viewModelScope.launch {
            repository.deleteLine(
                userId = currentUserId,
                projectId = projectId,
                lineId = lineId,
                newTotalCount = (currentTotalCount - 1).coerceAtLeast(0).toLong()
            ).onFailure { err ->
                _status.value = StudioActionStatus(
                    error = err.localizedMessage ?: "Could not delete line."
                )
            }
        }
    }

    fun exportDubbedWav(context: Context, outputUri: Uri, lines: List<DubLine>) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            audioEngine.exportCombinedDubWav(context, outputUri, lines).fold(
                onSuccess = {
                    _status.value = StudioActionStatus(
                        message = "हिन्दी डब ऑडियो WAV सेव हो गया (Dubbed audio exported)"
                    )
                },
                onFailure = { err ->
                    _status.value = StudioActionStatus(
                        error = err.localizedMessage ?: "Export failed."
                    )
                }
            )
        }
    }

    fun saveDubbedWavToMobileStorage(context: Context, project: DubbingProject, lines: List<DubLine>) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            try {
                val wavBytes = audioEngine.buildCombinedDubWavBytes(lines)
                val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val fileName = "${com.example.data.ProjectFileExporter.sanitizedBaseName(project.title)}_hindi_dub.wav"
                    com.example.data.ProjectFileExporter.saveBytesToLocalMobileStorage(
                        context = context,
                        fileName = fileName,
                        mimeType = "audio/wav",
                        bytes = wavBytes
                    )
                }
                _status.value = StudioActionStatus(
                    message = "डब ऑडियो मोबाइल स्टोरेज में सेव हुआ: $savedPath"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = StudioActionStatus(
                    error = e.localizedMessage ?: "Failed to save dubbed audio to storage."
                )
            }
        }
    }

    fun exportProjectFileToUri(context: Context, outputUri: Uri, project: DubbingProject, lines: List<DubLine>) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = com.example.data.ProjectFileExporter.serializeProjectToJson(project, lines)
                    com.example.data.ProjectFileExporter.writeStringToUri(context, outputUri, json)
                }
                _status.value = StudioActionStatus(
                    message = "प्रोजेक्ट फ़ाइल (.swardub.json) मोबाइल स्टोरेज में सेव हो गई!"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = StudioActionStatus(
                    error = e.localizedMessage ?: "Failed to export project file."
                )
            }
        }
    }

    fun saveProjectFileToMobileStorage(context: Context, project: DubbingProject, lines: List<DubLine>) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            try {
                val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = com.example.data.ProjectFileExporter.serializeProjectToJson(project, lines)
                    val fileName = "${com.example.data.ProjectFileExporter.sanitizedBaseName(project.title)}.swardub.json"
                    com.example.data.ProjectFileExporter.saveBytesToLocalMobileStorage(
                        context = context,
                        fileName = fileName,
                        mimeType = "application/json",
                        bytes = json.toByteArray(Charsets.UTF_8)
                    )
                }
                _status.value = StudioActionStatus(
                    message = "प्रोजेक्ट फ़ाइल सेव हुई: $savedPath"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = StudioActionStatus(
                    error = e.localizedMessage ?: "Failed to save project file to storage."
                )
            }
        }
    }

    fun saveSubtitlesSrtToMobileStorage(context: Context, project: DubbingProject, lines: List<DubLine>) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            try {
                val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val srt = com.example.data.ProjectFileExporter.generateHindiSrt(lines)
                    val fileName = "${com.example.data.ProjectFileExporter.sanitizedBaseName(project.title)}_hindi.srt"
                    com.example.data.ProjectFileExporter.saveBytesToLocalMobileStorage(
                        context = context,
                        fileName = fileName,
                        mimeType = "application/x-subrip",
                        bytes = srt.toByteArray(Charsets.UTF_8)
                    )
                }
                _status.value = StudioActionStatus(
                    message = "हिन्दी सबटाइटल (.srt) सेव हुए: $savedPath"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = StudioActionStatus(
                    error = e.localizedMessage ?: "Failed to save SRT subtitles."
                )
            }
        }
    }

    fun exportSubtitlesSrtToUri(context: Context, outputUri: Uri, lines: List<DubLine>) {
        viewModelScope.launch {
            _status.value = StudioActionStatus(isBusy = true)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val srt = com.example.data.ProjectFileExporter.generateHindiSrt(lines)
                    com.example.data.ProjectFileExporter.writeStringToUri(context, outputUri, srt)
                }
                _status.value = StudioActionStatus(
                    message = "हिन्दी सबटाइटल (.srt) सेव हो गए!"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = StudioActionStatus(
                    error = e.localizedMessage ?: "Failed to export SRT subtitles."
                )
            }
        }
    }

    fun clearStatus() {
        _status.value = StudioActionStatus()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }

    private companion object {
        const val TAG = "StudioVM"
    }
}
