package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.physics.CollisionEvent
import com.example.physics.PhysicsEngine
import com.example.physics.PhysicsUnits
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricBlueContainer
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceMutedText
import com.example.ui.theme.SpaceSurface
import com.example.ui.theme.SpaceWhite
import kotlin.math.roundToInt

@Composable
fun TopHudBar(
    simTimeSeconds: Double,
    timeState: PhysicsEngine.TimeControlState,
    bodyCount: Int,
    onOpenScenarios: () -> Unit,
    onOpenMyScenes: () -> Unit,
    onOpenLayers: () -> Unit,
    onReset: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SpaceSurface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, SpaceBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(
                    text = PhysicsUnits.formatTimeScale(
                        timeState.timeScale,
                        timeState.isReversed,
                        timeState.isPaused
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (timeState.isReversed) SolarAmber else ElectricBlueBright,
                    maxLines = 1
                )
                Text(
                    text = "${PhysicsUnits.formatSimTime(simTimeSeconds)} · $bodyCount тел",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpaceMutedText,
                    maxLines = 1
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudIconButton(
                onClick = onOpenScenarios,
                icon = Icons.Default.AutoAwesome,
                contentDescription = "Сценарии",
                highlighted = true,
                tag = "scenarios_button"
            )
            HudIconButton(
                onClick = onOpenMyScenes,
                icon = Icons.Default.FolderOpen,
                contentDescription = "Мои сцены",
                tag = "my_scenes_button"
            )
            HudIconButton(
                onClick = onOpenLayers,
                icon = Icons.Default.Layers,
                contentDescription = "Слои",
                tag = "layers_button"
            )
            HudIconButton(
                onClick = onReset,
                icon = Icons.Default.Refresh,
                contentDescription = "Сброс",
                tag = "reset_button"
            )
            HudIconButton(
                onClick = onOpenHelp,
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "Подсказка",
                tag = "help_button"
            )
        }
    }
}

@Composable
private fun HudIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    highlighted: Boolean = false
) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) ElectricBlueContainer else SpaceSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, if (highlighted) ElectricBlue else SpaceBorder)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .testTag(tag),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (highlighted) ElectricBlueBright else SpaceWhite
            )
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
fun CollisionBanner(
    event: CollisionEvent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        if (event != null) {
            val accent = if (event.isMerge) SolarAmber else AlertRed
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SpaceSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collision_banner")
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Энергия: ${PhysicsUnits.formatEnergyMt(event.energyMt)} · " +
                                "Масса: ${PhysicsUnits.formatMass(event.newMassKg)} (ρ ${PhysicsUnits.formatDensity(event.newDensityKgM3)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SpaceWhite
                        )
                        Text(
                            text = "Потеря массы: ${String.format("%.1f", event.massLossPercent)}% · " +
                                "Нагрев: +${event.tempDeltaK.roundToInt()} K (до ${event.newTempK.roundToInt()} K) · " +
                                "Атмосфера: -${String.format("%.0f", event.atmosphereLossPercent)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpaceMutedText
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dismiss_collision_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Скрыть отчёт о столкновении",
                            tint = SpaceMutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LaunchModeBanner(
    bodyName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ElectricBlueContainer.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, ElectricBlue),
        modifier = modifier
            .fillMaxWidth()
            .testTag("launch_mode_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Запуск: $bodyName",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpaceWhite
                )
                Text(
                    text = "Коснитесь космоса и потяните стрелку, чтобы задать скорость",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricBlueBright
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_launch_button"),
                border = BorderStroke(1.dp, ElectricBlueBright),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceWhite)
            ) {
                Text("Отмена")
            }
        }
    }
}

@Composable
fun BottomTimeAndAddBar(
    timeState: PhysicsEngine.TimeControlState,
    onToggleReverse: () -> Unit,
    onTogglePause: () -> Unit,
    onNormalSpeed: () -> Unit,
    onSpeedSliderChange: (Float) -> Unit,
    onAddBodyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderPos = PhysicsUnits.timeScaleToSlider(timeState.timeScale)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            color = SpaceSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, SpaceBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (timeState.isReversed) SolarAmber.copy(alpha = 0.22f) else ElectricBlueContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, if (timeState.isReversed) SolarAmber else SpaceBorder)
                    ) {
                        IconButton(
                            onClick = onToggleReverse,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("reverse_time_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Реверс времени",
                                tint = if (timeState.isReversed) SolarAmber else SpaceWhite
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = ElectricBlue,
                        border = BorderStroke(1.dp, ElectricBlueBright)
                    ) {
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("pause_play_button")
                        ) {
                            Icon(
                                imageVector = if (timeState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (timeState.isPaused) "Запустить" else "Пауза",
                                tint = SpaceWhite
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNormalSpeed,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("normal_speed_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, SpaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceWhite)
                    ) {
                        Text("1×", style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = if (timeState.isPaused) "0× (Пауза)" else if (timeState.isReversed) "Реверс" else "до 1 млн ×",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (timeState.isReversed) SolarAmber else SpaceMutedText
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Логарифмическая шкала: 0 (Пауза) .. 1 000 000x
                Slider(
                    value = sliderPos,
                    onValueChange = onSpeedSliderChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("time_speed_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlueBright,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = SpaceBorder
                    )
                )
            }
        }

        FloatingActionButton(
            onClick = onAddBodyClick,
            modifier = Modifier
                .size(58.dp)
                .testTag("add_body_fab"),
            shape = CircleShape,
            containerColor = ElectricBlue,
            contentColor = SpaceWhite
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Конструктор песочницы",
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
