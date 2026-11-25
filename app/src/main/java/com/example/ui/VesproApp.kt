package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.core.LockMode
import com.example.core.VaultLockManager
import com.example.core.VesproFirestoreProvider
import com.example.data.DiaryRepository
import com.example.data.GeminiDiaryAssistant
import com.example.data.SyncPhase
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.PdfExportResultSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EntryDetailOverlay
import com.example.ui.screens.EveningCaptureScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.JournalFeedScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.ProfileQuizScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.state.DiaryViewModel
import com.example.ui.state.UiState
import com.example.ui.theme.VesproTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private object VesproRoutes {
    const val CAPTURE = "sera"
    const val JOURNAL = "diario"
    const val INSIGHTS = "sintesi"
    const val SECURITY = "sicurezza"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(
        route = VesproRoutes.CAPTURE,
        label = "Scrivi",
        selectedIcon = Icons.Rounded.EditNote,
        unselectedIcon = Icons.Outlined.EditNote,
        testTag = "nav_sera"
    ),
    NavDestination(
        route = VesproRoutes.JOURNAL,
        label = "Diario",
        selectedIcon = Icons.Rounded.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories,
        testTag = "nav_diario"
    ),
    NavDestination(
        route = VesproRoutes.INSIGHTS,
        label = "Sintesi",
        selectedIcon = Icons.Rounded.Insights,
        unselectedIcon = Icons.Outlined.Insights,
        testTag = "nav_sintesi"
    ),
    NavDestination(
        route = VesproRoutes.SECURITY,
        label = "Sicurezza",
        selectedIcon = Icons.Rounded.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        testTag = "nav_sicurezza"
    )
)

