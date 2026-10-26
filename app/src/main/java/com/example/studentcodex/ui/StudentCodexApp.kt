package com.example.studentcodex.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.studentcodex.R
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.di.AppContainer
import com.example.studentcodex.ui.auth.AuthGateScreen
import com.example.studentcodex.ui.auth.ProfileCompletionScreen
import com.example.studentcodex.ui.auth.AuthMode
import com.example.studentcodex.ui.auth.AuthUiState
import com.example.studentcodex.ui.auth.AuthViewModel
import com.example.studentcodex.ui.beacon.BeaconScreen
import com.example.studentcodex.ui.community.CommunityScreen
import com.example.studentcodex.ui.guidance.GuidanceViewModel
import com.example.studentcodex.ui.guidance.overlay.GuidanceOverlay
import com.example.studentcodex.ui.guidance.overlay.GuidanceOverlayController
import com.example.studentcodex.ui.guidance.overlay.GuidanceOverlayMode
import com.example.studentcodex.ui.guidance.overlay.RusshwolfOverlayStore
import com.example.studentcodex.ui.home.HomeScreen
import com.example.studentcodex.ui.profile.ProfileScreen
import com.example.studentcodex.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import com.russhwolf.settings.Settings

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home),
    BEACON("beacon", R.string.nav_beacon, Icons.Rounded.TrackChanges),
    COMMUNITY("community", R.string.nav_community, Icons.Rounded.Forum),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings),
}

/**
 * Route of the pushed Profile page. Settings is the bottom-bar tab; the
 * profile itself is one tap deeper (viewed from Settings), with Back
 * returning to the Settings tab.
 */
private const val PROFILE_ROUTE = "profile"

/**
 * App root. Authentication is the gate before the main app: a restored or
 * newly created Supabase session (or the explicit demo-guest bypass) enters
 * the main navigation; signing out returns here.
 */
@Composable
fun StudentCodexApp(container: AppContainer) {
    val authViewModel: AuthViewModel = viewModel {
        AuthViewModel(
            container.authRepository,
            container.googleAuthManager,
            container.schoolDirectoryRepository,
        )
    }
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    // The school registry feeds both the sign-up form and the Google
    // completion form, so it lives beside (not inside) the auth states.
    val directoryState by authViewModel.schoolDirectory.collectAsStateWithLifecycle()

    when (val s = authState) {
        AuthUiState.CheckingSession -> SessionRestoreScreen()
        is AuthUiState.SignedOut -> AuthGateScreen(
            state = s,
            directory = directoryState,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onFirstNameChange = authViewModel::onFirstNameChange,
            onLastNameChange = authViewModel::onLastNameChange,
            onConfirmPasswordChange = authViewModel::onConfirmPasswordChange,
            onSchoolProgramChange = authViewModel::onSchoolProgramChange,
            onSchoolYearLevelChange = authViewModel::onSchoolYearLevelChange,
            onSchoolSectionChange = authViewModel::onSchoolSectionChange,
            onSchoolCampusChange = authViewModel::onSchoolCampusChange,
            onCourseCodeInputChange = authViewModel::onCourseCodeInputChange,
            onAddCourseCode = authViewModel::addCourseCode,
            onRemoveCourseCode = authViewModel::removeCourseCode,
            onRetryDirectory = authViewModel::retrySchoolDirectory,
            onSwitchMode = authViewModel::switchMode,
            onDismissConfirmation = authViewModel::dismissConfirmation,
            onSubmit = {
                if (s.mode == AuthMode.SIGN_IN) authViewModel.signIn() else authViewModel.signUp()
            },
            onGoogleSignIn = authViewModel::signInWithGoogle,
            onDismissGoogleSetup = authViewModel::dismissGoogleSetup,
            onExploreAsGuest = authViewModel::signInAsGuest,
        )
        is AuthUiState.CompletingProfile -> ProfileCompletionScreen(
            state = s,
            directory = directoryState,
            onFirstNameChange = authViewModel::onProfileFirstNameChange,
            onLastNameChange = authViewModel::onProfileLastNameChange,
            onPasswordChange = authViewModel::onProfilePasswordChange,
            onConfirmPasswordChange = authViewModel::onProfileConfirmPasswordChange,
            onSchoolProgramChange = authViewModel::onSchoolProgramChange,
            onSchoolYearLevelChange = authViewModel::onSchoolYearLevelChange,
            onSchoolSectionChange = authViewModel::onSchoolSectionChange,
            onSchoolCampusChange = authViewModel::onSchoolCampusChange,
            onCourseCodeInputChange = authViewModel::onCourseCodeInputChange,
            onAddCourseCode = authViewModel::addCourseCode,
            onRemoveCourseCode = authViewModel::removeCourseCode,
            onRetryDirectory = authViewModel::retrySchoolDirectory,
            onSubmit = authViewModel::submitProfileCompletion,
            onCancel = authViewModel::cancelProfileCompletion,
        )
        is AuthUiState.SignedIn -> StudentCodexMain(
            container = container,
            session = s.session,
            onSignOut = authViewModel::signOut,
        )
    }
}

