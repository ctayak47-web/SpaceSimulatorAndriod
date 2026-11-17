package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.physics.BodyType
import com.example.physics.PhysicsUnits
import com.example.physics.SelectedBodyTelemetry
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricBlueContainer
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceMutedText
import com.example.ui.theme.SpaceSurface
import com.example.ui.theme.SpaceWhite
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MIN_MASS_EXP = 18.0
private const val MAX_MASS_EXP = 32.3
private const val MIN_RADIUS_EXP = 5.0
private const val MAX_RADIUS_EXP = 11.3

@Composable
fun InspectorPanel(
    telemetry: SelectedBodyTelemetry,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onCenter: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onMassChange: (Double) -> Unit,
    onRadiusChange: (Double) -> Unit,
    onScaleVelocity: (Double) -> Unit,
    onToggleRings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val massSlider = ((log10(telemetry.massKg.coerceAtLeast(1e18)) - MIN_MASS_EXP) / (MAX_MASS_EXP - MIN_MASS_EXP))
        .toFloat().coerceIn(0f, 1f)
    val radiusSlider = ((log10(telemetry.radiusM.coerceAtLeast(1e5)) - MIN_RADIUS_EXP) / (MAX_RADIUS_EXP - MIN_RADIUS_EXP))
        .toFloat().coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .testTag("inspector_panel"),
        shape = RoundedCornerShape(20.dp),
        color = SpaceSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, SpaceBorder),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(telemetry.primaryColorArgb))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = telemetry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = SpaceWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${telemetry.type.titleRu} · ρ ${PhysicsUnits.formatDensity(telemetry.densityKgM3)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpaceMutedText
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_inspector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть панель тела",
                        tint = SpaceMutedText
                    )
                }
            }

            // Основные действия камеры и удаление (>= 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onToggleFollow,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("follow_body_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isFollowing) ElectricBlue else ElectricBlueContainer,
                        contentColor = SpaceWhite
                    )
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isFollowing) "Слежу" else "Следить", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onCenter,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("center_body_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, SpaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceWhite)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ElectricBlueBright
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Центр", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("delete_body_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить тело", modifier = Modifier.size(18.dp))
                }
            }

            // Быстрые орбитальные эксперименты (Universe Sandbox style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onScaleVelocity(0.5) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, SpaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceWhite)
                ) {
                    Text("Тормоз 0.5×", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onScaleVelocity(1.4) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, SpaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceWhite)
                ) {
                    Text("Ускорить 1.4×", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                if (telemetry.type == BodyType.PLANET) {
                    OutlinedButton(
                        onClick = onToggleRings,
                        modifier = Modifier
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, if (telemetry.hasRings) ElectricBlue else SpaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (telemetry.hasRings) ElectricBlueBright else SpaceMutedText
                        )
                    ) {
                        Text("Кольца", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }

            // Физическая телеметрия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryItem("Скорость", PhysicsUnits.formatSpeed(telemetry.speedMs), Modifier.weight(1f))
                TelemetryItem(
                    "До Солнца",
                    telemetry.distanceToSunM?.let { PhysicsUnits.formatDistance(it) } ?: "—",
                    Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryItem(
                    "Температура",
                    PhysicsUnits.formatTemperature(telemetry.surfaceTempK),
                    Modifier.weight(1f),
                    valueColor = when {
                        telemetry.surfaceTempK > 1200.0 -> AlertRed
                        telemetry.surfaceTempK > 600.0 -> SolarAmber
                        else -> SpaceWhite
                    }
                )
                TelemetryItem("Период", PhysicsUnits.formatPeriod(telemetry.orbitalPeriodSec), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryItem("Гравитация g", PhysicsUnits.formatGravity(telemetry.surfaceGravityMs2), Modifier.weight(1f))
                TelemetryItem("2-я косм. скор.", PhysicsUnits.formatSpeed(telemetry.escapeVelocityMs), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryItem("Плотность", PhysicsUnits.formatDensity(telemetry.densityKgM3), Modifier.weight(1f))
                TelemetryItem("Атмосфера", "${(telemetry.atmosphereFraction * 100).roundToInt()}%", Modifier.weight(1f))
            }

            // Ползунок массы на лету
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Масса", style = MaterialTheme.typography.bodyMedium, color = SpaceMutedText)
                    Text(
                        text = PhysicsUnits.formatMass(telemetry.massKg),
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlueBright
                    )
                }
                Slider(
                    value = massSlider,
                    onValueChange = { v ->
                        val exp = MIN_MASS_EXP + v * (MAX_MASS_EXP - MIN_MASS_EXP)
                        onMassChange(10.0.pow(exp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("mass_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlueBright,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = SpaceBorder
                    )
                )
            }

            // Ползунок радиуса на лету
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Радиус", style = MaterialTheme.typography.bodyMedium, color = SpaceMutedText)
                    Text(
                        text = PhysicsUnits.formatRadius(telemetry.radiusM),
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlueBright
                    )
                }
                Slider(
                    value = radiusSlider,
                    onValueChange = { v ->
                        val exp = MIN_RADIUS_EXP + v * (MAX_RADIUS_EXP - MIN_RADIUS_EXP)
                        onRadiusChange(10.0.pow(exp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("radius_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlueBright,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = SpaceBorder
                    )
                )
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = SpaceWhite
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SpaceMutedText)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
