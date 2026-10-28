@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divepilot.app.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Correction colorimétrique des photos sous-marines : applique un filtre
 * rouge (compense l'absorption du rouge par l'eau) et enregistre le résultat.
 */
@Composable
fun ColorCorrectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var intensity by remember { mutableFloatStateOf(0.5f) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> sourceUri = uri }

    val bitmap = remember(sourceUri) {
        sourceUri?.let { loadDownsampledBitmap(context, it, 1600) }
    }

    val scales = correctionScales(intensity)
    val colorFilter = ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(scaleMatrix(scales))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.color_correction_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bitmap == null) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.choose_photo_hint))
                Spacer(Modifier.height(12.dp))
                Button(onClick = { pickLauncher.launch("image/*") }) { Text(stringResource(R.string.choose_photo)) }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    colorFilter = colorFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.red_correction, (intensity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 0f..1f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { sourceUri = null }) { Text(stringResource(R.string.other_photo)) }
                    Button(onClick = {
                        val corrected = applyCorrection(bitmap, scales)
                        saveBitmapToGallery(
                            context,
                            corrected,
                            "CORR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
                        )
                    }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

private fun correctionScales(intensity: Float): FloatArray {
    val red = 1f + intensity * 1.5f
    val green = 1f - intensity * 0.1f
    val blue = 1f - intensity * 0.4f
    return floatArrayOf(red, green, blue, 1f)
}

private fun scaleMatrix(scales: FloatArray): FloatArray = floatArrayOf(
    scales[0], 0f, 0f, 0f, 0f,
    0f, scales[1], 0f, 0f, 0f,
    0f, 0f, scales[2], 0f, 0f,
    0f, 0f, 0f, scales[3], 0f,
)

private fun applyCorrection(source: Bitmap, scales: FloatArray): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(scaleMatrix(scales)))
    }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

private fun loadDownsampledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSize) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String) {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/DivePilot")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "DivePilot",
        )
        dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }
}