/** Quiet splash while the persisted Supabase session is being restored. */
@Composable
private fun SessionRestoreScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

/** The main app behind the auth gate: bottom navigation + top destinations. */
@Composable
private fun StudentCodexMain(
    container: AppContainer,
    session: SessionInfo,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // ONE guidance conversation for the whole signed-in session: every
    // surface that shows the assistant - the chat head's card, the edge
    // handle's card, the Home "Ask a study question" button - shares the
    // same transcript.
    val guidanceViewModel: GuidanceViewModel = viewModel {
        GuidanceViewModel(container.guidanceEngine)
    }
    // Floating assistant presentation (chat head vs edge panel, docked
    // positions, panel open/closed), persisted through the Settings store.
    val overlayController: GuidanceOverlayController = viewModel {
        GuidanceOverlayController(RusshwolfOverlayStore(Settings()))
    }
    val overlayMode by overlayController.mode.collectAsStateWithLifecycle()

    // When the signed-in session goes away, the shared transcript must go
    // with it - questions belong to the account that asked them.
    DisposableEffect(Unit) {
        onDispose { guidanceViewModel.clearConversation() }
    }

    // Auto Classroom sync: when the student turned the toggle on, every
    // app open (and return from background) silently re-imports new
    // Google Classroom work - the import is idempotent (upsert by source
    // key), so every open is safe. No sheet, no spinner: rows simply
    // surface through the Beacon on-resume refresh and the connection
    // card "last sync" note.
    val appActivity = LocalContext.current as? Activity
    val appLifecycleOwner = LocalLifecycleOwner.current
    val autoSyncScope = rememberCoroutineScope()
    DisposableEffect(appLifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                autoSyncScope.launch {
                    container.classroomAutoSyncer.runIfDue(appActivity)
                }
            }
        }
        appLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { appLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    TopDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTop(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            // Full innerPadding (top inset + bottom bar). The app draws edge to
            // edge, so dropping the top padding would slide screen headers under
            // the status bar; screens then only add their own content spacing.
            NavHost(
                navController = navController,
                startDestination = TopDestination.HOME.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(TopDestination.HOME.route) {
                    HomeScreen(
                        container = container,
                        onOpenBeacon = { navController.navigateToTop(TopDestination.BEACON) },
                        onOpenCommunity = { navController.navigateToTop(TopDestination.COMMUNITY) },
                        onAskGuidance = {
                            // No page push: this opens the floating
                            // assistant's chat panel - the SAME panel the
                            // chat head / edge handle opens - so guidance is
                            // one consistent surface everywhere.
                            overlayController.expand()
                        },
                    )
                }
                composable(TopDestination.BEACON.route) {
                    BeaconScreen(container = container, session = session)
                }
                composable(TopDestination.COMMUNITY.route) {
                    CommunityScreen(container = container, session = session)
                }
                composable(TopDestination.SETTINGS.route) {
                    // The floating overlay stays visible over Settings, so
                    // switching the assistant style previews it live.
                    SettingsScreen(
                        container = container,
                        session = session,
                        overlayMode = overlayMode,
                        onSelectOverlayMode = overlayController::selectMode,
                        onOpenProfile = {
                            // Pushed (not a tab swap): Back from the Profile
                            // page returns to the Settings tab.
                            navController.navigate(PROFILE_ROUTE) { launchSingleTop = true }
                        },
                        // Sign-out lives on the Settings page itself now
                        // (after all of its operation buttons), so the auth
                        // gate callback is passed straight to the tab.
                        onSignOut = onSignOut,
                    )
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen(
                        container = container,
                        session = session,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        // The floating guidance assistant rides above every page. Tapping
        // the chat head or the edge handle - or "Ask a study question" on
        // Home - opens the one shared guidance chat panel.
        GuidanceOverlay(
            controller = overlayController,
            viewModel = guidanceViewModel,
        )
    }
}

/** Single-top navigation between top destinations: no duplicate back stack entries. */
private fun NavHostController.navigateToTop(destination: TopDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

