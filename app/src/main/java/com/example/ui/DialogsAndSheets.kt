package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.physics.BodyType
import com.example.physics.PhysicsUnits
import com.example.scenarios.CustomBodyDraft
import com.example.scenarios.SandboxPreset
import com.example.scenarios.ScenarioCategory
import com.example.scenarios.ScenarioType
import com.example.storage.SavedSceneMetadata
import com.example.ui.theme.AlertRed
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceMutedText
import com.example.ui.theme.SpaceSurface
import com.example.ui.theme.SpaceSurfaceElevated
import com.example.ui.theme.SpaceWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ScenariosDialog(
    onSelectScenario: (ScenarioType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(ScenarioCategory.ALL) }
    val filteredScenarios = remember(selectedCategory) {
        if (selectedCategory == ScenarioCategory.ALL) ScenarioType.entries
        else ScenarioType.entries.filter { it.category == selectedCategory }
    }

    ModalContainer(title = "Сценарии и катастрофы", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_scenarios_hero),
                    contentDescription = "Космические эксперименты и катастрофы",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, SpaceBlack.copy(alpha = 0.85f))
                            )
                        )
                )
                Text(
                    text = "${ScenarioType.entries.size} сценариев · гравитация N тел",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldBright,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScenarioCategory.entries.forEach { cat ->
                    CategoryChip(cat.titleRu, selectedCategory == cat) {
                        selectedCategory = cat
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredScenarios) { scenario ->
                    val accent = when (scenario.category) {
                        ScenarioCategory.CATASTROPHES -> SolarAmber
                        ScenarioCategory.EXPERIMENTS -> EmeraldBright
                        else -> EmeraldAccent
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SpaceSurfaceElevated,
                        border = BorderStroke(1.dp, SpaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectScenario(scenario) }
                            .testTag("scenario_${scenario.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scenario.titleRu,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SpaceWhite
                                )
                                Text(
                                    text = scenario.descriptionRu,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SpaceMutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Расширенный конструктор Песочницы для создания собственных звёзд, планет и чёрных дыр
@Composable
fun SandboxBuilderDialog(
    draft: CustomBodyDraft,
    onSelectPreset: (SandboxPreset) -> Unit,
    onUpdateDraft: ((CustomBodyDraft) -> CustomBodyDraft) -> Unit,
    onLaunch: (CustomBodyDraft) -> Unit,
    onDismiss: () -> Unit
) {
    var filterCategory by remember { mutableStateOf<BodyType?>(null) }
    val presets = remember(filterCategory) {
        if (filterCategory == null) SandboxPreset.entries
        else SandboxPreset.entries.filter { it.bodyType == filterCategory }
    }

    val minMassExp = 16.0
    val maxMassExp = 32.5
    val minRadExp = 5.0
    val maxRadExp = 11.2
    val massSlider = ((log10(draft.massKg.coerceAtLeast(1e16)) - minMassExp) / (maxMassExp - minMassExp))
        .toFloat().coerceIn(0f, 1f)
    val radSlider = ((log10(draft.radiusM.coerceAtLeast(1e5)) - minRadExp) / (maxRadExp - minRadExp))
        .toFloat().coerceIn(0f, 1f)
    val density = PhysicsUnits.computeDensityKgM3(draft.massKg, draft.radiusM)

    ModalContainer(title = "Конструктор песочницы", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryChip("Все", filterCategory == null) { filterCategory = null }
                CategoryChip("Звёзды", filterCategory == BodyType.STAR) { filterCategory = BodyType.STAR }
                CategoryChip("Планеты", filterCategory == BodyType.PLANET) { filterCategory = BodyType.PLANET }
                CategoryChip("Чёрные дыры", filterCategory == BodyType.BLACK_HOLE) { filterCategory = BodyType.BLACK_HOLE }
                CategoryChip("Астероиды", filterCategory == BodyType.ASTEROID) { filterCategory = BodyType.ASTEROID }
                CategoryChip("Кометы", filterCategory == BodyType.COMET) { filterCategory = BodyType.COMET }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val selected = draft.preset == preset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) EmeraldContainer else SpaceSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) EmeraldAccent else SpaceBorder),
                        modifier = Modifier
                            .width(155.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectPreset(preset) }
                            .testTag("preset_${preset.name.lowercase()}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(if (preset.bodyType == BodyType.BLACK_HOLE) preset.secondaryColor else preset.primaryColor))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset.titleRu,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpaceWhite,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = preset.subtitleRu,
                                style = MaterialTheme.typography.labelSmall,
                                color = SpaceMutedText,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { txt -> onUpdateDraft { it.copy(name = txt) } },
                label = { Text("Название объекта") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_body_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldAccent,
                    unfocusedBorderColor = SpaceBorder,
                    focusedTextColor = SpaceWhite,
                    unfocusedTextColor = SpaceWhite,
                    focusedLabelColor = EmeraldBright,
                    unfocusedLabelColor = SpaceMutedText
                )
            )

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Масса", style = MaterialTheme.typography.bodyMedium, color = SpaceMutedText)
                    Text(PhysicsUnits.formatMass(draft.massKg), style = MaterialTheme.typography.labelMedium, color = EmeraldBright)
                }
                Slider(
                    value = massSlider,
                    onValueChange = { v ->
                        val newM = 10.0.pow(minMassExp + v * (maxMassExp - minMassExp))
                        onUpdateDraft { it.copy(massKg = newM) }
                    },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    colors = SliderDefaults.colors(thumbColor = EmeraldBright, activeTrackColor = EmeraldAccent, inactiveTrackColor = SpaceBorder)
                )
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Радиус", style = MaterialTheme.typography.bodyMedium, color = SpaceMutedText)
                    Text(PhysicsUnits.formatRadius(draft.radiusM), style = MaterialTheme.typography.labelMedium, color = EmeraldBright)
                }
                Slider(
                    value = radSlider,
                    onValueChange = { v ->
                        val newR = 10.0.pow(minRadExp + v * (maxRadExp - minRadExp))
                        onUpdateDraft { it.copy(radiusM = newR) }
                    },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    colors = SliderDefaults.colors(thumbColor = EmeraldBright, activeTrackColor = EmeraldAccent, inactiveTrackColor = SpaceBorder)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Плотность: ${PhysicsUnits.formatDensity(density)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpaceMutedText
                )
                if (draft.preset.bodyType == BodyType.PLANET) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Кольца", style = MaterialTheme.typography.bodyMedium, color = SpaceWhite)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = draft.hasRings,
                            onCheckedChange = { checked -> onUpdateDraft { it.copy(hasRings = checked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = SpaceBlack, checkedTrackColor = EmeraldAccent)
                        )
                    }
                }
            }

            Button(
                onClick = { onLaunch(draft) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("launch_custom_body_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = SpaceBlack)
            ) {
                Text("Разместить и задать скорость стрелкой →")
            }
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(title) },
        modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EmeraldContainer,
            selectedLabelColor = EmeraldBright,
            containerColor = SpaceSurfaceElevated,
            labelColor = SpaceMutedText
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SpaceBorder,
            selectedBorderColor = EmeraldAccent
        )
    )
}

