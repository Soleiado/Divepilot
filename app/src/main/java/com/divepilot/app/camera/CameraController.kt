package com.divepilot.app.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.divepilot.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class CameraMode { PHOTO, SELFIE, VIDEO, VIDEO_SELFIE }

data class CameraUiState(
    val mode: CameraMode = CameraMode.PHOTO,
    val isRecording: Boolean = false,
    val zoom: Float = 0f,
    val whiteBalanceLabel: String = "Auto",
    val imageSizeLabel: String = "Grande",
    val videoQualityLabel: String = "",
    val exposureIndex: Int = 0,
    val lastEvent: String? = null,
)

/**
 * Encapsule la pile CameraX : aperçu, photo, vidéo, zoom, mise au point
 * et bascule avant/arrière. Les médias sont enregistrés dans MediaStore
 * (dossier DCIM/DivePilot).
 */
class CameraController(private val context: Context) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private val settingsRepository = CameraSettingsRepository(context)
    private var cameraSettings = settingsRepository.load()
    private val whiteBalanceModes = listOf(
        R.string.wb_auto to CaptureRequest.CONTROL_AWB_MODE_AUTO,
        R.string.wb_incandescent to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
        R.string.wb_fluorescent to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
        R.string.wb_daylight to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
        R.string.wb_cloudy to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
    )
    private var supportedQualities = listOf<Quality>()

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var recording: Recording? = null
    private var videoLegacyFile: File? = null
    private var camera: Camera? = null

    private var mode = CameraMode.PHOTO

    private fun isVideoMode() = mode == CameraMode.VIDEO || mode == CameraMode.VIDEO_SELFIE
    private fun isFrontCamera() = mode == CameraMode.SELFIE || mode == CameraMode.VIDEO_SELFIE

    private val _state = MutableStateFlow(
        CameraUiState(
            whiteBalanceLabel = context.getString(whiteBalanceModes[cameraSettings.whiteBalance].first),
            imageSizeLabel = context.getString(if (cameraSettings.imageSize == 0) R.string.large else R.string.small),
            exposureIndex = cameraSettings.exposureIndex,
        )
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Initialisation caméra impossible", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val view = previewView ?: return
        val owner = lifecycleOwner ?: return

        provider.unbindAll()
        camera = null

        val lensFacing = if (isFrontCamera())
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val previewUseCase = Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        preview = previewUseCase

        try {
            if (isVideoMode()) {
                val rec = Recorder.Builder()
                    .setQualitySelector(videoQualitySelector())
                    .build()
                recorder = rec
                videoCapture = VideoCapture.withOutput(rec)
                camera = provider.bindToLifecycle(owner, selector, previewUseCase, videoCapture)
            } else {
                imageCapture = buildImageCapture()
                camera = provider.bindToLifecycle(owner, selector, previewUseCase, imageCapture)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Échec de liaison caméra, repli en mode photo", e)
            _state.update { it.copy(mode = CameraMode.PHOTO, lastEvent = context.getString(R.string.video_unavailable)) }
            imageCapture = buildImageCapture()
            camera = provider.bindToLifecycle(owner, selector, previewUseCase, imageCapture)
        }

        // Met à jour les qualités vidéo supportées et applique l'exposition.
        camera?.let { cam ->
            supportedQualities = QualitySelector.getSupportedQualities(cam.cameraInfo)
            val label = if (supportedQualities.isNotEmpty() &&
                cameraSettings.videoQuality < supportedQualities.size
            ) {
                qualityLabel(supportedQualities[cameraSettings.videoQuality])
            } else {
                ""
            }
            _state.update { it.copy(videoQualityLabel = label) }
        }
        applyExposure()
    }

    fun cycleMode() {
        if (_state.value.isRecording) stopVideo()
        mode = when (mode) {
            CameraMode.PHOTO -> CameraMode.SELFIE
            CameraMode.SELFIE -> CameraMode.VIDEO
            CameraMode.VIDEO -> CameraMode.VIDEO_SELFIE
            CameraMode.VIDEO_SELFIE -> CameraMode.PHOTO
        }
        _state.update { it.copy(mode = mode) }
        bindUseCases()
    }

    fun focus() {
        val cam = camera ?: return
        val view = previewView ?: return
        try {
            val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
            // Mise au point seule (FLAG_AF) : on évite FLAG_AE qui peut faire
            // basculer l'aperçu en noir sur certains appareils.
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.e(TAG, "Mise au point impossible", e)
        }
    }

    fun cycleWhiteBalance(direction: Int = 1) {
        val size = whiteBalanceModes.size
        cameraSettings = cameraSettings.copy(
            whiteBalance = (cameraSettings.whiteBalance + direction + size) % size
        )
        settingsRepository.save(cameraSettings)
        _state.update { it.copy(whiteBalanceLabel = context.getString(whiteBalanceModes[cameraSettings.whiteBalance].first)) }
        if (!isVideoMode()) bindUseCases()
    }

    fun cycleImageSize() {
        cameraSettings = cameraSettings.copy(imageSize = (cameraSettings.imageSize + 1) % 2)
        settingsRepository.save(cameraSettings)
        _state.update { it.copy(imageSizeLabel = context.getString(if (cameraSettings.imageSize == 0) R.string.large else R.string.small)) }
        if (!isVideoMode()) bindUseCases()
    }

    fun cycleVideoQuality(direction: Int = 1) {
        if (supportedQualities.isEmpty()) {
            _state.update { it.copy(lastEvent = context.getString(R.string.qualities_unavailable)) }
            return
        }
        val size = supportedQualities.size
        cameraSettings = cameraSettings.copy(
            videoQuality = (cameraSettings.videoQuality + direction + size) % size
        )
        settingsRepository.save(cameraSettings)
        _state.update { it.copy(videoQualityLabel = qualityLabel(supportedQualities[cameraSettings.videoQuality])) }
        if (isVideoMode()) bindUseCases()
    }

    fun adjustExposure(delta: Int) {
        val cam = camera ?: return
        val exposureState = cam.cameraInfo.exposureState
        if (!exposureState.isExposureCompensationSupported) {
            _state.update { it.copy(lastEvent = context.getString(R.string.exposure_unsupported)) }
            return
        }
        val range = exposureState.exposureCompensationRange
        val index = (cameraSettings.exposureIndex + delta).coerceIn(range.lower, range.upper)
        cameraSettings = cameraSettings.copy(exposureIndex = index)
        settingsRepository.save(cameraSettings)
        cam.cameraControl.setExposureCompensationIndex(index)
        _state.update { it.copy(exposureIndex = index) }
    }

    fun zoomBy(delta: Float) {
        val cam = camera ?: return
        val current = cam.cameraInfo.zoomState.value?.linearZoom ?: 0f
        val target = (current + delta).coerceIn(0f, 1f)
        cam.cameraControl.setLinearZoom(target)
        _state.update { it.copy(zoom = target) }
    }

    /** Déclencheur : photo en mode photo, démarrage/arrêt en mode vidéo. */
    fun shutter() {
        if (isVideoMode()) {
            if (_state.value.isRecording) stopVideo() else startVideo()
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = "IMG_${dateFormat.format(Date())}.jpg"
        val legacyFile: File? = if (Build.VERSION.SDK_INT < 29) legacyMediaFile(name) else null
        val output = if (Build.VERSION.SDK_INT >= 29) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/DivePilot")
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } else {
            ImageCapture.OutputFileOptions.Builder(legacyFile!!).build()
        }
        capture.takePicture(output, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                legacyFile?.let { scanLegacyFile(it) }
                _state.update { it.copy(lastEvent = context.getString(R.string.photo_saved)) }
            }

            override fun onError(exception: ImageCaptureException) {
                _state.update { it.copy(lastEvent = context.getString(R.string.photo_error, exception.message)) }
            }
        })
    }

    private fun startVideo() {
        val rec = recorder ?: return
        val name = "VID_${dateFormat.format(Date())}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/DivePilot")
            }
            val output = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            beginRecording(rec) { rec.prepareRecording(context, output) }
        } else {
            videoLegacyFile = legacyMediaFile(name)
            val output = FileOutputOptions.Builder(videoLegacyFile!!).build()
            beginRecording(rec) { rec.prepareRecording(context, output) }
        }
    }

    private fun beginRecording(rec: Recorder, prepare: () -> PendingRecording) {
        try {
            recording = prepare().withAudioEnabled().start(executor) { handleVideoEvent(it) }
        } catch (e: SecurityException) {
            // Pas de permission RECORD_AUDIO : on enregistre sans audio.
            recording = prepare().start(executor) { handleVideoEvent(it) }
        }
    }

    private fun handleVideoEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> _state.update { it.copy(isRecording = true) }
            is VideoRecordEvent.Finalize -> {
                _state.update { it.copy(isRecording = false) }
                if (event.hasError()) {
                    _state.update { it.copy(lastEvent = context.getString(R.string.video_error, event.error)) }
                } else {
                    videoLegacyFile?.let { scanLegacyFile(it) }
                    _state.update { it.copy(lastEvent = context.getString(R.string.video_saved)) }
                }
                videoLegacyFile = null
            }
            else -> Unit
        }
    }

    private fun stopVideo() {
        recording?.stop()
        recording = null
        _state.update { it.copy(isRecording = false) }
    }

    @Suppress("DEPRECATION")
    private fun legacyMediaFile(name: String): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "DivePilot",
        )
        dir.mkdirs()
        return File(dir, name)
    }

    private fun scanLegacyFile(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    private fun buildImageCapture(): ImageCapture {
        val builder = ImageCapture.Builder()
            .setCaptureMode(
                if (cameraSettings.imageSize == 0) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
        Camera2Interop.Extender(builder).setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            whiteBalanceModes[cameraSettings.whiteBalance].second,
        )
        return builder.build()
    }

    private fun videoQualitySelector(): QualitySelector {
        val target = if (supportedQualities.isNotEmpty() &&
            cameraSettings.videoQuality < supportedQualities.size
        ) {
            supportedQualities[cameraSettings.videoQuality]
        } else {
            Quality.FHD
        }
        return QualitySelector.from(target, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
    }

    private fun applyExposure() {
        val cam = camera ?: return
        val exposureState = cam.cameraInfo.exposureState
        if (!exposureState.isExposureCompensationSupported) return
        val index = cameraSettings.exposureIndex.coerceIn(
            exposureState.exposureCompensationRange.lower,
            exposureState.exposureCompensationRange.upper,
        )
        cam.cameraControl.setExposureCompensationIndex(index)
        _state.update { it.copy(exposureIndex = index) }
    }

    private fun qualityLabel(q: Quality): String = when (q) {
        Quality.UHD -> "4K"
        Quality.FHD -> "1080p"
        Quality.HD -> "720p"
        Quality.SD -> "480p"
        else -> q.toString()
    }

    fun shutdown() {
        recording?.stop()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "CameraController"
    }
}
