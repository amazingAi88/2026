package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiDubbingService
import com.example.data.DubbingRepository
import com.example.model.DubbingProject
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

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ProjectCreationState {
    data object Idle : ProjectCreationState
    data class Processing(val statusHindi: String, val statusEnglish: String) : ProjectCreationState
    data class Created(val projectId: String, val note: String? = null) : ProjectCreationState
    data class Error(val message: String) : ProjectCreationState
}

class ProjectsViewModel(
    private val repository: DubbingRepository,
    private val dubbingService: GeminiDubbingService,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<UiState<List<DubbingProject>>> = repository
        .observeProjects(currentUserId)
        .map<List<DubbingProject>, UiState<List<DubbingProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load dubbing projects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _creationState = MutableStateFlow<ProjectCreationState>(ProjectCreationState.Idle)
    val creationState: StateFlow<ProjectCreationState> = _creationState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun importProjectFileFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _creationState.value = ProjectCreationState.Processing(
                statusHindi = "प्रोजेक्ट फ़ाइल इम्पोर्ट हो रही है...",
                statusEnglish = "Importing saved SwarDub project file..."
            )
            try {
                val jsonText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.ProjectFileExporter.readStringFromUri(context, uri)
                }
                val (project, lines) = com.example.data.ProjectFileExporter.parseProjectFromJson(jsonText)
                repository.createProjectWithLines(currentUserId, project, lines).fold(
                    onSuccess = { newId ->
                        _creationState.value = ProjectCreationState.Created(
                            newId,
                            "प्रोजेक्ट फ़ाइल सफलतापूर्वक लोड हो गई!"
                        )
                    },
                    onFailure = { err ->
                        _creationState.value = ProjectCreationState.Error(
                            err.localizedMessage ?: "Failed to import project."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _creationState.value = ProjectCreationState.Error(
                    e.localizedMessage ?: "Invalid SwarDub project JSON file."
                )
            }
        }
    }

    fun saveProjectFileToMobileStorage(context: Context, project: com.example.model.DubbingProject) {
        viewModelScope.launch {
            repository.getLinesOnce(currentUserId, project.id).fold(
                onSuccess = { lines ->
                    try {
                        val location = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val json = com.example.data.ProjectFileExporter.serializeProjectToJson(project, lines)
                            val fileName = "${com.example.data.ProjectFileExporter.sanitizedBaseName(project.title)}.swardub.json"
                            com.example.data.ProjectFileExporter.saveBytesToLocalMobileStorage(
                                context = context,
                                fileName = fileName,
                                mimeType = "application/json",
                                bytes = json.toByteArray(Charsets.UTF_8)
                            )
                        }
                        _actionMessage.value = "प्रोजेक्ट फ़ाइल मोबाइल में सेव हुई: $location"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _actionError.value = e.localizedMessage ?: "Failed to save project file."
                    }
                },
                onFailure = { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to load lines for export."
                }
            )
        }
    }

    fun exportProjectFileToUri(context: Context, outputUri: Uri, project: com.example.model.DubbingProject) {
        viewModelScope.launch {
            repository.getLinesOnce(currentUserId, project.id).fold(
                onSuccess = { lines ->
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val json = com.example.data.ProjectFileExporter.serializeProjectToJson(project, lines)
                            com.example.data.ProjectFileExporter.writeStringToUri(context, outputUri, json)
                        }
                        _actionMessage.value = "प्रोजेक्ट फ़ाइल चुनी हुई जगह पर सेव हो गई!"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _actionError.value = e.localizedMessage ?: "Failed to write project file."
                    }
                },
                onFailure = { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to load lines for export."
                }
            )
        }
    }

    fun createFromMediaClip(
        context: Context,
        title: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        durationMs: Long,
        preferredStyle: VoiceStyleConfig,
        autoDetectCharacterVoices: Boolean
    ) {
        viewModelScope.launch {
            _creationState.value = ProjectCreationState.Processing(
                statusHindi = "AI वीडियो/ऑडियो का विश्लेषण और हिन्दी अनुवाद कर रहा है...",
                statusEnglish = "Analyzing original actor dialogue, emotion & translating to Hindi..."
            )
            val aiResult = dubbingService.analyzeAndDubMediaClip(
                context = context,
                uri = uri,
                mimeType = mimeType,
                durationMs = durationMs,
                preferredStyle = preferredStyle,
                autoDetectCharacterVoices = autoDetectCharacterVoices
            )
            aiResult.fold(
                onSuccess = { gen ->
                    val project = DubbingProject(
                        title = title.trim().ifBlank { gen.sceneTitle },
                        videoFileName = fileName,
                        videoUriString = uri.toString(),
                        durationMs = durationMs.coerceAtLeast(
                            gen.lines.maxOfOrNull { it.endMs } ?: 10_000L
                        ),
                        defaultGender = preferredStyle.gender.name,
                        defaultAgeGroup = preferredStyle.ageGroup.name,
                        defaultEffect = preferredStyle.effect.name,
                        defaultPitch = preferredStyle.pitchMultiplier.toDouble(),
                        defaultSpeed = preferredStyle.speedMultiplier.toDouble(),
                        matchOriginalEmotion = preferredStyle.matchOriginalEmotion,
                        originalAudioVolume = 0.15,
                        lineCount = gen.lines.size.toLong()
                    )
                    repository.createProjectWithLines(currentUserId, project, gen.lines).fold(
                        onSuccess = { newId ->
                            _creationState.value = ProjectCreationState.Created(newId, gen.note)
                        },
                        onFailure = { err ->
                            _creationState.value = ProjectCreationState.Error(
                                err.localizedMessage ?: "Failed to save dubbing project."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _creationState.value = ProjectCreationState.Error(
                        err.localizedMessage ?: "AI dubbing generation failed."
                    )
                }
            )
        }
    }

    fun createFromEnglishScript(
        title: String,
        englishScript: String,
        preferredStyle: VoiceStyleConfig,
        autoDetectCharacterVoices: Boolean,
        attachedVideoUri: Uri? = null,
        attachedVideoName: String = "",
        attachedDurationMs: Long = 20_000L
    ) {
        viewModelScope.launch {
            _creationState.value = ProjectCreationState.Processing(
                statusHindi = "AI अंग्रेज़ी संवादों को भावपूर्ण हिन्दी डबिंग में बदल रहा है...",
                statusEnglish = "Translating English script & matching actor emotional tone..."
            )
            val aiResult = dubbingService.dubEnglishScriptToHindi(
                englishScript = englishScript,
                sceneDurationMs = attachedDurationMs,
                preferredStyle = preferredStyle,
                autoDetectCharacterVoices = autoDetectCharacterVoices
            )
            aiResult.fold(
                onSuccess = { gen ->
                    val computedDuration = maxOf(
                        attachedDurationMs,
                        gen.lines.maxOfOrNull { it.endMs } ?: 15_000L
                    )
                    val project = DubbingProject(
                        title = title.trim().ifBlank { gen.sceneTitle },
                        videoFileName = attachedVideoName,
                        videoUriString = attachedVideoUri?.toString().orEmpty(),
                        durationMs = computedDuration,
                        defaultGender = preferredStyle.gender.name,
                        defaultAgeGroup = preferredStyle.ageGroup.name,
                        defaultEffect = preferredStyle.effect.name,
                        defaultPitch = preferredStyle.pitchMultiplier.toDouble(),
                        defaultSpeed = preferredStyle.speedMultiplier.toDouble(),
                        matchOriginalEmotion = preferredStyle.matchOriginalEmotion,
                        originalAudioVolume = 0.15,
                        lineCount = gen.lines.size.toLong()
                    )
                    repository.createProjectWithLines(currentUserId, project, gen.lines).fold(
                        onSuccess = { newId ->
                            _creationState.value = ProjectCreationState.Created(newId, gen.note)
                        },
                        onFailure = { err ->
                            _creationState.value = ProjectCreationState.Error(
                                err.localizedMessage ?: "Failed to save project."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _creationState.value = ProjectCreationState.Error(
                        err.localizedMessage ?: "AI script translation failed."
                    )
                }
            )
        }
    }

    fun resetCreationState() {
        _creationState.value = ProjectCreationState.Idle
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(currentUserId, projectId).onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not delete project."
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "ProjectsVM"
    }
}