@Composable
fun VesproRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = LocalContext.current
    val lockManager = remember(context) { VaultLockManager(context) }

    val user = currentUser
    var isDarkTheme by remember(user?.uid) {
        mutableStateOf(
            if (user != null) lockManager.isDarkThemePreferred(user.uid) else true
        )
    }

    VesproTheme(darkTheme = isDarkTheme) {
        if (user == null) {
            AuthScreen(onAuthSuccess = {})
        } else {
            val uid = user.uid
            key(uid) {
                AuthenticatedVaultShell(
                    uid = uid,
                    userEmail = user.email,
                    lockManager = lockManager,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = { dark ->
                        isDarkTheme = dark
                        lockManager.setDarkThemePreferred(uid, dark)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedVaultShell(
    uid: String,
    userEmail: String?,
    lockManager: VaultLockManager,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var lockMode by remember(uid) { mutableStateOf(lockManager.getLockMode(uid)) }
    var autoLockOnBg by remember(uid) { mutableStateOf(lockManager.isAutoLockOnBackground(uid)) }
    var isUnlocked by remember(uid) { mutableStateOf(lockMode == LockMode.NONE) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, lockMode, autoLockOnBg) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && lockMode != LockMode.NONE && autoLockOnBg) {
                isUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isUnlocked && lockMode != LockMode.NONE) {
        LockScreen(
            lockMode = lockMode,
            onVerifyPin = { pin -> lockManager.verifyPin(uid, pin) },
            onVerifyPattern = { pattern -> lockManager.verifyPattern(uid, pattern) },
            onUnlockSuccess = { isUnlocked = true },
            onSignOutRequested = {
                signOut(credentialManager = credentialManager, scope = scope)
            }
        )
        return
    }

    val viewModel: DiaryViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = VesproFirestoreProvider.getInstance(app)
                DiaryViewModel(
                    repository = DiaryRepository(db),
                    geminiAssistant = GeminiDiaryAssistant(),
                    currentUserId = uid
                )
            }
        }
    )

    val entriesState by viewModel.entriesState.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val summariesState by viewModel.summariesState.collectAsStateWithLifecycle()
    val aphorismsState by viewModel.aphorismsState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val isQuizSaving by viewModel.isProfileQuizSaving.collectAsStateWithLifecycle()
    val aiTaskState by viewModel.aiTaskState.collectAsStateWithLifecycle()
    val generatedAphorism by viewModel.generatedAphorism.collectAsStateWithLifecycle()
    val pdfExportState by viewModel.pdfExportState.collectAsStateWithLifecycle()
    val draftTranscript by viewModel.draftTranscript.collectAsStateWithLifecycle()
    val selectedEntryId by viewModel.selectedEntryId.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var showProfileQuizManually by remember(uid) { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val loadedProfile = (profileState as? UiState.Success)?.data
    val shouldShowInitialQuiz = profileState is UiState.Success &&
        (loadedProfile == null || (!loadedProfile.onboardingCompleted && !loadedProfile.onboardingSkipped))

    if (showProfileQuizManually || shouldShowInitialQuiz) {
        ProfileQuizScreen(
            initialProfile = loadedProfile,
            isSaving = isQuizSaving,
            isEditMode = showProfileQuizManually,
            onSaveProfile = { updated ->
                viewModel.saveUserProfile(updated) {
                    showProfileQuizManually = false
                }
            },
            onSkip = {
                if (showProfileQuizManually) {
                    showProfileQuizManually = false
                } else {
                    viewModel.skipProfileQuiz {
                        showProfileQuizManually = false
                    }
                }
            }
        )
        return
    }

    val entriesList = (entriesState as? UiState.Success)?.data.orEmpty()
    val latestEntry = entriesList.firstOrNull()
    val selectedEntry = remember(entriesList, selectedEntryId) {
        entriesList.firstOrNull { it.id == selectedEntryId }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: VesproRoutes.CAPTURE

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NightlightRound,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "  ${stringResource(R.string.app_name)}",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            val (syncIcon, syncTint, syncDescription) = when (cloudSyncStatus.phase) {
                                SyncPhase.SYNCED -> Triple(
                                    Icons.Rounded.CloudDone,
                                    MaterialTheme.colorScheme.tertiary,
                                    "Sincronizzato in cloud. Tocca per verificare ora"
                                )
                                SyncPhase.SYNCING -> Triple(
                                    Icons.Rounded.CloudSync,
                                    MaterialTheme.colorScheme.primary,
                                    "Sincronizzazione cloud in corso"
                                )
                                SyncPhase.PENDING_UPLOAD -> Triple(
                                    Icons.Rounded.CloudUpload,
                                    MaterialTheme.colorScheme.primary,
                                    "Modifiche in attesa di caricamento cloud. Tocca per sincronizzare"
                                )
                                SyncPhase.OFFLINE_CACHE -> Triple(
                                    Icons.Rounded.CloudOff,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    "Modalità offline, sincronizzazione automatica appena torni online. Tocca per riprovare"
                                )
                                SyncPhase.ERROR -> Triple(
                                    Icons.Rounded.CloudOff,
                                    MaterialTheme.colorScheme.error,
                                    "Errore di sincronizzazione cloud. Tocca per riprovare"
                                )
                            }
                            IconButton(
                                onClick = viewModel::syncNow,
                                modifier = Modifier.testTag("topbar_sync_status")
                            ) {
                                Icon(
                                    imageVector = syncIcon,
                                    contentDescription = syncDescription,
                                    tint = syncTint
                                )
                            }
                            if (lockMode != LockMode.NONE) {
                                IconButton(
                                    onClick = { isUnlocked = false },
                                    modifier = Modifier.testTag("topbar_lock_now")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Blocca il diario"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            Destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = VesproRoutes.CAPTURE,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    ) {
                        composable(VesproRoutes.CAPTURE) {
                            EveningCaptureScreen(
                                draftTranscript = draftTranscript,
                                aiTaskState = aiTaskState,
                                latestEntry = latestEntry,
                                onDraftChange = viewModel::updateDraftTranscript,
                                onVoiceSegmentCommitted = viewModel::appendVoiceSegment,
                                onClearDraft = viewModel::clearDraft,
                                onSaveWithGemini = viewModel::saveAndReflectWithGemini,
                                onSaveRawOnly = viewModel::saveRawWithoutAi,
                                onOpenEntry = { id -> viewModel.selectEntry(id) },
                                onDismissError = viewModel::dismissAiError
                            )
                        }
                        composable(VesproRoutes.JOURNAL) {
                            JournalFeedScreen(
                                entriesState = entriesState,
                                cloudSyncStatus = cloudSyncStatus,
                                onOpenEntry = { id -> viewModel.selectEntry(id) },
                                onExportCollectionPdf = { entries, title, slug ->
                                    viewModel.exportCollectionToPdf(context, entries, title, slug)
                                }
                            )
                        }
                        composable(VesproRoutes.INSIGHTS) {
                            InsightsScreen(
                                entriesState = entriesState,
                                summariesState = summariesState,
                                aphorismsState = aphorismsState,
                                generatedAphorism = generatedAphorism,
                                isDefaultBedtime = viewModel.isCurrentlyBedtimeOrEvening(),
                                aiTaskState = aiTaskState,
                                onGenerateWeeklySummary = viewModel::generateWeeklySummary,
                                onDeleteSummary = viewModel::deleteWeeklySummary,
                                onGenerateAphorism = viewModel::generateStandaloneAphorism,
                                onSaveAphorismToRegister = viewModel::saveGeneratedAphorismToRegister,
                                onDeleteAphorism = viewModel::deleteAphorism,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        composable(VesproRoutes.SECURITY) {
                            SecuritySettingsScreen(
                                userEmail = userEmail,
                                userProfile = loadedProfile,
                                cloudSyncStatus = cloudSyncStatus,
                                lockMode = lockMode,
                                autoLockOnBackground = autoLockOnBg,
                                isDarkTheme = isDarkTheme,
                                onSyncNow = viewModel::syncNow,
                                onOpenProfileQuiz = { showProfileQuizManually = true },
                                onSetPin = { pin ->
                                    lockManager.setPin(uid, pin)
                                    lockMode = lockManager.getLockMode(uid)
                                },
                                onSetPattern = { nodes ->
                                    lockManager.setPattern(uid, nodes)
                                    lockMode = lockManager.getLockMode(uid)
                                },
                                onVerifyCurrentPin = { pin -> lockManager.verifyPin(uid, pin) },
                                onVerifyCurrentPattern = { nodes -> lockManager.verifyPattern(uid, nodes) },
                                onDisableLock = {
                                    lockManager.disableLock(uid)
                                    lockMode = LockMode.NONE
                                },
                                onToggleAutoLock = { enabled ->
                                    autoLockOnBg = enabled
                                    lockManager.setAutoLockOnBackground(uid, enabled)
                                },
                                onToggleDarkTheme = onToggleDarkTheme,
                                onLockNow = { isUnlocked = false },
                                onSignOut = {
                                    signOut(credentialManager = credentialManager, scope = scope)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (selectedEntry != null) {
            EntryDetailOverlay(
                entry = selectedEntry,
                aiTaskState = aiTaskState,
                generatedAphorism = generatedAphorism,
                isDefaultBedtime = viewModel.isCurrentlyBedtimeOrEvening(),
                onClose = { viewModel.selectEntry(null) },
                onAnalyzeEntry = viewModel::analyzeExistingEntry,
                onRefreshQuestion = viewModel::refreshReflectionQuestion,
                onSaveAnswer = viewModel::saveReflectionAnswer,
                onGenerateAphorism = viewModel::generateAphorismForEntry,
                onSaveAphorismToRegister = viewModel::saveGeneratedAphorismToRegister,
                onExportPdf = { entry -> viewModel.exportSingleEntryToPdf(context, entry) },
                onDeleteEntry = viewModel::deleteEntry
            )
        }

        PdfExportResultSheet(
            pdfExportState = pdfExportState,
            onDismiss = viewModel::clearPdfExportState,
            onSavedSuccess = {
                viewModel.clearPdfExportState()
                viewModel.showMessage("PDF salvato sul dispositivo.")
            },
            onSaveError = { msg ->
                viewModel.showMessage(msg)
            }
        )
    }
}
