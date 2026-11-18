package com.example.ui.studio

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.DubAudioPlaybackState
import com.example.data.ProjectFileExporter
import com.example.media.MediaMetaInfo
import com.example.media.inspectMediaUri
import com.example.media.persistReadPermission
import com.example.model.DubLine
import com.example.model.DubbingProject
import com.example.model.OriginalEmotion
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceEffect
import com.example.model.VoiceGender
import com.example.model.VoiceStyleConfig
import com.example.ui.components.EmotionBadge
import com.example.ui.components.LineVoiceEditorDialog
import com.example.ui.components.VoiceStyleConfigurator
import com.example.viewmodel.FinalDubCompletionResult
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.UiState
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val projectState by viewModel.projectState.collectAsStateWithLifecycle()
    val linesState by viewModel.linesState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val activeSubtitleLine by viewModel.activeSubtitleLine.collectAsStateWithLifecycle()
    val finalDubResult by viewModel.finalDubResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingLine by remember { mutableStateOf<DubLine?>(null) }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Video player state
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var videoPositionMs by remember { mutableLongStateOf(0L) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var videoError by remember { mutableStateOf<String?>(null) }

    val project = (projectState as? UiState.Success)?.data
    val lines = (linesState as? UiState.Success)?.data.orEmpty()

    // Inline Hindi word editing & batch save state
    var draftHindiEdits by remember(project?.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedEditLineIds by remember(project?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var editAllWordsMode by remember(project?.id) { mutableStateOf(false) }

    val effectiveLines = remember(lines, draftHindiEdits) {
        if (draftHindiEdits.isEmpty()) {
            lines
        } else {
            lines.map { line ->
                val edited = draftHindiEdits[line.id]
                if (edited != null) line.copy(hindiText = edited) else line
            }
        }
    }

    val attachedVideoMeta = remember(project?.videoUriString) {
        project?.videoUriString?.takeIf { it.isNotBlank() }?.let { uriStr ->
            runCatching {
                inspectMediaUri(context, Uri.parse(uriStr), fallbackMime = "video/mp4")
            }.getOrNull()
        }
    }

    var workingStyle by remember(project?.id) {
        mutableStateOf(project?.toVoiceStyleConfig() ?: VoiceStyleConfig())
    }
    var originalVolume by remember(project?.id) {
        mutableFloatStateOf(project?.originalAudioVolume?.toFloat() ?: 0.15f)
    }

    LaunchedEffect(project?.defaultGender, project?.defaultAgeGroup, project?.defaultEffect, project?.matchOriginalEmotion) {
        project?.let {
            workingStyle = it.toVoiceStyleConfig()
            originalVolume = it.originalAudioVolume.toFloat()
        }
    }

    // Apply original audio ducking dynamically
    LaunchedEffect(originalVolume, mediaPlayerRef) {
        runCatching {
            mediaPlayerRef?.setVolume(originalVolume, originalVolume)
        }
    }

    // Track video position continuously
    LaunchedEffect(isVideoPlaying, mediaPlayerRef) {
        while (isVideoPlaying) {
            runCatching {
                mediaPlayerRef?.let { mp ->
                    if (mp.isPlaying) {
                        videoPositionMs = mp.currentPosition.toLong()
                    } else {
                        isVideoPlaying = false
                    }
                }
            }
            delay(120L)
        }
    }

    val videoFileMimeTypes = remember {
        arrayOf(
            "video/*",
            "application/x-matroska",
            "application/mp4",
            "application/octet-stream",
            "*/*"
        )
    }

    val videoAttachLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            videoError = null
            val info = inspectMediaUri(context, uri, fallbackMime = "video/mp4")
            viewModel.attachVideoAndOptionallyAnalyze(
                context = context,
                uri = uri,
                fileName = info.fileName,
                mimeType = info.mimeType,
                durationMs = info.durationMs,
                reAnalyzeLines = lines.isEmpty(),
                preferredStyle = workingStyle,
                autoDetectCharacterVoices = true
            )
        }
    }

    val videoFileManagerAttachLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            videoError = null
            val info = inspectMediaUri(context, uri, fallbackMime = "video/mp4")
            viewModel.attachVideoAndOptionallyAnalyze(
                context = context,
                uri = uri,
                fileName = info.fileName,
                mimeType = info.mimeType,
                durationMs = info.durationMs,
                reAnalyzeLines = lines.isEmpty(),
                preferredStyle = workingStyle,
                autoDetectCharacterVoices = true
            )
        }
    }

    val wavExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { outputUri ->
        if (outputUri != null) {
            viewModel.exportDubbedWav(context, outputUri, effectiveLines)
        }
    }

    val projectJsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { outputUri ->
        val currentProject = project
        if (outputUri != null && currentProject != null) {
            viewModel.exportProjectFileToUri(context, outputUri, currentProject, effectiveLines)
        }
    }

    val srtExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { outputUri ->
        if (outputUri != null) {
            viewModel.exportSubtitlesSrtToUri(context, outputUri, effectiveLines)
        }
    }

    LaunchedEffect(status.message, status.error) {
        status.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
        status.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = project?.title ?: "Dubbing Studio",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${lines.size} संवाद • Hindi Voice Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopAudio()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to projects"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportDialog = true },
                        enabled = project != null && !status.isBusy,
                        modifier = Modifier.testTag("export_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SaveAlt,
                            contentDescription = "Export project and dubbed audio to local storage"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (project != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (draftHindiEdits.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveHindiTextEdits(draftHindiEdits) {
                                        draftHindiEdits = emptyMap()
                                        expandedEditLineIds = emptySet()
                                        editAllWordsMode = false
                                    }
                                },
                                enabled = !status.isBusy,
                                modifier = Modifier
                                    .heightIn(min = 50.dp)
                                    .testTag("bottom_save_edits_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("सेव (${draftHindiEdits.size})", maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                mediaPlayerRef?.takeIf { it.isPlaying }?.pause()
                                isVideoPlaying = false
                                viewModel.stopAudio()
                                viewModel.completeFinalHindiDubbing(
                                    context = context,
                                    project = project,
                                    lines = lines,
                                    pendingEdits = draftHindiEdits,
                                    onEditsSaved = {
                                        draftHindiEdits = emptyMap()
                                        expandedEditLineIds = emptySet()
                                        editAllWordsMode = false
                                    }
                                )
                            },
                            enabled = !status.isBusy && lines.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("complete_final_dubbing_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "फाइनल हिन्दी ऑडियो डबिंग पूरी करें",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter)
            ) {
                if (status.isBusy || audioState.isSynthesizing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Video + Synced Dub Player & Dub Audio Preview Section
                StudioVideoPlayerDeck(
                    project = project,
                    mediaInfo = attachedVideoMeta,
                    lines = effectiveLines,
                    activeSubtitleLine = activeSubtitleLine,
                    audioState = audioState,
                    isBusy = status.isBusy,
                    isVideoPlaying = isVideoPlaying,
                    videoPositionMs = videoPositionMs,
                    videoDurationMs = videoDurationMs,
                    videoError = videoError,
                    onMediaPlayerReady = { mp, duration ->
                        mediaPlayerRef = mp
                        videoDurationMs = duration
                        videoError = null
                        runCatching { mp.setVolume(originalVolume, originalVolume) }
                    },
                    onMediaPlayerError = { err ->
                        videoError = err
                        isVideoPlaying = false
                    },
                    onToggleVideoPlayPause = {
                        mediaPlayerRef?.let { mp ->
                            if (mp.isPlaying) {
                                mp.pause()
                                isVideoPlaying = false
                                viewModel.stopAudio()
                            } else {
                                mp.start()
                                isVideoPlaying = true
                            }
                        }
                    },
                    onToggleSyncedDub = {
                        if (audioState.isPlayingScene) {
                            viewModel.stopAudio()
                            mediaPlayerRef?.takeIf { it.isPlaying }?.pause()
                            isVideoPlaying = false
                        } else {
                            if (mediaPlayerRef != null) {
                                runCatching {
                                    mediaPlayerRef?.seekTo(0)
                                    mediaPlayerRef?.start()
                                    isVideoPlaying = true
                                }
                                viewModel.startSyncedScenePlayback(
                                    lines = effectiveLines,
                                    getVideoPositionMs = {
                                        runCatching { mediaPlayerRef?.currentPosition?.toLong() ?: 0L }
                                            .getOrDefault(0L)
                                    },
                                    isVideoPlaying = {
                                        runCatching { mediaPlayerRef?.isPlaying ?: false }
                                            .getOrDefault(false)
                                    }
                                )
                            } else if (effectiveLines.isNotEmpty()) {
                                viewModel.previewFullDubAudio(effectiveLines)
                            }
                        }
                    },
                    onToggleFullDubPreview = {
                        if (audioState.isPreviewingFullDub) {
                            viewModel.stopAudio()
                        } else {
                            mediaPlayerRef?.takeIf { it.isPlaying }?.pause()
                            isVideoPlaying = false
                            viewModel.previewFullDubAudio(effectiveLines)
                        }
                    },
                    onToggleFullPreviewPause = { viewModel.toggleFullPreviewPause() },
                    onSeekFullPreview = { pos -> viewModel.seekFullPreview(pos) },
                    onSeekTo = { posMs ->
                        videoPositionMs = posMs
                        runCatching { mediaPlayerRef?.seekTo(posMs.toInt()) }
                    },
                    onAttachVideoGallery = {
                        videoAttachLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onAttachVideoFileManager = {
                        videoFileManagerAttachLauncher.launch(videoFileMimeTypes)
                    },
                    onReDubVideo = {
                        project?.let { proj ->
                            viewModel.reDubAttachedMedia(
                                context = context,
                                project = proj,
                                preferredStyle = workingStyle,
                                autoDetectCharacterVoices = true
                            )
                        }
                    }
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("संवाद टाइमलाइन (${lines.size})") },
                        modifier = Modifier.testTag("tab_timeline")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("आवाज़ और भाव स्टूडियो") },
                        modifier = Modifier.testTag("tab_voice_studio")
                    )
                }

                when (selectedTab) {
                    0 -> {
                        TimelineTabContent(
                            linesState = linesState,
                            draftHindiEdits = draftHindiEdits,
                            expandedEditLineIds = expandedEditLineIds,
                            editAllWordsMode = editAllWordsMode,
                            isBusy = status.isBusy,
                            activePlayingLineId = audioState.activeLineId,
                            isSynthesizing = audioState.isSynthesizing,
                            onToggleEditAllWords = {
                                editAllWordsMode = !editAllWordsMode
                            },
                            onToggleLineWordEdit = { lineId ->
                                expandedEditLineIds = if (expandedEditLineIds.contains(lineId)) {
                                    expandedEditLineIds - lineId
                                } else {
                                    expandedEditLineIds + lineId
                                }
                            },
                            onHindiWordDraftChange = { line, newText ->
                                draftHindiEdits = if (newText == line.hindiText) {
                                    draftHindiEdits - line.id
                                } else {
                                    draftHindiEdits + (line.id to newText)
                                }
                            },
                            onResetLineWordDraft = { lineId ->
                                draftHindiEdits = draftHindiEdits - lineId
                                expandedEditLineIds = expandedEditLineIds - lineId
                            },
                            onSaveAllWordEdits = {
                                viewModel.saveHindiTextEdits(draftHindiEdits) {
                                    draftHindiEdits = emptyMap()
                                    expandedEditLineIds = emptySet()
                                    editAllWordsMode = false
                                }
                            },
                            onSaveSingleLineWords = { line ->
                                val pendingText = draftHindiEdits[line.id]
                                if (pendingText != null) {
                                    viewModel.saveHindiTextEdits(mapOf(line.id to pendingText)) {
                                        draftHindiEdits = draftHindiEdits - line.id
                                        expandedEditLineIds = expandedEditLineIds - line.id
                                    }
                                } else {
                                    expandedEditLineIds = expandedEditLineIds - line.id
                                }
                            },
                            onPreviewLine = { line ->
                                if (audioState.activeLineId == line.id) {
                                    viewModel.stopAudio()
                                } else {
                                    val editedText = draftHindiEdits[line.id]
                                    val lineToPlay = if (editedText != null) {
                                        line.copy(hindiText = editedText)
                                    } else {
                                        line
                                    }
                                    viewModel.previewLine(lineToPlay)
                                }
                            },
                            onEditLine = { line -> editingLine = line },
                            onDeleteLine = { line ->
                                draftHindiEdits = draftHindiEdits - line.id
                                expandedEditLineIds = expandedEditLineIds - line.id
                                viewModel.deleteLine(line.id, lines.size)
                            },
                            onAddLine = { showAddLineDialog = true }
                        )
                    }
                    1 -> {
                        VoiceStudioTabContent(
                            style = workingStyle,
                            originalVolume = originalVolume,
                            isBusy = status.isBusy,
                            isSynthesizing = audioState.isSynthesizing,
                            isPreviewingFullDub = audioState.isPreviewingFullDub,
                            linesCount = lines.size,
                            onStyleChange = { workingStyle = it },
                            onOriginalVolumeChange = { originalVolume = it },
                            onAuditionSelectedStyle = {
                                viewModel.previewStyleAudition(workingStyle, effectiveLines)
                            },
                            onPreviewFullDub = {
                                if (audioState.isPreviewingFullDub) {
                                    viewModel.stopAudio()
                                } else {
                                    viewModel.previewFullDubAudio(effectiveLines)
                                }
                            },
                            onApplyToAllLines = {
                                viewModel.updateGlobalVoiceStyle(
                                    style = workingStyle,
                                    originalAudioVolume = originalVolume.toDouble(),
                                    applyToAllLines = true,
                                    currentLines = effectiveLines
                                )
                            },
                            onSaveDefaultOnly = {
                                viewModel.updateGlobalVoiceStyle(
                                    style = workingStyle,
                                    originalAudioVolume = originalVolume.toDouble(),
                                    applyToAllLines = false,
                                    currentLines = effectiveLines
                                )
                            },
                            onSaveProjectToDownloads = {
                                project?.let { proj ->
                                    viewModel.saveProjectFileToMobileStorage(context, proj, effectiveLines)
                                }
                            },
                            onSaveWavToDownloads = {
                                project?.let { proj ->
                                    viewModel.saveDubbedWavToMobileStorage(context, proj, effectiveLines)
                                }
                            },
                            onOpenExportMenu = { showExportDialog = true }
                        )
                    }
                }
            }
        }
    }

    editingLine?.let { targetLine ->
        LineVoiceEditorDialog(
            line = targetLine,
            isBusy = status.isBusy,
            onDismiss = { editingLine = null },
            onReAdaptWithAi = { eng, hin, gender, age, emo, onAdapted ->
                viewModel.reAdaptLineWithAi(
                    line = targetLine.copy(englishText = eng, hindiText = hin),
                    targetGender = gender,
                    targetAgeGroup = age,
                    targetEmotion = emo,
                    onAdaptedText = onAdapted
                )
            },
            onSave = { updated ->
                viewModel.updateLine(updated)
                editingLine = null
            }
        )
    }

    if (showAddLineDialog) {
        AddDialogueLineDialog(
            onDismiss = { showAddLineDialog = false },
            onAdd = { speaker, english, hindi, emotion ->
                viewModel.addManualLine(
                    speakerName = speaker,
                    englishText = english,
                    hindiText = hindi,
                    emotion = emotion,
                    style = workingStyle,
                    currentLines = lines
                )
                showAddLineDialog = false
            }
        )
    }
    if (showExportDialog && project != null) {
        val baseName = ProjectFileExporter.sanitizedBaseName(project.title)
        ExportAndSaveDialog(
            projectTitle = project.title,
            hasLines = effectiveLines.isNotEmpty(),
            isBusy = status.isBusy,
            onDismiss = { showExportDialog = false },
            onSaveProjectDownloads = {
                showExportDialog = false
                viewModel.saveProjectFileToMobileStorage(context, project, effectiveLines)
            },
            onSaveProjectChooseFolder = {
                showExportDialog = false
                projectJsonExportLauncher.launch("${baseName}.swardub.json")
            },
            onSaveWavDownloads = {
                showExportDialog = false
                viewModel.saveDubbedWavToMobileStorage(context, project, effectiveLines)
            },
            onSaveWavChooseFolder = {
                showExportDialog = false
                wavExportLauncher.launch("${baseName}_hindi_dub.wav")
            },
            onSaveSrtDownloads = {
                showExportDialog = false
                viewModel.saveSubtitlesSrtToMobileStorage(context, project, effectiveLines)
            },
            onSaveSrtChooseFolder = {
                showExportDialog = false
                srtExportLauncher.launch("${baseName}_hindi.srt")
            }
        )
    }

    finalDubResult?.let { result ->
        val baseName = ProjectFileExporter.sanitizedBaseName(result.projectTitle)
        FinalDubbingCompleteDialog(
            result = result,
            onPlayFinalAudio = {
                mediaPlayerRef?.takeIf { it.isPlaying }?.pause()
                isVideoPlaying = false
                viewModel.previewFullDubAudio(result.finalizedLines)
                viewModel.dismissFinalDubResult()
            },
            onSaveCopyFolder = {
                viewModel.dismissFinalDubResult()
                wavExportLauncher.launch("${baseName}_hindi_dub_final.wav")
            },
            onSaveSrt = {
                project?.let { proj ->
                    viewModel.saveSubtitlesSrtToMobileStorage(context, proj, result.finalizedLines)
                }
            },
            onDismiss = { viewModel.dismissFinalDubResult() }
        )
    }
}

