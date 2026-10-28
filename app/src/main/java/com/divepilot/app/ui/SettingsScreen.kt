@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divepilot.app.R
import com.divepilot.app.dive.DiveModeSetting
import com.divepilot.app.dive.ItemSetting
import com.divepilot.app.dive.UnitManager
import com.divepilot.app.dive.UnitSettings
import com.divepilot.app.util.LocaleManager

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDiveComputer: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val setting = viewModel.setting

    val context = LocalContext.current
    val activity = LocalActivity.current
    val unitManager = remember { UnitManager.get(context) }
    val units by unitManager.settings.collectAsState()
    val language = LocaleManager.getLanguage(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.units), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = units.depthUnit == UnitSettings.UNIT_M,
                    onClick = { unitManager.setDepthUnit(UnitSettings.UNIT_M) },
                    label = { Text(stringResource(R.string.meters)) },
                )
                FilterChip(
                    selected = units.depthUnit == UnitSettings.UNIT_FT,
                    onClick = { unitManager.setDepthUnit(UnitSettings.UNIT_FT) },
                    label = { Text(stringResource(R.string.feet)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = units.tempUnit == UnitSettings.UNIT_C,
                    onClick = { unitManager.setTempUnit(UnitSettings.UNIT_C) },
                    label = { Text(stringResource(R.string.celsius)) },
                )
                FilterChip(
                    selected = units.tempUnit == UnitSettings.UNIT_F,
                    onClick = { unitManager.setTempUnit(UnitSettings.UNIT_F) },
                    label = { Text(stringResource(R.string.fahrenheit)) },
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = language == "fr",
                    onClick = { changeLanguage(activity, "fr") },
                    label = { Text("Français") },
                )
                FilterChip(
                    selected = language == "en",
                    onClick = { changeLanguage(activity, "en") },
                    label = { Text("English") },
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onOpenDiveComputer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dc_experimental_option))
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.mode), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = setting.mode == DiveModeSetting.MODE_SCUBA,
                    onClick = { viewModel.update { it.copy(mode = DiveModeSetting.MODE_SCUBA) } },
                    label = { Text(stringResource(R.string.scuba)) },
                )
                FilterChip(
                    selected = setting.mode == DiveModeSetting.MODE_FREE,
                    onClick = { viewModel.update { it.copy(mode = DiveModeSetting.MODE_FREE) } },
                    label = { Text(stringResource(R.string.free)) },
                )
            }

            Spacer(Modifier.height(16.dp))

            if (setting.mode == DiveModeSetting.MODE_SCUBA) {
                SettingRow(stringResource(R.string.max_depth_setting), stringResource(R.string.unit_m), setting.scubaMaxDepth, 1..200) {
                    viewModel.update { s -> s.copy(scubaMaxDepth = it) }
                }
                SettingRow(stringResource(R.string.max_time_setting), stringResource(R.string.unit_min), setting.scubaMaxTime, 1..300) {
                    viewModel.update { s -> s.copy(scubaMaxTime = it) }
                }
                SettingRow(stringResource(R.string.ascent_rate_setting), stringResource(R.string.unit_m_min), setting.scubaAscentRate, 1..30) {
                    viewModel.update { s -> s.copy(scubaAscentRate = it) }
                }
            } else {
                SettingRow(stringResource(R.string.depth_alert, 1), stringResource(R.string.unit_m), setting.freeDepth1, 1..200) {
                    viewModel.update { s -> s.copy(freeDepth1 = it) }
                }
                SettingRow(stringResource(R.string.depth_alert, 2), stringResource(R.string.unit_m), setting.freeDepth2, 1..200) {
                    viewModel.update { s -> s.copy(freeDepth2 = it) }
                }
                SettingRow(stringResource(R.string.depth_alert, 3), stringResource(R.string.unit_m), setting.freeDepth3, 1..200) {
                    viewModel.update { s -> s.copy(freeDepth3 = it) }
                }
                SettingRow(stringResource(R.string.depth_alert, 4), stringResource(R.string.unit_m), setting.freeDepth4, 1..200) {
                    viewModel.update { s -> s.copy(freeDepth4 = it) }
                }
                SettingRow(stringResource(R.string.depth_alert, 5), stringResource(R.string.unit_m), setting.freeDepth5, 1..200) {
                    viewModel.update { s -> s.copy(freeDepth5 = it) }
                }
                SettingRow(stringResource(R.string.surface_interval_setting), stringResource(R.string.unit_min), setting.freeSurfaceInterval, 1..60) {
                    viewModel.update { s -> s.copy(freeSurfaceInterval = it) }
                }
            }
        }
    }
}

private fun changeLanguage(activity: Activity?, language: String) {
    if (activity == null) return
    LocaleManager.setLanguage(activity, language)
    activity.recreate()
}

@Composable
private fun SettingRow(
    title: String,
    unit: String,
    item: ItemSetting,
    range: IntRange,
    onChange: (ItemSetting) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${item.value} $unit", style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = item.onOff, onCheckedChange = { onChange(item.copy(onOff = it)) })
        OutlinedButton(onClick = { onChange(item.copy(value = (item.value - 1).coerceIn(range))) }) {
            Text("−")
        }
        OutlinedButton(onClick = { onChange(item.copy(value = (item.value + 1).coerceIn(range))) }) {
            Text("+")
        }
    }
}
