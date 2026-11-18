package com.example.ui.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ProjectFileExporter
import com.example.media.MediaMetaInfo
import com.example.media.inspectMediaUri
import com.example.media.persistReadPermission
import com.example.model.DubbingProject
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceEffect
import com.example.model.VoiceGender
import com.example.model.VoiceStyleConfig
import com.example.ui.auth.signOutUser
import com.example.ui.components.VoiceStyleConfigurator
import com.example.viewmodel.ProjectCreationState
import com.example.viewmodel.ProjectsViewModel
import com.example.viewmodel.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    userEmail: String?,
    onOpenProject: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val creationState by viewModel.creationState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateWizard by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaMeta by remember { mutableStateOf<MediaMetaInfo?>(null) }
    var selectedMimeType by remember { mutableStateOf("video/mp4") }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedDurationMs by remember { mutableLongStateOf(20_000L) }
    var pendingDeleteProject by remember { mutableStateOf<DubbingProject?>(null) }
    var pendingExportProject by remember { mutableStateOf<DubbingProject?>(null) }
    var safTargetProject by remember { mutableStateOf<DubbingProject?>(null) }

    val videoFileMimeTypes = remember {
        arrayOf(
            "video/*",
            "application/x-matroska",
            "application/mp4",
            "application/octet-stream",
            "*/*"
        )
    }

    // Zero-permission Android Photo Picker for Gallery videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val info = inspectMediaUri(context, uri, fallbackMime = "video/mp4")
            selectedUri = uri
            selectedMediaMeta = info
            selectedMimeType = info.mimeType
            selectedFileName = info.fileName
            selectedDurationMs = info.durationMs
            showCreateWizard = true
        }
    }

    // SAF OpenDocument for File Manager videos (supports MP4, MKV, 3GP, AVI, MOV, WebM, TS, etc.)
    val videoFileManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            val info = inspectMediaUri(context, uri, fallbackMime = "video/mp4")
            selectedUri = uri
            selectedMediaMeta = info
            selectedMimeType = info.mimeType
            selectedFileName = info.fileName
            selectedDurationMs = info.durationMs
            showCreateWizard = true
        }
    }

    // SAF OpenDocument for audio/video clips
    val audioDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            val info = inspectMediaUri(context, uri, fallbackMime = "audio/mpeg")
            selectedUri = uri
            selectedMediaMeta = info
            selectedMimeType = info.mimeType
            selectedFileName = info.fileName
            selectedDurationMs = info.durationMs
            showCreateWizard = true
        }
    }

    // SAF OpenDocument for saved .swardub.json project file import
    val importProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importProjectFileFromUri(context, uri)
        }
    }

    // SAF CreateDocument for saving .swardub.json project file to chosen folder
    val exportProjectSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { outputUri ->
        val proj = safTargetProject
        safTargetProject = null
        if (outputUri != null && proj != null) {
            viewModel.exportProjectFileToUri(context, outputUri, proj)
        }
    }

    LaunchedEffect(creationState) {
        when (val state = creationState) {
            is ProjectCreationState.Created -> {
                showCreateWizard = false
                selectedUri = null
                selectedMediaMeta = null
                val note = state.note
                viewModel.resetCreationState()
                if (!note.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(note)
                }
                onOpenProject(state.projectId)
            }
            is ProjectCreationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetCreationState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(actionError) {
        actionError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.projects_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { signOutUser(credentialManager, scope) },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.sign_out)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    selectedUri = null
                    selectedMediaMeta = null
                    selectedFileName = ""
                    showCreateWizard = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("नया डब (New Dub)") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("new_dub_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StudioHeroLaunchCard(
                        onPickVideoGallery = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onPickVideoFileManager = {
                            videoFileManagerLauncher.launch(videoFileMimeTypes)
                        },
                        onPickAudio = {
                            audioDocLauncher.launch(arrayOf("audio/*", "video/*"))
                        },
                        onScriptDub = {
                            selectedUri = null
                            selectedMediaMeta = null
                            selectedFileName = ""
                            showCreateWizard = true
                        },
                        onImportProject = {
                            importProjectLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }

                item {
                    Text(
                        text = "आपके डबिंग प्रोजेक्ट (Your Dubbing Projects)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                when (val state = projectsState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    is UiState.Error -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                EmptyProjectsCard(
                                    onPickVideoGallery = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    onPickVideoFileManager = {
                                        videoFileManagerLauncher.launch(videoFileMimeTypes)
                                    },
                                    onScriptDub = {
                                        selectedUri = null
                                        selectedMediaMeta = null
                                        selectedFileName = ""
                                        showCreateWizard = true
                                    }
                                )
                            }
                        } else {
                            items(state.data, key = { it.id }) { project ->
                                ProjectCard(
                                    project = project,
                                    onClick = { onOpenProject(project.id) },
                                    onExport = { pendingExportProject = project },
                                    onDelete = { pendingDeleteProject = project }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateWizard) {
        NewDubbingWizardDialog(
            initialUri = selectedUri,
            initialMediaMeta = selectedMediaMeta,
            initialFileName = selectedFileName,
            initialMimeType = selectedMimeType,
            initialDurationMs = selectedDurationMs,
            creationState = creationState,
            onPickVideoGallery = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPickVideoFileManager = {
                videoFileManagerLauncher.launch(videoFileMimeTypes)
            },
            onPickAudio = {
                audioDocLauncher.launch(arrayOf("audio/*", "video/*"))
            },
            onDismiss = {
                if (creationState !is ProjectCreationState.Processing) {
                    viewModel.resetCreationState()
                    showCreateWizard = false
                }
            },
            onCreateFromMedia = { title, uri, name, mime, dur, style, autoDetect ->
                viewModel.createFromMediaClip(
                    context = context,
                    title = title,
                    uri = uri,
                    fileName = name,
                    mimeType = mime,
                    durationMs = dur,
                    preferredStyle = style,
                    autoDetectCharacterVoices = autoDetect
                )
            },
            onCreateFromScript = { title, script, style, autoDetect ->
                viewModel.createFromEnglishScript(
                    title = title,
                    englishScript = script,
                    preferredStyle = style,
                    autoDetectCharacterVoices = autoDetect,
                    attachedVideoUri = selectedUri,
                    attachedVideoName = selectedFileName,
                    attachedDurationMs = selectedDurationMs
                )
            }
        )
    }

    pendingDeleteProject?.let { proj ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProject = null },
            title = { Text("प्रोजेक्ट हटाएँ? (Delete Project)") },
            text = { Text("\"${proj.title}\" और इसके सभी हिन्दी डब संवाद स्थायी रूप से हट जाएँगे।") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(proj.id)
                        pendingDeleteProject = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("हटाएँ (Delete)")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProject = null }) {
                    Text("रद्द करें (Cancel)")
                }
            }
        )
    }
    pendingExportProject?.let { proj ->
        AlertDialog(
            onDismissRequest = { pendingExportProject = null },
            title = { Text("प्रोजेक्ट फ़ाइल सेव करें (Export Project)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "\"${proj.title}\" को .swardub.json प्रोजेक्ट फ़ाइल के रूप में मोबाइल स्टोरेज में सेव करें।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            viewModel.saveProjectFileToMobileStorage(context, proj)
                            pendingExportProject = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_project_downloads_button")
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Downloads/SwarDub में तुरंत सेव करें")
                    }
                    OutlinedButton(
                        onClick = {
                            safTargetProject = proj
                            pendingExportProject = null
                            exportProjectSafLauncher.launch(
                                "${ProjectFileExporter.sanitizedBaseName(proj.title)}.swardub.json"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_project_choose_folder_button")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("मोबाइल फ़ोल्डर चुनकर सेव करें")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingExportProject = null }) {
                    Text("बंद करें (Close)")
                }
            }
        )
    }
}

@Composable
private fun StudioHeroLaunchCard(
    onPickVideoGallery: () -> Unit,
    onPickVideoFileManager: () -> Unit,
    onPickAudio: () -> Unit,
    onScriptDub: () -> Unit,
    onImportProject: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero_1794908349287),
                    contentDescription = "Dubbing studio control deck",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "AI English → Hindi Movie Dubbing",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "गैलरी या फ़ाइल मैनेजर से वीडियो चुनें • Low (144p) से 4K HD सभी फॉर्मेट सपोर्ट",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickVideoGallery,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("pick_video_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("गैलरी से वीडियो", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = onPickVideoFileManager,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("pick_video_file_manager_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("फ़ाइल मैनेजर", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Text(
                    text = "सपोर्टेड वीडियो फॉर्मेट: MP4, MKV, 3GP, AVI, MOV, WebM, TS, FLV (Low Quality से HD/4K तक)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickAudio,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pick_audio_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ऑडियो चुनें", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onScriptDub,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("script_dub_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("स्क्रिप्ट लिखें", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OutlinedButton(
                    onClick = onImportProject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("import_project_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("प्रोजेक्ट फ़ाइल इम्पोर्ट करें (.swardub.json)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsCard(
    onPickVideoGallery: () -> Unit,
    onPickVideoFileManager: () -> Unit,
    onScriptDub: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = stringResource(R.string.empty_projects_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.empty_projects_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickVideoGallery,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("गैलरी से वीडियो", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onPickVideoFileManager,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("फ़ाइल मैनेजर", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(
                onClick = onScriptDub,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("स्क्रिप्ट से शुरू करें (Write Script)")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(
    project: DubbingProject,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val gender = VoiceGender.fromRaw(project.defaultGender)
    val age = VoiceAgeGroup.fromRaw(project.defaultAgeGroup)
    val effect = VoiceEffect.fromRaw(project.defaultEffect)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = if (project.videoFileName.isNotBlank()) {
                            "${project.videoFileName} • ${project.lineCount} संवाद"
                        } else {
                            "स्क्रिप्ट डब • ${project.lineCount} संवाद"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.testTag("export_project_button_${project.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SaveAlt,
                            contentDescription = "Export project file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete project",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetaTag("${gender.hindiLabel} (${gender.englishLabel})")
                MetaTag("${age.hindiLabel} (${age.englishLabel})")
                MetaTag(effect.hindiLabel)
                if (project.matchOriginalEmotion) {
                    MetaTag("भाव मिलान ON", highlight = true)
                }
            }
        }
    }
}

@Composable
private fun MetaTag(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun NewDubbingWizardDialog(
    initialUri: Uri?,
    initialMediaMeta: MediaMetaInfo?,
    initialFileName: String,
    initialMimeType: String,
    initialDurationMs: Long,
    creationState: ProjectCreationState,
    onPickVideoGallery: () -> Unit,
    onPickVideoFileManager: () -> Unit,
    onPickAudio: () -> Unit,
    onDismiss: () -> Unit,
    onCreateFromMedia: (
        title: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        durationMs: Long,
        style: VoiceStyleConfig,
        autoDetectVoices: Boolean
    ) -> Unit,
    onCreateFromScript: (
        title: String,
        script: String,
        style: VoiceStyleConfig,
        autoDetectVoices: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialFileName.substringBeforeLast('.')) }
    var englishScript by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(VoiceStyleConfig()) }
    var autoDetectVoices by remember { mutableStateOf(true) }

    LaunchedEffect(initialUri, initialFileName) {
        if (title.isBlank() && initialFileName.isNotBlank()) {
            title = initialFileName.substringBeforeLast('.')
        }
    }

    val isProcessing = creationState is ProjectCreationState.Processing

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 780.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "नया AI हिन्दी डब प्रोजेक्ट (New Dubbing)",
                    style = MaterialTheme.typography.titleLarge
                )

                // Step 1: Attached Media Summary + Gallery / File Manager Upload Options
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1. वीडियो / ऑडियो अपलोड करें (Gallery या File Manager)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (initialUri != null) {
                            Text(
                                text = "चयनित फ़ाइल: ${initialFileName.ifBlank { "Media Clip" }}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            val badge = initialMediaMeta?.summaryBadge()
                                ?: "${initialDurationMs / 1000L}s • $initialMimeType"
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "गैलरी या फ़ाइल मैनेजर से कोई भी वीडियो (Low 144p से 4K HD — MP4, MKV, 3GP, AVI, MOV, WebM) चुनें या नीचे अंग्रेज़ी संवाद लिखें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPickVideoGallery,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("wizard_pick_gallery_button")
                            ) {
                                Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("गैलरी", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onPickVideoFileManager,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("wizard_pick_file_manager_button")
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("फ़ाइल मैनेजर", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        OutlinedButton(
                            onClick = onPickAudio,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("wizard_pick_audio_button")
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ऑडियो फ़ाइल चुनें (MP3 / WAV / M4A / AAC)")
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("प्रोजेक्ट नाम (Movie / Scene Title)") },
                    placeholder = { Text("उदा. Action Scene Hindi Dub") },
                    singleLine = true,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_title_input")
                )

                OutlinedTextField(
                    value = englishScript,
                    onValueChange = { englishScript = it },
                    label = {
                        Text(
                            if (initialUri != null) {
                                "वैकल्पिक अंग्रेज़ी संवाद (या AI वीडियो से खुद निकालेगी)"
                            } else {
                                "अंग्रेज़ी मूवी संवाद लिखें / पेस्ट करें (English Dialogue)"
                            }
                        )
                    },
                    placeholder = {
                        Text("Hero: Stay back! They are coming.\nElena: We can't run anymore, we fight here!")
                    },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("english_script_input")
                )

                // Auto-detect character voice toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "AI पात्रों के अनुसार लिंग/उम्र चुने",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Auto-assign gender & age per character, or lock to style below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = autoDetectVoices,
                        onCheckedChange = { autoDetectVoices = it },
                        enabled = !isProcessing
                    )
                }

                VoiceStyleConfigurator(
                    style = style,
                    onStyleChange = { style = it }
                )

                if (creationState is ProjectCreationState.Processing) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                            Column {
                                Text(
                                    text = creationState.statusHindi,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = creationState.statusEnglish,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isProcessing) {
                        Text("रद्द करें (Cancel)")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (englishScript.isNotBlank() || initialUri == null) {
                                onCreateFromScript(
                                    title,
                                    englishScript,
                                    style,
                                    autoDetectVoices
                                )
                            } else {
                                onCreateFromMedia(
                                    title,
                                    initialUri,
                                    initialFileName,
                                    initialMimeType,
                                    initialDurationMs,
                                    style,
                                    autoDetectVoices
                                )
                            }
                        },
                        enabled = !isProcessing && (initialUri != null || englishScript.isNotBlank()),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("generate_dub_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("हिन्दी में डब करें (Dub in Hindi)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
