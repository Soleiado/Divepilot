package com.divepilot.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divepilot.app.R
import com.divepilot.app.dive.DiveState
import com.divepilot.app.dive.DiveUiState
import com.divepilot.app.dive.UnitManager

/** Panneau de télémétrie affiché en haut à gauche sur la caméra. */
@Composable
fun DiveOverlay(state: DiveUiState, modifier: Modifier = Modifier) {
    val diving = state.diveState == DiveState.START
    val units = UnitManager.get(LocalContext.current).settings.collectAsState().value

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Profondeur.
            Text(
                stringResource(R.string.depth_label),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatDepth(state.currentDepthMeters, units.depthUnit),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    formatDepth(state.maxDepthMeters, units.depthUnit),
                    color = Color.Red,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Temps de plongée (actuel + restant).
            Text(
                stringResource(R.string.dive_time_label),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatDuration(state.diveElapsedMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                )
                if (state.dcEnabled) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        remainingTimeText(state.ndlMinutes),
                        color = remainingTimeColor(state.ndlMinutes),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            // Palier : profondeur + temps (si l'ordinateur de plongée est activé).
            if (state.dcEnabled) {
                Text(
                    stringResource(R.string.stop_label),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val (stopDepthText, stopTimeText, stopColor) = when {
                    state.ceilingMeters > 0.0 -> Triple(
                        formatDepth(state.firstStopMeters, units.depthUnit),
                        formatMinutesAsTime(state.firstStopMinutes),
                        Color.Red,
                    )
                    state.staySafeState == 1 || state.staySafeState == 2 -> Triple(
                        formatDepth(6.0, units.depthUnit),
                        formatMinutesAsTime(3),
                        Color.Green,
                    )
                    else -> Triple(depthPlaceholder(units.depthUnit), "--:--", Color.Green)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stopDepthText, color = stopColor, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(stopTimeText, color = stopColor, style = MaterialTheme.typography.headlineLarge)
                }
            }

            // Température.
            Text(
                formatTemp(state.currentTemperature, units.tempUnit),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )

            // Toxicité oxygène.
            if (state.cnsPercent > 0.5 || state.otu > 0.5) {
                Text(
                    stringResource(R.string.cns_otu, state.cnsPercent.toInt(), state.otu.toInt()),
                    color = if (state.cnsPercent > 80.0 || state.otu > 600.0) Color.Red else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Intervalle de surface + temps avant avion (si l'ordinateur de plongée est activé).
            if (!diving && state.dcEnabled && state.surfaceIntervalMillis > 0L) {
                Text(
                    stringResource(R.string.surface_interval, formatHoursMinutes(state.surfaceIntervalMillis)),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.noFlyMinutes > 0.0) {
                    Text(
                        stringResource(R.string.no_fly_time, formatMinutesToHours(state.noFlyMinutes)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun remainingTimeText(ndl: Double): String {
    if (ndl >= 99.0) return "--:--"
    val totalSeconds = (ndl * 60.0).toInt()
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun remainingTimeColor(ndl: Double): Color = when {
    ndl >= 99.0 -> Color.Green
    ndl > 10.0 -> Color.Green
    ndl >= 5.0 -> Color.Yellow
    else -> Color.Red
}

private fun formatMinutesAsTime(minutes: Int): String =
    "%d:%02d".format(minutes, 0)

/** Batteries du téléphone et du boîtier, sur une seule ligne en bas à gauche. */
@Composable
fun BatteryOverlay(boxBatteryPercent: Int, modifier: Modifier = Modifier) {
    val phoneBattery = rememberPhoneBatteryPercent()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Text(
            "${stringResource(R.string.phone_battery)} $phoneBattery %   " +
                "${stringResource(R.string.box_battery)} $boxBatteryPercent %",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
