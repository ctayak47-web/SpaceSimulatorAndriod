package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.SpaceSurface
import com.example.ui.theme.SpaceWhite
import kotlinx.coroutines.delay

@Composable
fun CosmosSimulatorScreen(
    viewModel: SimulationViewModel = viewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val timeState by viewModel.timeState.collectAsStateWithLifecycle()
    val lastCollision by viewModel.lastCollision.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(2500L)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(
        enabled = uiState.launchState != null ||
            uiState.activeModal != ActiveModal.NONE ||
            snapshot.selectedTelemetry != null ||
            uiState.onboardingStep != null
    ) {
        when {
            uiState.onboardingStep != null -> viewModel.finishOnboarding()
            uiState.launchState != null -> viewModel.cancelAddBodyMode()
            uiState.activeModal != ActiveModal.NONE -> viewModel.setModal(ActiveModal.NONE)
            snapshot.selectedTelemetry != null -> viewModel.deselectBody()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
    ) {
        SpaceCanvas(
            snapshot = snapshot,
            camera = uiState.camera,
            visualSettings = uiState.visualSettings,
            launchState = uiState.launchState,
            onTransform = viewModel::onCameraTransform,
            onTap = viewModel::onCanvasTap,
            onDoubleTap = viewModel::onCanvasDoubleTap,
            onLaunchStart = viewModel::onLaunchDragStart,
            onLaunchDrag = viewModel::onLaunchDragMove,
            onLaunchEnd = viewModel::onLaunchDragEnd
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopHudBar(
                    simTimeSeconds = snapshot.simTimeSeconds,
                    timeState = timeState,
                    bodyCount = snapshot.heavyBodyCount + snapshot.particleCount,
                    onOpenScenarios = { viewModel.setModal(ActiveModal.SCENARIOS) },
                    onOpenMyScenes = { viewModel.setModal(ActiveModal.MY_SCENES) },
                    onOpenLayers = { viewModel.setModal(ActiveModal.LAYERS) },
                    onReset = viewModel::resetSolarSystem,
                    onOpenHelp = viewModel::reopenOnboarding
                )

                val launch = uiState.launchState
                if (launch != null) {
                    LaunchModeBanner(
                        bodyName = launch.draft.name.ifBlank { launch.draft.preset.titleRu },
                        onCancel = viewModel::cancelAddBodyMode
                    )
                }

                CollisionBanner(
                    event = lastCollision,
                    onDismiss = viewModel::dismissCollisionBanner
                )

                AnimatedVisibility(
                    visible = uiState.statusMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    uiState.statusMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SpaceSurface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, EmeraldAccent),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpaceWhite,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val telemetry = snapshot.selectedTelemetry
                if (telemetry != null && uiState.launchState == null) {
                    InspectorPanel(
                        telemetry = telemetry,
                        isFollowing = uiState.camera.followBodyId == telemetry.id,
                        onToggleFollow = { viewModel.toggleFollowSelected(telemetry.id) },
                        onCenter = { viewModel.centerOnBody(telemetry.id, zoomIfFar = true) },
                        onDetonate = { viewModel.detonateBody(telemetry.id) },
                        onDelete = { viewModel.deleteBody(telemetry.id) },
                        onClose = viewModel::deselectBody,
                        onMassChange = { newMass -> viewModel.updateSelectedMass(telemetry.id, newMass) },
                        onRadiusChange = { newRadius -> viewModel.updateSelectedRadius(telemetry.id, newRadius) },
                        onScaleVelocity = { factor -> viewModel.scaleSelectedVelocity(telemetry.id, factor) },
                        onToggleRings = { viewModel.toggleSelectedRings(telemetry.id) },
                        modifier = Modifier.heightIn(max = 350.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                BottomTimeAndAddBar(
                    timeState = timeState,
                    onToggleReverse = viewModel::toggleReverse,
                    onTogglePause = viewModel::togglePause,
                    onNormalSpeed = viewModel::setNormalSpeed,
                    onSpeedSliderChange = viewModel::setTimeScaleFromSlider,
                    onAddBodyClick = { viewModel.setModal(ActiveModal.SANDBOX_BUILDER) }
                )
            }

            val onboarding = uiState.onboardingStep
            if (onboarding != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    OnboardingOverlay(
                        step = onboarding,
                        onNext = viewModel::nextOnboardingStep,
                        onSkip = viewModel::finishOnboarding
                    )
                }
            }
        }
    }

    when (uiState.activeModal) {
        ActiveModal.NONE -> Unit
        ActiveModal.SCENARIOS -> ScenariosDialog(
            onSelectScenario = viewModel::loadScenario,
            onDismiss = { viewModel.setModal(ActiveModal.NONE) }
        )
        ActiveModal.SANDBOX_BUILDER -> SandboxBuilderDialog(
            draft = uiState.sandboxDraft,
            onSelectPreset = viewModel::selectSandboxPreset,
            onUpdateDraft = viewModel::updateSandboxDraft,
            onLaunch = viewModel::startAddCustomBodyMode,
            onDismiss = { viewModel.setModal(ActiveModal.NONE) }
        )
        ActiveModal.MY_SCENES -> MyScenesDialog(
            scenes = uiState.savedScenes,
            onRequestSaveCurrent = { viewModel.setModal(ActiveModal.SAVE_SCENE_INPUT) },
            onLoadScene = viewModel::loadSavedScene,
            onDeleteScene = viewModel::deleteSavedScene,
            onDismiss = { viewModel.setModal(ActiveModal.NONE) }
        )
        ActiveModal.SAVE_SCENE_INPUT -> SaveSceneNameDialog(
            onConfirm = viewModel::saveCurrentScene,
            onDismiss = { viewModel.setModal(ActiveModal.MY_SCENES) }
        )
        ActiveModal.LAYERS -> LayersDialog(
            settings = uiState.visualSettings,
            onToggleGrid = viewModel::toggleGrid,
            onToggleGravityGrid = viewModel::toggleGravityGrid,
            onToggleTrails = viewModel::toggleTrails,
            onToggleLabels = viewModel::toggleLabels,
            onToggleHabitableZone = viewModel::toggleHabitableZone,
            onToggleVelocityVectors = viewModel::toggleVelocityVectors,
            onDismiss = { viewModel.setModal(ActiveModal.NONE) }
        )
    }
}