@Composable
private fun StudioVideoPlayerDeck(
    project: DubbingProject?,
    mediaInfo: MediaMetaInfo?,
    lines: List<DubLine>,
    activeSubtitleLine: DubLine?,
    audioState: DubAudioPlaybackState,
    isBusy: Boolean,
    isVideoPlaying: Boolean,
    videoPositionMs: Long,
    videoDurationMs: Long,
    videoError: String?,
    onMediaPlayerReady: (MediaPlayer, Long) -> Unit,
    onMediaPlayerError: (String) -> Unit,
    onToggleVideoPlayPause: () -> Unit,
    onToggleSyncedDub: () -> Unit,
    onToggleFullDubPreview: () -> Unit,
    onToggleFullPreviewPause: () -> Unit,
    onSeekFullPreview: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onAttachVideoGallery: () -> Unit,
    onAttachVideoFileManager: () -> Unit,
    onReDubVideo: () -> Unit
) {
    val videoUri = project?.videoUriString?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    val subtitleToShow = activeSubtitleLine ?: lines.firstOrNull {
        videoPositionMs in it.startMs..it.endMs
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            if (videoUri != null && videoError == null) {
                var activePlayer by remember(videoUri) { mutableStateOf<MediaPlayer?>(null) }
                DisposableEffect(videoUri) {
                    onDispose {
                        runCatching {
                            activePlayer?.release()
                        }
                        activePlayer = null
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTex: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    runCatching {
                                        val mp = MediaPlayer().apply {
                                            setDataSource(ctx, videoUri)
                                            setSurface(Surface(surfaceTex))
                                            setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                            setOnPreparedListener { prepared ->
                                                onMediaPlayerReady(prepared, prepared.duration.toLong())
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                onMediaPlayerError("वीडियो लोड नहीं हो सका। कृपया गैलरी या फ़ाइल मैनेजर से क्लिप दोबारा चुनें।")
                                                true
                                            }
                                            prepareAsync()
                                        }
                                        activePlayer = mp
                                    }.onFailure {
                                        onMediaPlayerError("वीडियो एक्सेस अनुमति समाप्त हो गई है। क्लिप दोबारा चुनें।")
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    runCatching {
                                        activePlayer?.release()
                                    }
                                    activePlayer = null
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = videoError ?: "गैलरी या फ़ाइल मैनेजर से वीडियो जोड़ें (144p से 4K HD)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAttachVideoGallery,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("studio_pick_gallery_button")
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("गैलरी (Gallery)")
                        }
                        OutlinedButton(
                            onClick = onAttachVideoFileManager,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("studio_pick_file_manager_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("फ़ाइल मैनेजर")
                        }
                    }
                }
            }

            // Live Hindi Dubbing Subtitle Overlay
            if (subtitleToShow != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${subtitleToShow.speakerName}: ${subtitleToShow.hindiText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Attached video format/quality badge + switch/re-dub strip
        if (videoUri != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mediaInfo?.summaryBadge() ?: project?.videoFileName.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onAttachVideoGallery,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("switch_gallery_video_button")
                    ) {
                        Text("गैलरी", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = onAttachVideoFileManager,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("switch_file_manager_video_button")
                    ) {
                        Text("फ़ाइल", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = onReDubVideo,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("redub_video_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("हिन्दी डब करें", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Video Timeline & Synced Dub Transport Bar
        if (videoUri != null && videoDurationMs > 0L && videoError == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatTimeMs(videoPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = videoPositionMs.toFloat().coerceIn(0f, videoDurationMs.toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..videoDurationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTimeMs(videoDurationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (videoUri != null && videoError == null) {
                OutlinedButton(
                    onClick = onToggleVideoPlayPause,
                    modifier = Modifier.testTag("video_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isVideoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isVideoPlaying) "रोकें" else "वीडियो")
                }

                Button(
                    onClick = onToggleSyncedDub,
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_synced_dub_button")
                ) {
                    Icon(
                        imageVector = if (audioState.isPlayingScene) Icons.Rounded.Stop else Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (audioState.isPlayingScene) "सिंक रोकें" else "वीडियो + डब",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val previewBtnModifier = if (videoUri != null && videoError == null) {
                Modifier.testTag("dub_audio_preview_button")
            } else {
                Modifier
                    .weight(1f)
                    .testTag("dub_audio_preview_button")
            }

            if (audioState.isPreviewingFullDub) {
                Button(
                    onClick = onToggleFullDubPreview,
                    modifier = previewBtnModifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("प्रीव्यू रोकें (Stop)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(
                    onClick = onToggleFullDubPreview,
                    enabled = lines.isNotEmpty() && !audioState.isSynthesizing,
                    modifier = previewBtnModifier
                ) {
                    if (audioState.isSynthesizing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("डब ऑडियो प्रीव्यू", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Inline Full Dub Audio Preview Bar with Play/Pause & Seek
        if (audioState.isPreviewingFullDub) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dub_audio_preview_bar")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "डब ऑडियो प्रीव्यू (Dub Audio Preview)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onToggleFullPreviewPause,
                                modifier = Modifier.testTag("dub_preview_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (audioState.isPreviewPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = if (audioState.isPreviewPaused) "Resume preview" else "Pause preview"
                                )
                            }
                            IconButton(
                                onClick = onToggleFullDubPreview,
                                modifier = Modifier.testTag("dub_preview_stop_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop dub audio preview"
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatTimeMs(audioState.previewPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = audioState.previewPositionMs.toFloat()
                                .coerceIn(0f, audioState.previewDurationMs.coerceAtLeast(1L).toFloat()),
                            onValueChange = { onSeekFullPreview(it.toLong()) },
                            valueRange = 0f..audioState.previewDurationMs.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTimeMs(audioState.previewDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTabContent(
    linesState: UiState<List<DubLine>>,
    draftHindiEdits: Map<String, String>,
    expandedEditLineIds: Set<String>,
    editAllWordsMode: Boolean,
    isBusy: Boolean,
    activePlayingLineId: String?,
    isSynthesizing: Boolean,
    onToggleEditAllWords: () -> Unit,
    onToggleLineWordEdit: (String) -> Unit,
    onHindiWordDraftChange: (DubLine, String) -> Unit,
    onResetLineWordDraft: (String) -> Unit,
    onSaveAllWordEdits: () -> Unit,
    onSaveSingleLineWords: (DubLine) -> Unit,
    onPreviewLine: (DubLine) -> Unit,
    onEditLine: (DubLine) -> Unit,
    onDeleteLine: (DubLine) -> Unit,
    onAddLine: () -> Unit
) {
    when (linesState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(linesState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            val lines = linesState.data
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (draftHindiEdits.isNotEmpty()) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (draftHindiEdits.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "2. हिन्दी शब्द बदलें / सुधारें और सेव करें",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (draftHindiEdits.isNotEmpty()) {
                                    "${draftHindiEdits.size} संवादों में शब्द बदले गए हैं। नीचे सेव करें और फिर 'फाइनल हिन्दी ऑडियो डबिंग पूरी करें' दबाएँ।"
                                } else {
                                    "किसी भी संवाद के हिन्दी शब्द सीधे एडिट करें, बदलाव सेव करें, और फिर फाइनल हिन्दी ऑडियो डबिंग पूरी करें।"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onToggleEditAllWords,
                                    enabled = lines.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("toggle_edit_all_words_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (editAllWordsMode) "एडिट बॉक्स समेटें" else "सभी शब्द एडिट करें",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (draftHindiEdits.isNotEmpty()) {
                                    Button(
                                        onClick = onSaveAllWordEdits,
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("save_hindi_edits_button")
                                    ) {
                                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "बदलाव सेव करें (${draftHindiEdits.size})",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(lines, key = { it.id }) { line ->
                    val draftText = draftHindiEdits[line.id]
                    val isEditingWords = editAllWordsMode || expandedEditLineIds.contains(line.id)
                    DubLineCard(
                        line = line,
                        draftHindiText = draftText,
                        isEditingWords = isEditingWords,
                        isBusy = isBusy,
                        isPlaying = activePlayingLineId == line.id,
                        isSynthesizing = isSynthesizing && activePlayingLineId == line.id,
                        onToggleWordEdit = { onToggleLineWordEdit(line.id) },
                        onHindiWordDraftChange = { newText -> onHindiWordDraftChange(line, newText) },
                        onResetWordDraft = { onResetLineWordDraft(line.id) },
                        onSaveLineWords = { onSaveSingleLineWords(line) },
                        onPreview = { onPreviewLine(line) },
                        onEdit = { onEditLine(line) },
                        onDelete = { onDeleteLine(line) }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onAddLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_line_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("नया संवाद जोड़ें (Add Dialogue Line)")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DubLineCard(
    line: DubLine,
    draftHindiText: String?,
    isEditingWords: Boolean,
    isBusy: Boolean,
    isPlaying: Boolean,
    isSynthesizing: Boolean,
    onToggleWordEdit: () -> Unit,
    onHindiWordDraftChange: (String) -> Unit,
    onResetWordDraft: () -> Unit,
    onSaveLineWords: () -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val emotion = OriginalEmotion.fromRaw(line.emotion)
    val gender = VoiceGender.fromRaw(line.voiceGender)
    val age = VoiceAgeGroup.fromRaw(line.ageGroup)
    val effect = VoiceEffect.fromRaw(line.voiceEffect)
    val effective = line.effectiveParams()
    val hasUnsavedDraft = draftHindiText != null
    val displayedHindiText = draftHindiText ?: line.hindiText

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            width = if (isPlaying || hasUnsavedDraft) 1.5.dp else 1.dp,
            color = when {
                isPlaying -> MaterialTheme.colorScheme.primary
                hasUnsavedDraft -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dub_line_card_${line.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = line.speakerName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${formatTimeMs(line.startMs)} → ${formatTimeMs(line.endMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasUnsavedDraft) {
                        VoiceTag("बदला गया", accent = MaterialTheme.colorScheme.tertiary)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete line",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            EmotionBadge(
                emotion = emotion,
                intensity = line.emotionIntensity.toFloat()
            )

            if (line.englishText.isNotBlank()) {
                Text(
                    text = line.englishText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEditingWords) {
                OutlinedTextField(
                    value = displayedHindiText,
                    onValueChange = onHindiWordDraftChange,
                    label = { Text("हिन्दी शब्द बदलें (Edit Hindi Words)") },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inline_hindi_input_${line.orderIndex}")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveLineWords,
                        enabled = !isBusy && displayedHindiText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("inline_save_words_button_${line.orderIndex}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (hasUnsavedDraft) "बदलाव सेव करें" else "सेव / ठीक है")
                    }
                    OutlinedButton(
                        onClick = onResetWordDraft,
                        enabled = !isBusy,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (hasUnsavedDraft) "रीसेट" else "बंद करें")
                    }
                }
            } else {
                Text(
                    text = displayedHindiText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Active Voice Style & Changer Tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceTag("${gender.hindiLabel} • ${age.hindiLabel}")
                VoiceTag(effect.hindiLabel)
                VoiceTag(
                    "Pitch ${String.format(Locale.US, "%.2fx", effective.finalPitch)} • ${
                        String.format(Locale.US, "%.2fx", effective.finalSpeed)
                    }"
                )
                if (line.matchOriginalEmotion) {
                    VoiceTag("भाव मिलान ON", accent = MaterialTheme.colorScheme.tertiary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onPreview,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("preview_line_button_${line.orderIndex}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSynthesizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "रोकें" else "सुनें",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onToggleWordEdit,
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 48.dp)
                        .testTag("quick_edit_words_button_${line.orderIndex}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEditingWords) "छुपाएँ" else "शब्द बदलें",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("edit_line_button_${line.orderIndex}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "वॉइस/भाव",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceTag(
    text: String,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VoiceStudioTabContent(
    style: VoiceStyleConfig,
    originalVolume: Float,
    isBusy: Boolean,
    isSynthesizing: Boolean,
    isPreviewingFullDub: Boolean,
    linesCount: Int,
    onStyleChange: (VoiceStyleConfig) -> Unit,
    onOriginalVolumeChange: (Float) -> Unit,
    onAuditionSelectedStyle: () -> Unit,
    onPreviewFullDub: () -> Unit,
    onApplyToAllLines: () -> Unit,
    onSaveDefaultOnly: () -> Unit,
    onSaveProjectToDownloads: () -> Unit,
    onSaveWavToDownloads: () -> Unit,
    onOpenExportMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VoiceStyleConfigurator(
            style = style,
            onStyleChange = onStyleChange
        )

        // Live Dub Audio Preview & Voice Style Audition Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "डब ऑडियो प्रीव्यू (Dub Audio Preview)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = "लागू करने से पहले चुनी हुई आवाज़, उम्र और फ़िल्टर का लाइव प्रीव्यू सुनें या पूरा हिन्दी डब सुनें।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAuditionSelectedStyle,
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_voice_style_button")
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("आवाज़ टेस्ट करें", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onPreviewFullDub,
                        enabled = linesCount > 0 && !isSynthesizing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_full_dub_in_studio_button")
                    ) {
                        Icon(
                            imageVector = if (isPreviewingFullDub) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPreviewingFullDub) "प्रीव्यू रोकें" else "पूरा डब सुनें",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "मूल फ़िल्म ऑडियो स्तर (Original Audio Ducking)",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "${(originalVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Slider(
                    value = originalVolume,
                    onValueChange = onOriginalVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
            }
        }

        Button(
            onClick = onApplyToAllLines,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("apply_voice_to_all_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "सभी $linesCount संवादों पर आवाज़ लागू करें (Apply to All Lines)",
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onSaveDefaultOnly,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("डिफ़ॉल्ट स्टाइल सेव करें (Save Default Style)")
        }

        // Local Mobile Storage & Project File Export Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SaveAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "मोबाइल स्टोरेज में सेव व एक्सपोर्ट करें",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = "प्रोजेक्ट फ़ाइल (.swardub.json), हिन्दी डब ऑडियो (.wav) या सबटाइटल (.srt) सीधे फोन के Downloads/SwarDub में सेव करें।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveProjectToDownloads,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_project_quick_button")
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("प्रोजेक्ट सेव", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onSaveWavToDownloads,
                        enabled = !isBusy && linesCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_wav_quick_button")
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WAV सेव", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = onOpenExportMenu,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_export_options_button")
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("सभी एक्सपोर्ट विकल्प (JSON / WAV / SRT)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExportAndSaveDialog(
    projectTitle: String,
    hasLines: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSaveProjectDownloads: () -> Unit,
    onSaveProjectChooseFolder: () -> Unit,
    onSaveWavDownloads: () -> Unit,
    onSaveWavChooseFolder: () -> Unit,
    onSaveSrtDownloads: () -> Unit,
    onSaveSrtChooseFolder: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("प्रोजेक्ट और डब एक्सपोर्ट करें (Save to Mobile)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "\"$projectTitle\" के लिए सेव विकल्प चुनें:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Project File (.swardub.json)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("प्रोजेक्ट फ़ाइल (.swardub.json)", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = onSaveProjectDownloads,
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_project_downloads_button")
                        ) {
                            Text("मोबाइल Downloads/SwarDub में सेव करें")
                        }
                        OutlinedButton(
                            onClick = onSaveProjectChooseFolder,
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_project_saf_button")
                        ) {
                            Text("फ़ोल्डर चुनकर सेव करें (Pick Folder)")
                        }
                    }
                }

                // 2. Hindi Dub Audio (.wav)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text("हिन्दी डब ऑडियो (.wav)", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = onSaveWavDownloads,
                            enabled = !isBusy && hasLines,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_wav_downloads_button")
                        ) {
                            Text("मोबाइल Downloads/SwarDub में सेव करें")
                        }
                        OutlinedButton(
                            onClick = onSaveWavChooseFolder,
                            enabled = !isBusy && hasLines,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_wav_saf_button")
                        ) {
                            Text("फ़ोल्डर चुनकर सेव करें (Pick Folder)")
                        }
                    }
                }

                // 3. Hindi Subtitles (.srt)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text("हिन्दी सबटाइटल (.srt)", style = MaterialTheme.typography.labelLarge)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSaveSrtDownloads,
                                enabled = !isBusy && hasLines,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_srt_downloads_button")
                            ) {
                                Text("Downloads में सेव", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onSaveSrtChooseFolder,
                                enabled = !isBusy && hasLines,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_srt_saf_button")
                            ) {
                                Text("फ़ोल्डर चुनें", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("बंद करें (Close)")
            }
        }
    )
}

@Composable
private fun AddDialogueLineDialog(
    onDismiss: () -> Unit,
    onAdd: (speaker: String, english: String, hindi: String, emotion: OriginalEmotion) -> Unit
) {
    var speaker by remember { mutableStateOf("Hero") }
    var english by remember { mutableStateOf("") }
    var hindi by remember { mutableStateOf("") }
    var emotion by remember { mutableStateOf(OriginalEmotion.NEUTRAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("नया संवाद जोड़ें (Add Line)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = speaker,
                    onValueChange = { speaker = it },
                    label = { Text("पात्र (Speaker)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = english,
                    onValueChange = { english = it },
                    label = { Text("अंग्रेज़ी संवाद (English)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hindi,
                    onValueChange = { hindi = it },
                    label = { Text("हिन्दी डब (Hindi)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(speaker, english, hindi, emotion) },
                enabled = hindi.isNotBlank()
            ) {
                Text("जोड़ें (Add)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("रद्द करें")
            }
        }
    )
}

@Composable
private fun FinalDubbingCompleteDialog(
    result: FinalDubCompletionResult,
    onPlayFinalAudio: () -> Unit,
    onSaveCopyFolder: () -> Unit,
    onSaveSrt: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = { Text("फाइनल हिन्दी डबिंग पूरी हुई!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "\"${result.projectTitle}\" के सभी ${result.lineCount} संवादों की फाइनल हिन्दी ऑडियो डबिंग सफलतापूर्वक तैयार और सेव हो गई है।",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "सेव स्थान (Saved WAV Audio):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = result.savedLocation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                Button(
                    onClick = onPlayFinalAudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("play_final_dub_audio_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("फाइनल हिन्दी ऑडियो सुनें (Play Final Dub)")
                }
                OutlinedButton(
                    onClick = onSaveCopyFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_final_dub_copy_button")
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("दूसरे फ़ोल्डर में सेव करें (Save Copy As)")
                }
                OutlinedButton(
                    onClick = onSaveSrt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_final_srt_button")
                ) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("हिन्दी सबटाइटल (.srt) सेव करें")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_final_dub_dialog_button")
            ) {
                Text("बंद करें (Done)")
            }
        }
    )
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    val tenths = (ms % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}
