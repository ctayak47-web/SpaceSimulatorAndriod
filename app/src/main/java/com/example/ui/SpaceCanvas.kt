package com.example.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import com.example.physics.SimulationSnapshot
import com.example.render.CameraState
import com.example.render.SimulationCanvas

@Composable
fun SpaceCanvas(
    snapshot: SimulationSnapshot,
    camera: CameraState,
    visualSettings: VisualSettings,
    launchState: LaunchDragState?,
    onTransform: (panX: Float, panY: Float, zoom: Float, cx: Float, cy: Float, w: Float, h: Float) -> Unit,
    onTap: (x: Float, y: Float, w: Float, h: Float) -> Unit,
    onDoubleTap: (x: Float, y: Float, w: Float, h: Float) -> Unit,
    onLaunchStart: (x: Float, y: Float) -> Unit,
    onLaunchDrag: (x: Float, y: Float, w: Float, h: Float) -> Unit,
    onLaunchEnd: (w: Float, h: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewW by remember { mutableFloatStateOf(1f) }
    var viewH by remember { mutableFloatStateOf(1f) }

    val latestLaunchMode by rememberUpdatedState(launchState != null)
    val latestOnTransform by rememberUpdatedState(onTransform)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnLaunchStart by rememberUpdatedState(onLaunchStart)
    val latestOnLaunchDrag by rememberUpdatedState(onLaunchDrag)
    val latestOnLaunchEnd by rememberUpdatedState(onLaunchEnd)

    val gestureModifier = if (launchState != null) {
        Modifier.pointerInput(launchState.draft) {
            detectDragGestures(
                onDragStart = { offset ->
                    latestOnLaunchStart(offset.x, offset.y)
                },
                onDrag = { change, _ ->
                    change.consume()
                    latestOnLaunchDrag(change.position.x, change.position.y, viewW, viewH)
                },
                onDragEnd = {
                    latestOnLaunchEnd(viewW, viewH)
                },
                onDragCancel = {
                    latestOnLaunchEnd(viewW, viewH)
                }
            )
        }
    } else {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!latestLaunchMode) {
                            latestOnTap(offset.x, offset.y, viewW, viewH)
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!latestLaunchMode) {
                            latestOnDoubleTap(offset.x, offset.y, viewW, viewH)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (!latestLaunchMode) {
                        latestOnTransform(pan.x, pan.y, zoom, centroid.x, centroid.y, viewW, viewH)
                    }
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("space_canvas")
            .onSizeChanged { size ->
                viewW = size.width.toFloat().coerceAtLeast(1f)
                viewH = size.height.toFloat().coerceAtLeast(1f)
            }
            .then(gestureModifier)
    ) {
        SimulationCanvas(
            snapshot = snapshot,
            camera = camera,
            selectedBodyId = snapshot.selectedTelemetry?.id,
            showGrid = visualSettings.showGrid,
            showGravityGrid = visualSettings.showGravityGrid,
            showTrails = visualSettings.showTrails,
            showLabels = visualSettings.showLabels,
            showHabitableZone = visualSettings.showHabitableZone,
            showVelocityVectors = visualSettings.showVelocityVectors,
            launchStartScreenX = launchState?.startX,
            launchStartScreenY = launchState?.startY,
            launchEndScreenX = launchState?.endX,
            launchEndScreenY = launchState?.endY,
            launchSpeedLabel = launchState?.speedLabel,
            predictedPointsWorld = launchState?.predictedPointsWorld
        )
    }
}
