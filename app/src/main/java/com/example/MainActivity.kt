package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.LvlLoadingOverlay
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.TemplateFillScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.LavelleTheme
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LavelleViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_TEMPLATE_FILL = "template_fill"
private const val ROUTE_EDITOR = "editor"

private enum class PickMode {
    BLANK_PROJECT,
    TEMPLATE_BATCH,
    ADD_TO_EDITOR,
    REPLACE_CLIP,
    TEMPLATE_SINGLE_SLOT
}

class MainActivity : ComponentActivity() {

    private val viewModel: LavelleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        handleIncomingIntent(intent)

        setContent {
            LavelleTheme {
                LavelleRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }
        if (uri != null) {
            viewModel.openLvlFromUri(uri)
        }
    }
}

@Composable
private fun LavelleRootApp(viewModel: LavelleViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val pendingTemplate by viewModel.pendingTemplate.collectAsStateWithLifecycle()
    val pendingSlotImages by viewModel.pendingSlotImages.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val selectedClipIndex by viewModel.selectedClipIndex.collectAsStateWithLifecycle()
    val lvlLoadingState by viewModel.lvlLoadingState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val navEditorSignal by viewModel.navigateToEditorSignal.collectAsStateWithLifecycle()
    val navFillSignal by viewModel.navigateToTemplateFillSignal.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    var pendingPickMode by remember { mutableStateOf(PickMode.BLANK_PROJECT) }
    var pendingTargetSlotIndex by remember { mutableIntStateOf(0) }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            when (pendingPickMode) {
                PickMode.BLANK_PROJECT -> viewModel.createBlankProjectFromPhotos(uris)
                PickMode.TEMPLATE_BATCH -> viewModel.fillTemplateBatch(uris)
                PickMode.ADD_TO_EDITOR -> viewModel.addPhotosToProject(uris)
                else -> Unit
            }
        }
    }

    val singlePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            when (pendingPickMode) {
                PickMode.TEMPLATE_SINGLE_SLOT -> viewModel.fillTemplateSlot(pendingTargetSlotIndex, uri)
                PickMode.REPLACE_CLIP -> viewModel.replaceClipPhoto(pendingTargetSlotIndex, uri)
                else -> Unit
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomMusic(uri)
        }
    }

    val lvlFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openLvlFromUri(uri)
        }
    }

    LaunchedEffect(navFillSignal) {
        if (navFillSignal > 0 && pendingTemplate != null) {
            navController.navigate(ROUTE_TEMPLATE_FILL) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navEditorSignal) {
        if (navEditorSignal > 0 && activeProject != null) {
            navController.navigate(ROUTE_EDITOR) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeToast()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
    ) {
        Scaffold(
            containerColor = StudioObsidian,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(ROUTE_HOME) {
                    HomeHubScreen(
                        projects = projects,
                        onSelectTemplate = { tpl -> viewModel.startTemplateFlow(tpl) },
                        onNewBlankMontage = {
                            pendingPickMode = PickMode.BLANK_PROJECT
                            multiPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onOpenLvlFile = {
                            lvlFilePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenSavedProject = { proj -> viewModel.openSavedProject(proj) },
                        onDeleteSavedProject = { proj -> viewModel.deleteSavedProject(proj) }
                    )
                }

                composable(ROUTE_TEMPLATE_FILL) {
                    val tpl = pendingTemplate
                    if (tpl != null) {
                        TemplateFillScreen(
                            template = tpl,
                            slotImages = pendingSlotImages,
                            onBack = { navController.popBackStack() },
                            onPickMultiplePhotos = {
                                pendingPickMode = PickMode.TEMPLATE_BATCH
                                multiPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickSingleSlotPhoto = { idx ->
                                pendingPickMode = PickMode.TEMPLATE_SINGLE_SLOT
                                pendingTargetSlotIndex = idx
                                singlePhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onLaunchEditor = { viewModel.launchEditorFromPendingTemplate() }
                        )
                    }
                }

                composable(ROUTE_EDITOR) {
                    val proj = activeProject
                    if (proj != null) {
                        EditorScreen(
                            project = proj,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            selectedClipIndex = selectedClipIndex,
                            exportState = exportState,
                            onBack = {
                                viewModel.pausePlayback()
                                navController.popBackStack(ROUTE_HOME, inclusive = false)
                            },
                            onTogglePlay = { viewModel.togglePlayback() },
                            onSeek = { ms -> viewModel.seekTo(ms) },
                            onSelectClip = { idx -> viewModel.selectClip(idx) },
                            onRenameProject = { name -> viewModel.renameProject(name) },
                            onSetAspectRatio = { opt -> viewModel.setAspectRatio(opt) },
                            onAddPhotos = {
                                pendingPickMode = PickMode.ADD_TO_EDITOR
                                multiPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onReplaceClipPhoto = { idx ->
                                pendingPickMode = PickMode.REPLACE_CLIP
                                pendingTargetSlotIndex = idx
                                singlePhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onUpdateClip = { idx, transform -> viewModel.updateClip(idx, transform) },
                            onApplyFilterToAll = { filter -> viewModel.applyFilterToAll(filter) },
                            onApplyMotionToAll = { motion -> viewModel.applyMotionToAll(motion) },
                            onMoveClip = { idx, dir -> viewModel.moveClip(idx, dir) },
                            onDuplicateClip = { idx -> viewModel.duplicateClip(idx) },
                            onDeleteClip = { idx -> viewModel.deleteClip(idx) },
                            onAddText = { txt, anim, color -> viewModel.addTextOverlay(txt, anim, color) },
                            onDeleteText = { id -> viewModel.deleteTextOverlay(id) },
                            onSelectBuiltInMusic = { track -> viewModel.selectBuiltInMusic(track) },
                            onPickMusicFromPhone = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            onUpdateMusicSettings = { vol, fin, fout ->
                                viewModel.updateMusicSettings(vol, fin, fout, 0L)
                            },
                            onRemoveMusic = { viewModel.removeMusic() },
                            onExportMp4 = { name -> viewModel.exportMp4(name) },
                            onExportLvl = { name -> viewModel.exportLvl(name) },
                            onResetExportState = { viewModel.resetExportState() },
                            onShareFile = { uriStr, mime ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Faylni ulashish"))
                            }
                        )
                    }
                }
            }
        }

        LvlLoadingOverlay(
            state = lvlLoadingState,
            onDismissError = { viewModel.dismissLvlError() }
        )
    }
}

@Composable
private fun HomeHubScreen(
    projects: List<com.example.model.MontageProject>,
    onSelectTemplate: (com.example.model.TemplateSpec) -> Unit,
    onNewBlankMontage: () -> Unit,
    onOpenLvlFile: () -> Unit,
    onOpenSavedProject: (com.example.model.MontageProject) -> Unit,
    onDeleteSavedProject: (com.example.model.MontageProject) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val isMediumOrExpanded = maxWidth >= 600.dp

        if (isMediumOrExpanded) {
            Scaffold(
                containerColor = StudioObsidian,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavigationRailItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Rounded.AutoAwesomeMosaic else Icons.Outlined.AutoAwesomeMosaic,
                                    contentDescription = "Shablonlar"
                                )
                            },
                            label = { Text("Shablonlar") },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric,
                                indicatorColor = StudioSurfaceElevated,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                        NavigationRailItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = "Loyihalar"
                                )
                            },
                            label = { Text("Loyihalar") },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric,
                                indicatorColor = StudioSurfaceElevated,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (selectedTab == 0) {
                            TemplatesScreen(
                                isExpandedWidth = isExpanded,
                                onSelectTemplate = onSelectTemplate,
                                onNewBlankMontage = onNewBlankMontage,
                                onOpenLvlFile = onOpenLvlFile
                            )
                        } else {
                            ProjectsScreen(
                                projects = projects,
                                onOpenProject = onOpenSavedProject,
                                onDeleteProject = onDeleteSavedProject,
                                onNewBlankMontage = onNewBlankMontage,
                                onOpenLvlFile = onOpenLvlFile
                            )
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = StudioObsidian,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(containerColor = StudioSurface) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Rounded.AutoAwesomeMosaic else Icons.Outlined.AutoAwesomeMosaic,
                                    contentDescription = "Shablonlar"
                                )
                            },
                            label = { Text("Shablonlar") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric,
                                indicatorColor = StudioSurfaceElevated,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = "Loyihalar"
                                )
                            },
                            label = { Text("Loyihalar") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric,
                                indicatorColor = StudioSurfaceElevated,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (selectedTab == 0) {
                        TemplatesScreen(
                            isExpandedWidth = false,
                            onSelectTemplate = onSelectTemplate,
                            onNewBlankMontage = onNewBlankMontage,
                            onOpenLvlFile = onOpenLvlFile
                        )
                    } else {
                        ProjectsScreen(
                            projects = projects,
                            onOpenProject = onOpenSavedProject,
                            onDeleteProject = onDeleteSavedProject,
                            onNewBlankMontage = onNewBlankMontage,
                            onOpenLvlFile = onOpenLvlFile
                        )
                    }
                }
            }
        }
    }
}
