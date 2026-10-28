package com.divepilot.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divepilot.app.R
import com.divepilot.app.dive.DiveComputerManager
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassScreen(onBack: () -> Unit) {
    val viewModel: CompassViewModel = viewModel()
    val azimuth by viewModel.azimuth.collectAsState()

    val context = LocalContext.current
    val diveManager = remember { DiveComputerManager.get(context) }
    val diveState by diveManager.uiState.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Boussole centrée.
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompassFace(azimuth, Modifier.size(300.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                "${azimuth.roundToInt()}° ${CompassViewModel.cardinal(azimuth)}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        }

        // Retour.
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) { Text(stringResource(R.string.back), color = Color.White) }

        // Tuile plongée : toujours visible, même en mode boussole.
        DiveOverlay(
            state = diveState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp),
        )

        // Batteries (téléphone + boîtier).
        BatteryOverlay(
            boxBatteryPercent = diveState.batteryPercent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun CompassFace(azimuth: Float, modifier: Modifier = Modifier) {
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f * 0.85f
        textPaint.textSize = 28.dp.toPx()

        drawCircle(Color(0xFF12263A), size.minDimension / 2f)
        drawCircle(Color.White, radius, center = Offset(cx, cy), style = Stroke(3.dp.toPx()))

        rotate(degrees = -azimuth, pivot = Offset(cx, cy)) {
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val c = cos(rad).toFloat()
                val s = sin(rad).toFloat()
                val isCardinal = deg % 90 == 0
                val inner = if (isCardinal) radius - 22.dp.toPx() else radius - 10.dp.toPx()
                drawLine(
                    color = if (isCardinal) Color(0xFF4DD0E1) else Color.Gray,
                    start = Offset(cx + c * inner, cy + s * inner),
                    end = Offset(cx + c * radius, cy + s * radius),
                    strokeWidth = if (isCardinal) 4.dp.toPx() else 2.dp.toPx(),
                )
            }
            val letters = arrayOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
            for ((letter, deg) in letters) {
                val rad = Math.toRadians(deg.toDouble())
                val c = cos(rad).toFloat()
                val s = sin(rad).toFloat()
                val x = cx + c * (radius - 40.dp.toPx())
                val y = cy + s * (radius - 40.dp.toPx()) + textPaint.textSize / 3f
                drawContext.canvas.nativeCanvas.drawText(letter, x, y, textPaint)
            }
        }

        // Aiguille fixe (repère de cap) vers le haut.
        val needle = Path().apply {
            moveTo(cx, cy - radius * 0.72f)
            lineTo(cx - 12.dp.toPx(), cy - radius * 0.45f)
            lineTo(cx + 12.dp.toPx(), cy - radius * 0.45f)
            close()
        }
        drawPath(needle, Color.Red)
        drawCircle(Color(0xFF4DD0E1), 5.dp.toPx(), Offset(cx, cy))
    }
}
