package com.vicherarr.camespdroid.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vicherarr.camespdroid.model.MediaItem
import com.vicherarr.camespdroid.network.CameraRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val ipAddress: String = "192.168.1.143",
    val httpPort: String = "80",
    val username: String = "admin",
    val password: String = "admin123",
    val isCameraOnline: Boolean = false,
    val currentCameraMode: String = "",
    val isMotionDetected: Boolean = false,
    val mediaList: List<MediaItem> = emptyList(),
    val isLoadingMedia: Boolean = false,
    val isLiveStreaming: Boolean = false,
    val selectedMedia: MediaItem? = null,
    val selectedTab: Int = 0, // 0: Control, 1: Live, 2: Gallery, 3: Settings
    val toastMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CameraRepository()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pingJob: Job? = null

    init {
        startPingLoop()
    }

    val baseUrl: String
        get() = "http://${uiState.value.ipAddress}:${uiState.value.httpPort}"

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        if (index == 1 || index == 2) {
            refreshMediaList()
        }
    }

    fun updateSettings(ip: String, port: String, user: String, pass: String) {
        _uiState.value = _uiState.value.copy(
            ipAddress = ip,
            httpPort = port,
            username = user,
            password = pass
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
                if (!uiState.value.isCameraOnline) {
                    val discoveredIp = repository.discoverCameraIP()
                    if (discoveredIp != null && discoveredIp != uiState.value.ipAddress) {
                        _uiState.value = _uiState.value.copy(ipAddress = discoveredIp)
                    }
                }

                val currentBaseUrl = "http://${uiState.value.ipAddress}:${uiState.value.httpPort}"
                val status = repository.pingCamera(currentBaseUrl, uiState.value.username, uiState.value.password)
                _uiState.value = _uiState.value.copy(
                    isCameraOnline = status.isOnline,
                    currentCameraMode = status.mode,
                    isMotionDetected = status.isMotionDetected
                )
                delay(5000)
            }
        }
    }

    fun sendEspConfig(mode: String, ssid: String, wifiPass: String) {
        viewModelScope.launch {
            val success = repository.sendEspConfig(baseUrl, uiState.value.username, uiState.value.password, mode, ssid, wifiPass)
            if (success) {
                _uiState.value = _uiState.value.copy(toastMessage = "Configuración enviada al ESP32 con éxito")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "Error al enviar configuración al ESP32")
            }
        }
    }
}
