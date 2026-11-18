package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.GeminiDubbingService
import com.example.audio.VoiceChangerAudioEngine
import com.example.data.DubbingRepository
import com.example.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.projects.ProjectsScreen
import com.example.ui.studio.StudioScreen
import com.example.ui.theme.SwarDubTheme
import com.example.viewmodel.ProjectsViewModel
import com.example.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_PROJECTS = "projects"
const val ROUTE_STUDIO = "studio/{projectId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent BEFORE rendering Compose UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            SwarDubTheme {
                AppNavigation()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    if (FirebaseApp.getApps(context).isEmpty()) {
        MissingFirebaseConfigScreen()
        return
    }
    val auth: FirebaseAuth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedNavGraph(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun MissingFirebaseConfigScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Firebase Config Missing (google-services.json)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "इस APK बिल्ड में google-services.json शामिल नहीं था। GitHub Secrets में GOOGLE_SERVICES_JSON जोड़कर दोबारा बिल्ड करें।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedNavGraph(
    currentUserId: String,
    userEmail: String?
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_PROJECTS
    ) {
        composable(ROUTE_PROJECTS) {
            val projectsViewModel: ProjectsViewModel = viewModel(
                key = "projects_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ProjectsViewModel(
                            repository = DubbingRepository(db),
                            dubbingService = GeminiDubbingService(),
                            currentUserId = currentUserId
                        )
                    }
                }
            )
            ProjectsScreen(
                viewModel = projectsViewModel,
                userEmail = userEmail,
                onOpenProject = { projectId ->
                    navController.navigate("studio/$projectId")
                }
            )
        }

        composable(
            route = ROUTE_STUDIO,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val studioViewModel: StudioViewModel = viewModel(
                key = "studio_${currentUserId}_$projectId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        StudioViewModel(
                            repository = DubbingRepository(db),
                            dubbingService = GeminiDubbingService(),
                            audioEngine = VoiceChangerAudioEngine(app),
                            currentUserId = currentUserId,
                            projectId = projectId
                        )
                    }
                }
            )
            StudioScreen(
                viewModel = studioViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
