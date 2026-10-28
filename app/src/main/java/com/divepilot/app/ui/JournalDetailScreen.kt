@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divepilot.app.data.DepthSample
import com.divepilot.app.dive.UnitManager
import androidx.compose.ui.res.stringResource
import com.divepilot.app.R

@Composable
fun JournalDetailScreen(logId: Long, onBack: () -> Unit) {
    val viewModel: JournalViewModel = viewModel()
    val logs by viewModel.logs.collectAsState()
    val log = logs.firstOrNull { it.id == logId }
    val context = LocalContext.current
    val units = UnitManager.get(context).settings.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_detail_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (log == null) {
            Box(Modifier.padding(padding)) { Text(stringResource(R.string.dive_not_found)) }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(formatDateTime(log.startTimeMillis), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${stringResource(if (log.diveType == "scuba") R.string.scuba else R.string.free)} · " +
                        formatDuration(log.durationSeconds * 1000),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.max_depth_label, formatDepth(log.maxDepthMeters, units.depthUnit)))
                Text(stringResource(R.string.avg_depth_label, formatDepth(log.avgDepthMeters, units.depthUnit)))
                Text(stringResource(R.string.min_temp_label, formatTemp(log.minTemperature, units.tempUnit)))
                Text(stringResource(R.string.avg_temp_label, formatTemp(log.avgTemperature, units.tempUnit)))
                val lat = log.latitude
                val lng = log.longitude
                if (lat != null && lng != null) {
                    Text(
                        stringResource(R.string.gps_label, formatGps(lat, lng)!!),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4DD0E1),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { openMaps(context, lat, lng) },
                    )
                }
                Text(
                    stringResource(R.string.journal_altitude, log.altitudeMeters),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.journal_water,
                        stringResource(if (log.freshWater) R.string.dc_fresh else R.string.dc_salt),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                DepthChart(log.samples, Modifier.fillMaxWidth().height(200.dp))
            }
        }
    }
}

@Composable
private fun DepthChart(samples: List<DepthSample>, modifier: Modifier) {
    Canvas(modifier) {
        if (samples.size < 2) return@Canvas
        val maxDepth = maxOf(samples.maxOf { it.depthMeters }, 0.5).toFloat()
        val maxTime = samples.maxOf { it.timeSeconds }.coerceAtLeast(1L).toFloat()
        val stepX = size.width / maxTime
        val stepY = size.height / maxDepth
        val path = Path()
        samples.forEachIndexed { i, s ->
            val x = s.timeSeconds * stepX
            val y = size.height - s.depthMeters.toFloat() * stepY
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFF4DD0E1), style = Stroke(width = 4f))
    }
}

/** Ouvre Google Maps (ou toute app de cartes) centré sur les coordonnées données. */
private fun openMaps(context: Context, latitude: Double, longitude: Double) {
    val geo = Uri.parse("geo:0,0?q=$latitude,$longitude")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (_: ActivityNotFoundException) {
        // Repli : ouverture dans le navigateur.
        val web = Uri.parse("https://maps.google.com/maps?q=$latitude,$longitude")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, web))
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.maps_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