@Composable
fun MyScenesDialog(
    scenes: List<SavedSceneMetadata>,
    onRequestSaveCurrent: () -> Unit,
    onLoadScene: (String) -> Unit,
    onDeleteScene: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    ModalContainer(title = "Мои сцены", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRequestSaveCurrent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_current_scene_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = SpaceBlack)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сохранить текущую сцену")
            }

            if (scenes.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SpaceSurfaceElevated,
                    border = BorderStroke(1.dp, SpaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Image(
                            painter = painterResource(id = R.drawable.img_onboarding_cosmos),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                        )
                        Text(
                            text = "Сохранённых сцен пока нет. Создайте эксперимент и нажмите «Сохранить текущую сцену».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpaceMutedText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scenes, key = { it.id }) { scene ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SpaceSurfaceElevated,
                            border = BorderStroke(1.dp, SpaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onLoadScene(scene.id) }
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = scene.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SpaceWhite
                                    )
                                    Text(
                                        text = "${scene.bodyCount} тел · ${dateFormatter.format(Date(scene.savedAtMillis))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SpaceMutedText
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onLoadScene(scene.id) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                    border = BorderStroke(1.dp, EmeraldAccent),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldBright)
                                ) {
                                    Text("Открыть")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onDeleteScene(scene.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить сцену",
                                        tint = AlertRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaveSceneNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf("Мой эксперимент") }

    ModalContainer(title = "Название сцены", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Введите название") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldAccent,
                    unfocusedBorderColor = SpaceBorder,
                    focusedTextColor = SpaceWhite,
                    unfocusedTextColor = SpaceWhite,
                    focusedLabelColor = EmeraldBright,
                    unfocusedLabelColor = SpaceMutedText
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Отмена", color = SpaceMutedText)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(nameText) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_save_scene_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = SpaceBlack)
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}

@Composable
fun LayersDialog(
    settings: VisualSettings,
    onToggleGrid: () -> Unit,
    onToggleGravityGrid: () -> Unit,
    onToggleTrails: () -> Unit,
    onToggleLabels: () -> Unit,
    onToggleHabitableZone: () -> Unit,
    onToggleVelocityVectors: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalContainer(title = "Визуализация", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LayerToggleRow("Сетка расстояний", settings.showGrid, onToggleGrid, "toggle_grid")
            LayerToggleRow("Искривление сетки гравитацией", settings.showGravityGrid, onToggleGravityGrid, "toggle_gravity_grid")
            LayerToggleRow("Следы орбит", settings.showTrails, onToggleTrails, "toggle_trails")
            LayerToggleRow("Подписи тел", settings.showLabels, onToggleLabels, "toggle_labels")
            LayerToggleRow("Зона обитаемости звёзд", settings.showHabitableZone, onToggleHabitableZone, "toggle_habitable")
            LayerToggleRow("Векторы скорости", settings.showVelocityVectors, onToggleVelocityVectors, "toggle_vectors")
        }
    }
}

@Composable
private fun LayerToggleRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SpaceSurfaceElevated,
        border = BorderStroke(1.dp, SpaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = SpaceWhite)
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SpaceBlack,
                    checkedTrackColor = EmeraldAccent,
                    uncheckedThumbColor = SpaceMutedText,
                    uncheckedTrackColor = SpaceBlack
                )
            )
        }
    }
}

