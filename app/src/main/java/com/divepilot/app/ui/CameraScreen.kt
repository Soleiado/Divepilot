@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divepilot.app.R
import com.divepilot.app.camera.CameraMode
import com.divepilot.app.camera.CameraUiState
import com.divepilot.app.camera.CameraViewModel
import com.divepilot.app.dive.DiveComputerManager

/**
 * Écran caméra : aperçu CameraX, contrôles photo/vidéo, zoom et mise au point.
 * Les commandes du boîtier (BleCommand) sont reçues via [CameraViewModel] et
 * déclenchent les mêmes actions que les boutons à l'écran.
 */
@Composable
fun CameraScreen(onBack: () -> Unit, onOpenCompass: () -> Unit) {
    val viewModel: CameraViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val editing by viewModel.editing.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val diveManager = remember { DiveComputerManager.get(context) }
    val diveState by diveManager.uiState.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants[Manifest.permission.CAMERA] == true
    }
    val cameraPermissions = remember {
        if (Build.VERSION.SDK_INT >= 29) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(cameraPermissions)
        }
    }

    LaunchedEffect(state.lastEvent) {
        state.lastEvent?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) {
        diveManager.alerts.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Boussole activée via le menu caméra (déclencheur).
    LaunchedEffect(Unit) {
        viewModel.openCompass.collect { onOpenCompass() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(Unit) { viewModel.onPreviewReady(previewView, lifecycleOwner) }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.camera_permission), color = Color.White)
                Button(
                    onClick = { permissionLauncher.launch(cameraPermissions) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(stringResource(R.string.authorize)) }
            }
        }

        // Barre supérieure : retour + mode + indicateur d'enregistrement.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    when (state.mode) {
                        CameraMode.PHOTO -> R.string.photo
                        CameraMode.SELFIE -> R.string.self_portrait
                        CameraMode.VIDEO -> R.string.video
                        CameraMode.VIDEO_SELFIE -> R.string.video_self_portrait
                    }
                ),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.isRecording) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rec), color = Color.Red, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Boutons latéraux (côté droit) : AF, Mode, +, Menu, −.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundAction("AF", onClick = { viewModel.focus() })
            RoundAction("Mode", onClick = { viewModel.cycleMode() })
            RoundAction("+", onClick = { viewModel.zoomIn() })
            RoundAction("⚙", onClick = { viewModel.toggleSettings() })
            RoundAction("−", onClick = { viewModel.zoomOut() })
        }

        // Télémétrie de plongée (surimpression).
        DiveOverlay(
            state = diveState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp),
        )

        // Batteries (téléphone + boîtier) en bas à gauche.
        BatteryOverlay(
            boxBatteryPercent = diveState.batteryPercent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
        )

        // Panneau de réglages caméra.
        if (showSettings) {
            CameraSettingsPanel(
                state = state,
                selectedIndex = selectedIndex,
                editing = editing,
                onSelect = { viewModel.selectOption(it) },
                onCycleWhiteBalance = { viewModel.cycleWhiteBalance() },
                onCycleImageSize = { viewModel.cycleImageSize() },
                onCycleVideoQuality = { viewModel.cycleVideoQuality() },
                onExposure = { viewModel.adjustExposure(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 360.dp)
                    .padding(start = 12.dp, end = 12.dp, bottom = 120.dp),
            )
        }
    }
}

@Composable
private fun CameraSettingsPanel(
    state: CameraUiState,
    selectedIndex: Int,
    editing: Boolean,
    onSelect: (Int) -> Unit,
    onCycleWhiteBalance: () -> Unit,
    onCycleImageSize: () -> Unit,
    onCycleVideoQuality: () -> Unit,
    onExposure: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.78f),
    ) {
        Column(Modifier.padding(12.dp)) {
            OptionRow(
                stringResource(R.string.white_balance),
                state.whiteBalanceLabel,
                selected = selectedIndex == 0,
                editing = editing && selectedIndex == 0,
                onClick = { onSelect(0); onCycleWhiteBalance() },
            )
            OptionRow(
                stringResource(R.string.image_size),
                state.imageSizeLabel,
                selected = selectedIndex == 1,
                editing = editing && selectedIndex == 1,
                onClick = { onSelect(1); onCycleImageSize() },
            )
            OptionRow(
                stringResource(R.string.video_size),
                state.videoQualityLabel.ifEmpty { stringResource(R.string.auto) },
                selected = selectedIndex == 2,
                editing = editing && selectedIndex == 2,
                onClick = { onSelect(2); onCycleVideoQuality() },
            )
            ExposureRow(
                state.exposureIndex,
                selected = selectedIndex == 3,
                editing = editing && selectedIndex == 3,
                onSelect = { onSelect(3) },
                onExposure = onExposure,
            )
            // Boussole (action : activée par le déclencheur).
            OptionRow(
                stringResource(R.string.compass),
                "▶",
                selected = selectedIndex == 4,
                editing = false,
                onClick = { onSelect(4) },
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    value: String,
    selected: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        editing -> Color(0xFF4DD0E1).copy(alpha = 0.40f)
        selected -> Color(0xFF4DD0E1).copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Surface(onClick = onClick, color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            if (editing) Text("◀", color = Color(0xFF4DD0E1))
            Text(value, color = if (editing) Color(0xFF4DD0E1) else Color.White)
            if (editing) Text("▶", color = Color(0xFF4DD0E1))
        }
    }
}

@Composable
private fun ExposureRow(
    index: Int,
    selected: Boolean,
    editing: Boolean,
    onSelect: () -> Unit,
    onExposure: (Int) -> Unit,
) {
    val bg = when {
        editing -> Color(0xFF4DD0E1).copy(alpha = 0.40f)
        selected -> Color(0xFF4DD0E1).copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Surface(onClick = onSelect, color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.exposure), color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = { onExposure(-1) }) { Text("−", color = Color.White) }
            Text(
                if (index == 0) stringResource(R.string.auto) else index.toString(),
                color = if (editing) Color(0xFF4DD0E1) else Color.White,
            )
            TextButton(onClick = { onExposure(+1) }) { Text("+", color = Color.White) }
        }
    }
}

@Composable
private fun ShutterButton(isRecording: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.25f),
        modifier = Modifier.size(78.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = if (isRecording) RoundedCornerShape(8.dp) else CircleShape,
                color = if (isRecording) Color.Red else Color.White,
                modifier = Modifier.size(58.dp),
            ) {}
        }
    }
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp),
        ) {
            Text(label, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}
