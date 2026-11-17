package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.physics.CollisionEvent
import com.example.physics.PhysicsEngine
import com.example.physics.PhysicsUnits
import com.example.physics.SimulationSnapshot
import com.example.render.CameraState
import com.example.scenarios.CustomBodyDraft
import com.example.scenarios.SandboxPreset
import com.example.scenarios.ScenarioRepository
import com.example.scenarios.ScenarioType
import com.example.scenarios.SolarSystemFactory
import com.example.storage.SavedSceneMetadata
import com.example.storage.SceneStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

enum class ActiveModal {
    NONE,
    SCENARIOS,
    MY_SCENES,
    SAVE_SCENE_INPUT,
    SANDBOX_BUILDER,
    LAYERS
}

data class LaunchDragState(
    val draft: CustomBodyDraft,
    val startX: Float? = null,
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val speedLabel: String? = null,
    val predictedPointsWorld: FloatArray? = null
)

data class VisualSettings(
    val showGrid: Boolean = true,
    val showTrails: Boolean = true,
    val showLabels: Boolean = true,
    val showHabitableZone: Boolean = true,
    val showVelocityVectors: Boolean = false
)

data class SimulatorUiState(
    val camera: CameraState = CameraState(),
    val visualSettings: VisualSettings = VisualSettings(),
    val activeModal: ActiveModal = ActiveModal.NONE,
    val launchState: LaunchDragState? = null,
    val sandboxDraft: CustomBodyDraft = CustomBodyDraft(SandboxPreset.ROCKY_PLANET),
    val savedScenes: List<SavedSceneMetadata> = emptyList(),
    val onboardingStep: Int? = null,
    val statusMessage: String? = null
)

class SimulationViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PhysicsEngine()
    private val storage = SceneStorageRepository(application)

    val snapshot: StateFlow<SimulationSnapshot> = engine.snapshot
    val timeState: StateFlow<PhysicsEngine.TimeControlState> = engine.timeState

    // Переменная-множитель скорости времени: 0.0 (пауза) .. 1_000_000.0x
    val timeScale: StateFlow<Double> = engine.timeState
        .map { it.timeScale }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100_000.0)

    val lastCollision: StateFlow<CollisionEvent?> = engine.lastCollision

    private val _uiState = MutableStateFlow(
        SimulatorUiState(
            onboardingStep = if (storage.isOnboardingCompleted()) null else 0
        )
    )
    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()

    private val launchVelocityPerPx = 180.0

    init {
        loadScenario(ScenarioType.SOLAR_SYSTEM)
        engine.start()
        refreshSavedScenes()

        viewModelScope.launch {
            engine.snapshot.collect { snap ->
                val followId = _uiState.value.camera.followBodyId ?: return@collect
                val target = snap.bodies.firstOrNull { !it.isLightweight && it.id == followId }
                if (target != null) {
                    _uiState.update { state ->
                        state.copy(
                            camera = state.camera.copy(
                                centerX = target.x,
                                centerY = target.y
                            )
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(camera = state.camera.copy(followBodyId = null))
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }

    fun loadScenario(type: ScenarioType) {
        val setup = ScenarioRepository.buildScenario(type)
        engine.loadBodies(
            newBodies = setup.bodies,
            initialTimeScale = setup.timeScale,
            startPaused = false,
            initialSelectedId = setup.focusBodyId
        )
        _uiState.update { state ->
            state.copy(
                camera = CameraState(
                    centerX = setup.cameraCenterX,
                    centerY = setup.cameraCenterY,
                    halfWidthMeters = setup.halfWidthMeters,
                    followBodyId = setup.focusBodyId
                ),
                activeModal = ActiveModal.NONE,
                launchState = null
            )
        }
    }

    fun resetSolarSystem() {
        loadScenario(ScenarioType.SOLAR_SYSTEM)
        showToast("Солнечная система сброшена")
    }

    // Управление временем (0 = пауза, до 1 000 000x через логарифмическую шкалу)
    fun togglePause() = engine.togglePause()
    fun toggleReverse() = engine.toggleReverse()

    fun setTimeScale(scale: Double) {
        engine.setTimeScale(scale)
    }

    fun setNormalSpeed() {
        engine.setReversed(false)
        engine.setTimeScale(1.0)
    }

    fun setTimeScaleFromSlider(sliderValue: Float) {
        engine.setTimeScale(PhysicsUnits.sliderToTimeScale(sliderValue))
    }

    // Управление камерой
    fun onCameraTransform(
        panX: Float,
        panY: Float,
        zoomFactor: Float,
        centroidX: Float,
        centroidY: Float,
        viewWidthPx: Float,
        viewHeightPx: Float
    ) {
        if (viewWidthPx <= 1f || viewHeightPx <= 1f) return
        _uiState.update { state ->
            var cam = state.camera
            val isManualPan = kotlin.math.abs(panX) > 1.5f || kotlin.math.abs(panY) > 1.5f
            if (isManualPan && cam.followBodyId != null && zoomFactor in 0.985f..1.015f) {
                cam = cam.copy(followBodyId = null)
            }

            val mppBefore = cam.metersPerPixel(viewWidthPx)
            val worldAnchorX = cam.screenToWorldX(centroidX, viewWidthPx)
            val worldAnchorY = cam.screenToWorldY(centroidY, viewWidthPx, viewHeightPx)

            val newHalfWidth = (cam.halfWidthMeters / zoomFactor.toDouble())
                .coerceIn(CameraState.MIN_HALF_WIDTH_M, CameraState.MAX_HALF_WIDTH_M)

            val newMpp = (newHalfWidth * 2.0) / viewWidthPx.toDouble()
            val newCenterX = if (cam.followBodyId != null) {
                cam.centerX
            } else {
                worldAnchorX - (centroidX - viewWidthPx * 0.5) * newMpp - panX * mppBefore
            }
            val newCenterY = if (cam.followBodyId != null) {
                cam.centerY
            } else {
                worldAnchorY - (centroidY - viewHeightPx * 0.5) * newMpp - panY * mppBefore
            }

            state.copy(
                camera = cam.copy(
                    centerX = newCenterX,
                    centerY = newCenterY,
                    halfWidthMeters = newHalfWidth
                )
            )
        }
    }

    fun onCanvasTap(screenX: Float, screenY: Float, viewWidthPx: Float, viewHeightPx: Float) {
        val hitId = findBodyAtScreen(screenX, screenY, viewWidthPx, viewHeightPx)
        engine.selectBody(hitId)
        if (hitId == null) {
            _uiState.update { it.copy(camera = it.camera.copy(followBodyId = null)) }
        }
    }

    fun onCanvasDoubleTap(screenX: Float, screenY: Float, viewWidthPx: Float, viewHeightPx: Float) {
        val hitId = findBodyAtScreen(screenX, screenY, viewWidthPx, viewHeightPx)
        if (hitId != null) {
            engine.selectBody(hitId)
            centerOnBody(hitId, zoomIfFar = true)
        } else {
            val snap = snapshot.value
            val targetX = if (snap.hasStar) snap.nearestStarX else 0.0
            val targetY = if (snap.hasStar) snap.nearestStarY else 0.0
            _uiState.update { state ->
                state.copy(
                    camera = state.camera.copy(
                        centerX = targetX,
                        centerY = targetY,
                        followBodyId = null
                    )
                )
            }
        }
    }

    private fun findBodyAtScreen(
        screenX: Float,
        screenY: Float,
        viewWidthPx: Float,
        viewHeightPx: Float
    ): Long? {
        val snap = snapshot.value
        val cam = _uiState.value.camera
        var bestId: Long? = null
        var bestDist = Float.MAX_VALUE

        for (b in snap.bodies) {
            if (b.isLightweight) continue
            val sx = cam.worldToScreenX(b.x, viewWidthPx)
            val sy = cam.worldToScreenY(b.y, viewWidthPx, viewHeightPx)
            val dx = sx - screenX
            val dy = sy - screenY
            val dist = sqrt(dx * dx + dy * dy)
            val physPx = cam.metersToPx(b.radiusM, viewWidthPx)
            val hitRadius = max(physPx + 22f, 46f)
            if (dist <= hitRadius && dist < bestDist) {
                bestDist = dist
                bestId = b.id
            }
        }
        return bestId
    }

    fun centerOnBody(bodyId: Long, zoomIfFar: Boolean = false) {
        val body = snapshot.value.bodies.firstOrNull { it.id == bodyId } ?: return
        _uiState.update { state ->
            val targetZoom = if (zoomIfFar) {
                minOf(
                    state.camera.halfWidthMeters,
                    max(body.radiusM * 25.0, PhysicsUnits.AU_METERS * 0.18)
                )
            } else {
                state.camera.halfWidthMeters
            }
            state.copy(
                camera = state.camera.copy(
                    centerX = body.x,
                    centerY = body.y,
                    halfWidthMeters = targetZoom
                )
            )
        }
    }

    fun toggleFollowSelected(bodyId: Long) {
        _uiState.update { state ->
            val newFollow = if (state.camera.followBodyId == bodyId) null else bodyId
            val target = snapshot.value.bodies.firstOrNull { it.id == bodyId }
            state.copy(
                camera = state.camera.copy(
                    followBodyId = newFollow,
                    centerX = target?.x ?: state.camera.centerX,
                    centerY = target?.y ?: state.camera.centerY
                )
            )
        }
    }

    // Редактирование параметров выбранного тела на лету
    fun updateSelectedMass(bodyId: Long, newMassKg: Double) = engine.updateBodyMass(bodyId, newMassKg)
    fun updateSelectedRadius(bodyId: Long, newRadiusM: Double) = engine.updateBodyRadius(bodyId, newRadiusM)
    fun toggleSelectedRings(bodyId: Long) = engine.toggleBodyRings(bodyId)
    fun scaleSelectedVelocity(bodyId: Long, factor: Double) = engine.scaleBodyVelocity(bodyId, factor)

    fun deleteBody(bodyId: Long) {
        engine.deleteBody(bodyId)
        _uiState.update { state ->
            if (state.camera.followBodyId == bodyId) {
                state.copy(camera = state.camera.copy(followBodyId = null))
            } else state
        }
    }

    fun deselectBody() {
        engine.selectBody(null)
        _uiState.update { it.copy(camera = it.camera.copy(followBodyId = null)) }
    }

    // Конструктор песочницы
    fun selectSandboxPreset(preset: SandboxPreset) {
        _uiState.update {
            it.copy(
                sandboxDraft = CustomBodyDraft(
                    preset = preset,
                    name = preset.titleRu,
                    massKg = preset.massKg,
                    radiusM = preset.radiusM,
                    hasRings = preset.hasRings
                )
            )
        }
    }

    fun updateSandboxDraft(transform: (CustomBodyDraft) -> CustomBodyDraft) {
        _uiState.update { it.copy(sandboxDraft = transform(it.sandboxDraft)) }
    }

    fun startAddCustomBodyMode(draft: CustomBodyDraft) {
        _uiState.update {
            it.copy(
                activeModal = ActiveModal.NONE,
                launchState = LaunchDragState(draft = draft)
            )
        }
    }

    fun cancelAddBodyMode() {
        _uiState.update { it.copy(launchState = null) }
    }

    fun onLaunchDragStart(screenX: Float, screenY: Float) {
        val current = _uiState.value.launchState ?: return
        _uiState.update {
            it.copy(
                launchState = current.copy(
                    startX = screenX,
                    startY = screenY,
                    endX = screenX,
                    endY = screenY,
                    speedLabel = "0.0 км/с",
                    predictedPointsWorld = null
                )
            )
        }
    }

    fun onLaunchDragMove(screenX: Float, screenY: Float, viewWidthPx: Float, viewHeightPx: Float) {
        val current = _uiState.value.launchState ?: return
        val sx = current.startX ?: return
        val sy = current.startY ?: return
        val cam = _uiState.value.camera

        val worldX = cam.screenToWorldX(sx, viewWidthPx)
        val worldY = cam.screenToWorldY(sy, viewWidthPx, viewHeightPx)
        val vx = (screenX - sx) * launchVelocityPerPx
        val vy = (screenY - sy) * launchVelocityPerPx
        val speedMs = sqrt(vx * vx + vy * vy)

        val stepDt = (cam.halfWidthMeters / 6.0e6).coerceIn(1200.0, 180_000.0)
        val preview = engine.predictTrajectory(worldX, worldY, vx, vy, steps = 55, stepDtSeconds = stepDt)

        _uiState.update {
            it.copy(
                launchState = current.copy(
                    endX = screenX,
                    endY = screenY,
                    speedLabel = PhysicsUnits.formatSpeed(speedMs),
                    predictedPointsWorld = preview
                )
            )
        }
    }

    fun onLaunchDragEnd(viewWidthPx: Float, viewHeightPx: Float) {
        val current = _uiState.value.launchState ?: return
        val sx = current.startX ?: return
        val sy = current.startY ?: return
        val ex = current.endX ?: sx
        val ey = current.endY ?: sy
        val cam = _uiState.value.camera

        val worldX = cam.screenToWorldX(sx, viewWidthPx)
        val worldY = cam.screenToWorldY(sy, viewWidthPx, viewHeightPx)
        val vx = (ex - sx) * launchVelocityPerPx
        val vy = (ey - sy) * launchVelocityPerPx

        val newId = engine.allocateId()
        val body = SolarSystemFactory.createFromDraft(
            id = newId,
            draft = current.draft,
            x = worldX,
            y = worldY,
            vx = vx,
            vy = vy
        )
        engine.addBody(body)
        engine.selectBody(newId)
        _uiState.update { it.copy(launchState = null) }
    }

    // Модальные окна и слои
    fun setModal(modal: ActiveModal) {
        if (modal == ActiveModal.MY_SCENES) refreshSavedScenes()
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun toggleGrid() = _uiState.update {
        it.copy(visualSettings = it.visualSettings.copy(showGrid = !it.visualSettings.showGrid))
    }

    fun toggleTrails() = _uiState.update {
        it.copy(visualSettings = it.visualSettings.copy(showTrails = !it.visualSettings.showTrails))
    }

    fun toggleLabels() = _uiState.update {
        it.copy(visualSettings = it.visualSettings.copy(showLabels = !it.visualSettings.showLabels))
    }

    fun toggleHabitableZone() = _uiState.update {
        it.copy(visualSettings = it.visualSettings.copy(showHabitableZone = !it.visualSettings.showHabitableZone))
    }

    fun toggleVelocityVectors() = _uiState.update {
        it.copy(visualSettings = it.visualSettings.copy(showVelocityVectors = !it.visualSettings.showVelocityVectors))
    }

    fun dismissCollisionBanner() = engine.clearCollisionBanner()

    // Локальное сохранение сцен
    fun refreshSavedScenes() {
        viewModelScope.launch {
            val list = storage.listScenes()
            _uiState.update { it.copy(savedScenes = list) }
        }
    }

    fun saveCurrentScene(name: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val bodies = engine.getCurrentBodiesCopy()
            storage.saveScene(
                name = name,
                bodies = bodies,
                timeScale = engine.getTimeScale(),
                cameraCenterX = state.camera.centerX,
                cameraCenterY = state.camera.centerY,
                halfWidthMeters = state.camera.halfWidthMeters,
                showGrid = state.visualSettings.showGrid,
                showTrails = state.visualSettings.showTrails,
                showLabels = state.visualSettings.showLabels
            )
            refreshSavedScenes()
            _uiState.update { it.copy(activeModal = ActiveModal.MY_SCENES) }
            showToast("Сцена «${name.trim().ifEmpty { "Моя сцена" }}» сохранена")
        }
    }

    fun loadSavedScene(sceneId: String) {
        viewModelScope.launch {
            val loaded = storage.loadScene(sceneId) ?: return@launch
            engine.loadBodies(
                newBodies = loaded.bodies,
                initialTimeScale = loaded.timeScale,
                startPaused = false,
                initialSelectedId = null
            )
            _uiState.update {
                it.copy(
                    camera = CameraState(
                        centerX = loaded.cameraCenterX,
                        centerY = loaded.cameraCenterY,
                        halfWidthMeters = loaded.halfWidthMeters
                    ),
                    visualSettings = it.visualSettings.copy(
                        showGrid = loaded.showGrid,
                        showTrails = loaded.showTrails,
                        showLabels = loaded.showLabels
                    ),
                    activeModal = ActiveModal.NONE,
                    launchState = null
                )
            }
            showToast("Загружена сцена «${loaded.name}»")
        }
    }

    fun deleteSavedScene(sceneId: String) {
        viewModelScope.launch {
            storage.deleteScene(sceneId)
            refreshSavedScenes()
        }
    }

    fun nextOnboardingStep() {
        val current = _uiState.value.onboardingStep ?: return
        if (current < 2) {
            _uiState.update { it.copy(onboardingStep = current + 1) }
        } else {
            finishOnboarding()
        }
    }

    fun finishOnboarding() {
        storage.setOnboardingCompleted(true)
        _uiState.update { it.copy(onboardingStep = null) }
    }

    fun reopenOnboarding() {
        _uiState.update { it.copy(onboardingStep = 0, activeModal = ActiveModal.NONE) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }
}
