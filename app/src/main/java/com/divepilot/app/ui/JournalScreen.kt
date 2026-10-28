@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divepilot.app.R
import com.divepilot.app.data.DiveLog
import com.divepilot.app.dive.UnitManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun JournalScreen(onBack: () -> Unit, onOpenDetail: (Long) -> Unit) {
    val viewModel: JournalViewModel = viewModel()
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (logs.isEmpty()) {
                Text(stringResource(R.string.no_dives), Modifier.padding(16.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(logs) { log -> DiveLogRow(log) { onOpenDetail(log.id) } }
                }
            }
        }
    }
}

@Composable
private fun DiveLogRow(log: DiveLog, onClick: () -> Unit) {
    val units = UnitManager.get(LocalContext.current).settings.collectAsState().value
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(formatDateTime(log.startTimeMillis), style = MaterialTheme.typography.titleMedium)
            val mode = stringResource(if (log.diveType == "scuba") R.string.scuba else R.string.free)
            Text(
                stringResource(
                    R.string.dive_row,
                    mode,
                    formatDuration(log.durationSeconds * 1000),
                    formatDepth(log.maxDepthMeters, units.depthUnit),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.avg_min,
                    formatDepth(log.avgDepthMeters, units.depthUnit),
                    formatTemp(log.minTemperature, units.tempUnit),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
