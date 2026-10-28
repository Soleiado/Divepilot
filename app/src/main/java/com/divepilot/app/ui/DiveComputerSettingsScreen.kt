@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.divepilot.app.R
import com.divepilot.app.dive.DiveComputerSettingsManager

/**
 * Écran de configuration de l'ordinateur de plongée (fonctionnalité expérimentale,
 * désactivée par défaut). Accessible via « Option expérimentale » dans les réglages.
 */
@Composable
fun DiveComputerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { DiveComputerSettingsManager.get(context) }
    val settings by manager.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dc_title)) },
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
            // GROS disclaimer (fonctionnalité expérimentale).
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.dc_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.dc_enable),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings.enabled, onCheckedChange = { manager.setEnabled(it) })
            }

            if (settings.enabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dc_o2), style = MaterialTheme.typography.titleMedium)
                        Text("${settings.o2Percent} %", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { manager.setO2(settings.o2Percent - 1) }) { Text("−") }
                    OutlinedButton(onClick = { manager.setO2(settings.o2Percent + 1) }) { Text("+") }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.dc_altitude),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { manager.setAltitude(nextAltitude(settings.altitudeMeters, -1)) }) { Text("−") }
                    Text("${settings.altitudeMeters} m", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { manager.setAltitude(nextAltitude(settings.altitudeMeters, +1)) }) { Text("+") }
                }

                Text(stringResource(R.string.dc_water), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !settings.freshWater,
                        onClick = { manager.setFreshWater(false) },
                        label = { Text(stringResource(R.string.dc_salt)) },
                    )
                    FilterChip(
                        selected = settings.freshWater,
                        onClick = { manager.setFreshWater(true) },
                        label = { Text(stringResource(R.string.dc_fresh)) },
                    )
                }
            }
        }
    }
}

private fun nextAltitude(current: Int, direction: Int): Int {
    val values = listOf(0, 1000, 2000, 3000)
    val idx = values.indexOf(current)
    return values[(idx + direction + values.size) % values.size]
}
