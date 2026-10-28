@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.divepilot.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divepilot.app.R
import com.divepilot.app.ble.BleViewModel
import com.divepilot.app.ble.ScannedDevice

/**
 * Écran d'accueil : connexion Bluetooth au boîtier et accès aux différentes fonctions.
 */
@Composable
fun BleCaptureScreen(
    onOpenCamera: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenColorCorrection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: BleViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshBondedDevices() }

    Box(Modifier.fillMaxSize()) {
        // Image de fond (corail + poissons) avec voile sombre pour la lisibilité.
        Image(
            painter = painterResource(R.drawable.bg_home),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.capture_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
            // Options : grille 2 colonnes, boutons de même taille.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionButton(stringResource(R.string.camera), onOpenCamera, Modifier.weight(1f))
                    OptionButton(stringResource(R.string.journal), onOpenJournal, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionButton(stringResource(R.string.gallery), onOpenGallery, Modifier.weight(1f))
                    OptionButton(stringResource(R.string.correction), onOpenColorCorrection, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionButton(stringResource(R.string.settings), onOpenSettings, Modifier.weight(1f))
                    OptionButton(stringResource(R.string.guide), onOpenGuide, Modifier.weight(1f))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Scanner + statut (juste au-dessus de la liste des appareils).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                    Text(stringResource(if (state.isScanning) R.string.scanning else R.string.scan))
                }
                Text(
                    stringResource(R.string.status, state.connectionStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.isScanning) {
                    OutlinedButton(onClick = { viewModel.stopScan() }) { Text(stringResource(R.string.stop)) }
                }
                if (state.connectedDevice != null) {
                    OutlinedButton(onClick = { viewModel.disconnect() }) { Text(stringResource(R.string.disconnect)) }
                }
            }

            Text(
                stringResource(R.string.devices),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (state.devices.isEmpty()) {
                Text(
                    stringResource(R.string.no_devices),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(state.devices) { device ->
                    DeviceRow(device) { viewModel.connect(device) }
                }
            }
        }
    }
    }
}

@Composable
private fun OptionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color.White),
    ) {
        Text(label, fontSize = 18.sp)
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    (device.name ?: stringResource(R.string.unknown)) +
                        if (device.bonded) "  ${stringResource(R.string.paired)}" else ""
                )
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}