@Composable
fun OnboardingOverlay(
    step: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val (title, body, icon) = when (step) {
        0 -> Triple(
            "1 / 3 · Камера и объекты",
            "Перетаскивайте космос одним пальцем и приближайте щипком от масштаба всей системы до поверхности планеты. Нажмите на тело для редактирования, двойное касание центрирует камеру.",
            Icons.Default.TouchApp
        )
        1 -> Triple(
            "2 / 3 · Время и катастрофы",
            "Внизу расположены пауза (0×), реверс и логарифмический ползунок ускорения до 1 млн раз. В панели выбранного тела можно менять массу, радиус и даже взорвать звезду как сверхновую!",
            Icons.Default.Brightness7
        )
        else -> Triple(
            "3 / 3 · Песочница и сценарии",
            "Нажмите «+», соберите собственную звезду, пульсар, планету с кольцами или чёрную дыру. Или откройте «Сценарии» с 14 готовыми космическими экспериментами!",
            Icons.Default.AutoAwesome
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .testTag("onboarding_card"),
            shape = RoundedCornerShape(22.dp),
            color = SpaceSurface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, EmeraldAccent)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_onboarding_cosmos),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, SpaceSurface.copy(alpha = 0.95f))
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = EmeraldBright,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = SpaceWhite
                        )
                    }

                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpaceMutedText
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0..2) {
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (i == step) 24.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(if (i == step) EmeraldBright else SpaceBorder)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("onboarding_skip_button")
                        ) {
                            Text("Пропустить", color = SpaceMutedText)
                        }
                        Button(
                            onClick = onNext,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("onboarding_next_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = SpaceBlack)
                        ) {
                            Text(if (step < 2) "Далее" else "Начать")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalContainer(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SpaceSurface,
            border = BorderStroke(1.dp, SpaceBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = SpaceWhite
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = SpaceMutedText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}
