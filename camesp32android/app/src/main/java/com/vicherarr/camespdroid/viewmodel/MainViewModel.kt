package com.vicherarr.camespdroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vicherarr.camespdroid.ble.BleManager
import com.vicherarr.camespdroid.ble.BleState
import com.vicherarr.camespdroid.model.MediaItem
import com.vicherarr.camespdroid.network.CameraRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val ipAddress: String = "192.168.4.1",
    val httpPort: String = "80",
    val username: String = "admin",
    val password: String = "admin123",
    val bleDeviceName: String = "CAM-ACTIVATE",
    val isCameraOnline: Boolean = false,
    val mediaList: List<MediaItem> = emptyList(),
    val isLoadingMedia: Boolean = false,
    val isLiveStreaming: Boolean = false,
    val selectedMedia: MediaItem? = null,
    val selectedTab: Int = 0, // 0: Control, 1: Live, 2: Gallery, 3: Settings
    val toastMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CameraRepository()
    val bleManager = BleManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val bleState: StateFlow<BleState> = bleManager.bleState

    private var pingJob: Job? = null

    init {
        startPingLoop()
    }

    val baseUrl: String
        get() = "http://${uiState.value.ipAddress}:${uiState.value.httpPort}"

    fun triggerBleWakeup() {
        bleManager.triggerWakeup(uiState.value.bleDeviceName)
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        if (index == 1 || index == 2) {
            refreshMediaList()
        }
    }

    fun updateSettings(ip: String, port: String, user: String, pass: String, bleName: String) {
        _uiState.value = _uiState.value.copy(
            ipAddress = ip,
            httpPort = port,
            username = user,
            password = pass,
            bleDeviceName = bleName
        )
        refreshMediaList()
    }

    fun refreshMediaList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMedia = true)
            val list = repository.fetchMediaList(baseUrl, uiState.value.username, uiState.value.password)
            _uiState.value = _uiState.value.copy(mediaList = list, isLoadingMedia = false)
        }
    }

    fun triggerPhotoCapture() {
        viewModelScope.launch {
            val success = repository.triggerCapture(baseUrl, uiState.value.username, uiState.value.password)
            if (success) {
                _uiState.value = _uiState.value.copy(toastMessage = "¡Foto capturada con éxito!")
                refreshMediaList()
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "Error al capturar foto")
            }
        }
    }

    fun selectMediaItem(item: MediaItem?) {
        _uiState.value = _uiState.value.copy(selectedMedia = item)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (true) {
                val isOnline = repository.pingCamera(baseUrl, uiState.value.username, uiState.value.password)
                _uiState.value = _uiState.value.copy(isCameraOnline = isOnline)
                delay(5000)
            }
        }
    }
}
