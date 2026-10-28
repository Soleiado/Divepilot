package com.divepilot.app.camera

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.divepilot.app.ble.BleCommand
import com.divepilot.app.ble.BleManager
import com.divepilot.app.ble.DecodedFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager.get(application)
    private val controller = CameraController(application)

    val uiState: StateFlow<CameraUiState> = controller.state

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _openCompass = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openCompass: SharedFlow<Unit> = _openCompass.asSharedFlow()

    init {
        viewModelScope.launch {
            bleManager.frames.collect { frame ->
                if (frame is DecodedFrame.Command) onCommand(frame.command)
            }
        }
    }

    fun onPreviewReady(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        controller.bind(previewView, lifecycleOwner)
    }

    fun cycleMode() = controller.cycleMode()
    fun focus() = controller.focus()
    fun zoomIn() = controller.zoomBy(+0.1f)
    fun zoomOut() = controller.zoomBy(-0.1f)
    fun shutter() = controller.shutter()

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
        _editing.value = false
        _selectedIndex.value = 0
    }

    /** Sélectionne une option (tap à l'écran). La boussole se déclenche directement. */
    fun selectOption(index: Int) {
        if (index == COMPASS_INDEX) {
            openCompassFromMenu()
            return
        }
        _selectedIndex.value = index
        _editing.value = true
    }

    /** Navigue entre les options : -1 = vers le haut, +1 = vers le bas. */
    fun navigate(direction: Int) {
        _selectedIndex.value = (_selectedIndex.value + direction + OPTION_COUNT) % OPTION_COUNT
    }

    /** Déclencheur : active la boussole, sinon sélectionne/valide la valeur. */
    fun toggleSelect() {
        if (_selectedIndex.value == COMPASS_INDEX) {
            openCompassFromMenu()
            return
        }
        _editing.value = !_editing.value
    }

    private fun openCompassFromMenu() {
        _showSettings.value = false
        _editing.value = false
        _selectedIndex.value = 0
        _openCompass.tryEmit(Unit)
    }

    /** Ajuste la valeur de l'option sélectionnée (direction : +1 / -1). */
    fun adjustValue(direction: Int) {
        when (_selectedIndex.value) {
            0 -> controller.cycleWhiteBalance(direction)
            1 -> controller.cycleImageSize()
            2 -> controller.cycleVideoQuality(direction)
            3 -> controller.adjustExposure(direction)
        }
    }

    fun cycleWhiteBalance() = controller.cycleWhiteBalance()
    fun cycleImageSize() = controller.cycleImageSize()
    fun cycleVideoQuality() = controller.cycleVideoQuality()
    fun adjustExposure(delta: Int) = controller.adjustExposure(delta)

    private fun onCommand(command: BleCommand) {
        when (command) {
            BleCommand.FOCUS -> controller.focus()
            BleCommand.PHOTO_VIDEO_TOGGLE -> controller.cycleMode()
            BleCommand.SHUTTER ->
                if (_showSettings.value) toggleSelect() else controller.shutter()
            BleCommand.ZOOM_IN_A, BleCommand.ZOOM_IN_B ->
                if (_showSettings.value) {
                    if (_editing.value) adjustValue(+1) else navigate(-1)
                } else {
                    controller.zoomBy(+0.1f)
                }
            BleCommand.ZOOM_OUT_A, BleCommand.ZOOM_OUT_B ->
                if (_showSettings.value) {
                    if (_editing.value) adjustValue(-1) else navigate(+1)
                } else {
                    controller.zoomBy(-0.1f)
                }
            // Flash (f10d) désactivé : la lumière se réfléchit dans le boîtier.
            BleCommand.FLASH -> Unit
            BleCommand.MENU_A, BleCommand.MENU_B -> toggleSettings()
            BleCommand.COMPASS -> Unit
        }
    }

    override fun onCleared() {
        controller.shutdown()
        super.onCleared()
    }

    companion object {
        const val OPTION_COUNT = 5
        const val COMPASS_INDEX = 4
    }
}
