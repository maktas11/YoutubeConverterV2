package com.maktas.ytconverter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maktas.ytconverter.data.ColorPreset
import com.maktas.ytconverter.data.ColorPresets
import com.maktas.ytconverter.data.ColorThemeMode
import com.maktas.ytconverter.data.CustomColorRole
import com.maktas.ytconverter.data.CustomColors

@Composable
fun AppearanceSettingsScreen(vm: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    var editingRole by remember { mutableStateOf<CustomColorRole?>(null) }
    var editingSeed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.colorThemeMode == ColorThemeMode.DYNAMIC,
                onClick = { vm.setColorThemeMode(ColorThemeMode.DYNAMIC) },
                label = { Text("Match wallpaper") },
            )
            FilterChip(
                selected = settings.colorThemeMode == ColorThemeMode.PRESET,
                onClick = { vm.setColorThemeMode(ColorThemeMode.PRESET) },
                label = { Text("Preset") },
            )
            FilterChip(
                selected = settings.colorThemeMode == ColorThemeMode.CUSTOM,
                onClick = { vm.setColorThemeMode(ColorThemeMode.CUSTOM) },
                label = { Text("Custom") },
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        when (settings.colorThemeMode) {
            ColorThemeMode.DYNAMIC -> Text(
                "The app's colors follow your phone's wallpaper (Android 12+). " +
                    "Pick Preset or Custom above to choose your own instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ColorThemeMode.PRESET -> PresetGrid(
                selectedId = settings.colorPresetId,
                onSelect = { vm.setColorPresetId(it) },
            )

            ColorThemeMode.CUSTOM -> {
                Text(
                    "Base color",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Used for anything below you haven't customized.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ColorRoleRow(
                    label = "Base color",
                    color = Color(settings.customColors.seed),
                    showReset = settings.customColors.seed != CustomColors.DEFAULT_CUSTOM_SEED,
                    onReset = { vm.setCustomSeed(CustomColors.DEFAULT_CUSTOM_SEED) },
                    onClick = { editingSeed = true },
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                CustomRoleRow(vm, settings.customColors, CustomColorRole.PRIMARY, "Buttons & accents", MaterialTheme.colorScheme.primary) { editingRole = it }
                CustomRoleRow(vm, settings.customColors, CustomColorRole.SECONDARY, "Secondary accent", MaterialTheme.colorScheme.secondary) { editingRole = it }
                CustomRoleRow(vm, settings.customColors, CustomColorRole.TERTIARY, "Tertiary accent", MaterialTheme.colorScheme.tertiary) { editingRole = it }
                CustomRoleRow(vm, settings.customColors, CustomColorRole.NEUTRAL, "Background", MaterialTheme.colorScheme.background) { editingRole = it }
                CustomRoleRow(vm, settings.customColors, CustomColorRole.NEUTRAL_VARIANT, "Cards & surfaces", MaterialTheme.colorScheme.surfaceVariant) { editingRole = it }
                CustomRoleRow(vm, settings.customColors, CustomColorRole.ERROR, "Error", MaterialTheme.colorScheme.error) { editingRole = it }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (editingSeed) {
        ColorWheelDialog(
            initialColor = Color(settings.customColors.seed),
            onColorSelected = { color ->
                vm.setCustomSeed(color.toArgb())
                editingSeed = false
            },
            onDismiss = { editingSeed = false },
        )
    }

    editingRole?.let { role ->
        ColorWheelDialog(
            initialColor = roleOverrideOrCurrent(role, settings.customColors),
            onColorSelected = { color ->
                vm.setCustomRoleColor(role, color.toArgb())
                editingRole = null
            },
            onDismiss = { editingRole = null },
        )
    }
}

private fun roleOverrideOrCurrent(role: CustomColorRole, custom: CustomColors): Color {
    val argb = when (role) {
        CustomColorRole.PRIMARY -> custom.primary
        CustomColorRole.SECONDARY -> custom.secondary
        CustomColorRole.TERTIARY -> custom.tertiary
        CustomColorRole.NEUTRAL -> custom.neutral
        CustomColorRole.NEUTRAL_VARIANT -> custom.neutralVariant
        CustomColorRole.ERROR -> custom.error
    }
    return argb?.let { Color(it) } ?: Color(custom.seed)
}

@Composable
private fun CustomRoleRow(
    vm: MainViewModel,
    custom: CustomColors,
    role: CustomColorRole,
    label: String,
    autoColor: Color,
    onEdit: (CustomColorRole) -> Unit,
) {
    val override = when (role) {
        CustomColorRole.PRIMARY -> custom.primary
        CustomColorRole.SECONDARY -> custom.secondary
        CustomColorRole.TERTIARY -> custom.tertiary
        CustomColorRole.NEUTRAL -> custom.neutral
        CustomColorRole.NEUTRAL_VARIANT -> custom.neutralVariant
        CustomColorRole.ERROR -> custom.error
    }
    ColorRoleRow(
        label = label,
        color = override?.let { Color(it) } ?: autoColor,
        showReset = override != null,
        onReset = { vm.setCustomRoleColor(role, null) },
        onClick = { onEdit(role) },
    )
}

@Composable
private fun ColorRoleRow(
    label: String,
    color: Color,
    showReset: Boolean,
    onReset: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (showReset) {
            IconButton(onClick = onReset) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset $label to auto")
            }
            Spacer(Modifier.width(4.dp))
        }
        ColorSwatch(color = color, size = 40.dp, onClick = onClick)
    }
}

@Composable
private fun ColorSwatch(color: Color, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick)
            .background(color, CircleShape),
    )
}

@Composable
private fun PresetGrid(selectedId: String, onSelect: (String) -> Unit) {
    ColorPresets.all.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            row.forEach { preset ->
                PresetSwatch(preset = preset, selected = preset.id == selectedId, onClick = { onSelect(preset.id) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PresetSwatch(preset: ColorPreset, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick)
                .background(Color(preset.seed), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(preset.label, style = MaterialTheme.typography.bodySmall)
    }
}
