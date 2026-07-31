package com.vicherarr.camespdroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vicherarr.camespdroid.ble.BleManager
import com.vicherarr.camespdroid.ble.BleStatus
import com.vicherarr.camespdroid.model.MediaItem
import com.vicherarr.camespdroid.network.CameraRepository
import com.vicherarr.camespdroid.network.WifiLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la sesión de media por WiFi. */
enum class MediaSession { None, Opening, Active, Error }

data class UiState(
    val bleConnected: Boolean = false,
    val bleScanning: Boolean = false,
    val bleError: String? = null,
    val bleDevicesSeen: Int = 0,
    val bleCameraSeen: Boolean = false,
    val armed: Boolean = false,
    val motion: Boolean = false,
    val deviceWifiOn: Boolean = false,
    val mediaSession: MediaSession = MediaSession.None,
    val mediaList: List<MediaItem> = emptyList(),
    val isLoadingMedia: Boolean = false,
    val snapshot: ByteArray? = null,
    val selectedMedia: MediaItem? = null,
    val selectedClipBytes: ByteArray? = null,
    val loadingClip: Boolean = false,
    val selectedTab: Int = 0, // 0 Inicio, 1 En Vivo, 2 Galería, 3 Ajustes
    val toastMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val AP_SSID = "MIWIFI"
        const val AP_PASS = "moto1112"
        const val BASE_URL = CameraRepository.DEFAULT_BASE_URL
    }

    private val ble = BleManager(application)
    private val wifiLink = WifiLink(application)
    private val repo = CameraRepository()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var snapshotJob: Job? = null

    init {
        viewModelScope.launch {
            ble.status.collect { st -> onBleStatus(st) }
        }
    }

    /** La UI llama a esto una vez concedidos los permisos de Bluetooth. */
    fun connectBle() {
        if (_uiState.value.mediaSession != MediaSession.None) return
        ble.startScanAndConnect()
    }

    private fun onBleStatus(st: BleStatus) {
        when (st) {
            is BleStatus.Scanning -> _uiState.value = _uiState.value.copy(
                bleScanning = true, bleConnected = false, bleError = null,
                bleDevicesSeen = st.devicesSeen, bleCameraSeen = st.cameraSeen
            )
            is BleStatus.Connecting -> _uiState.value = _uiState.value.copy(bleScanning = true, bleConnected = false, bleCameraSeen = true)
            is BleStatus.Connected -> {
                val first = !_uiState.value.bleConnected
                _uiState.value = _uiState.value.copy(
                    bleScanning = false, bleConnected = true, bleError = null,
                    armed = st.state.armed, motion = st.state.motion, deviceWifiOn = st.state.wifiOn
                )
                // Al conectar por primera vez, sincroniza la hora (epoch local en ms). Con un
                // pequeño retraso para no chocar con la activación de notificaciones (una op GATT
                // cada vez).
                if (first) {
                    viewModelScope.launch {
                        delay(700)
                        val offset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())
                        ble.setTime(System.currentTimeMillis() + offset)
                    }
                }
            }
            is BleStatus.Disconnected -> {
                _uiState.value = _uiState.value.copy(bleScanning = false, bleConnected = false)
                if (_uiState.value.mediaSession == MediaSession.None) {
                    viewModelScope.launch {
                        delay(2000)
                        if (_uiState.value.mediaSession == MediaSession.None) {
                            connectBle()
                        }
                    }
                }
            }
            is BleStatus.Error -> _uiState.value = _uiState.value.copy(bleScanning = false, bleError = st.msg)
            is BleStatus.Idle -> {}
        }
    }

    // ---- Control de alarma (BLE) ----
    fun arm() {
        ble.arm()
        _uiState.value = _uiState.value.copy(armed = true, toastMessage = "Alarma armada")
    }

    fun disarm() {
        ble.disarm()
        _uiState.value = _uiState.value.copy(armed = false, toastMessage = "Alarma desarmada")
    }

    // ---- Sesión de media (WiFi bajo demanda) ----
    fun openMediaSession() {
        if (_uiState.value.mediaSession == MediaSession.Active ||
            _uiState.value.mediaSession == MediaSession.Opening) return
        _uiState.value = _uiState.value.copy(mediaSession = MediaSession.Opening, toastMessage = "Encendiendo WiFi de la cámara…")
        // 1) Pide a la cámara que levante su AP (por BLE). La cámara apagará BLE.
        ble.wifiOn()
        viewModelScope.launch {
            delay(2500) // deja que la cámara deinit BLE y levante el AP
            ble.disconnect()
            // 2) Enlaza el móvil al AP de forma local (sin cambiar la red del teléfono).
            wifiLink.connect(AP_SSID, AP_PASS) { ok ->
                viewModelScope.launch {
                    if (ok) {
                        _uiState.value = _uiState.value.copy(mediaSession = MediaSession.Active, deviceWifiOn = true)
                        refreshMediaList()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            mediaSession = MediaSession.Error,
                            toastMessage = "No se pudo enlazar al WiFi de la cámara"
                        )
                    }
                }
            }
        }
    }

    fun closeMediaSession() {
        stopLiveSnapshots()
        viewModelScope.launch {
            // Pide a la cámara cerrar WiFi y volver a BLE, suelta el enlace y reconecta BLE.
            repo.requestWifiOff(wifiLink.httpClient(), BASE_URL)
            wifiLink.release()
            _uiState.value = _uiState.value.copy(
                mediaSession = MediaSession.None, deviceWifiOn = false,
                snapshot = null, selectedClipBytes = null, selectedMedia = null
            )
            delay(1500)
            ble.startScanAndConnect()
        }
    }

    // ---- Galería ----
    fun refreshMediaList() {
        if (_uiState.value.mediaSession != MediaSession.Active) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMedia = true)
            val list = repo.fetchMediaList(wifiLink.httpClient(), BASE_URL)
            _uiState.value = _uiState.value.copy(mediaList = list, isLoadingMedia = false)
        }
    }

    fun triggerPhotoCapture() {
        viewModelScope.launch {
            val ok = repo.triggerCapture(wifiLink.httpClient(), BASE_URL)
            _uiState.value = _uiState.value.copy(toastMessage = if (ok) "¡Foto capturada!" else "Error al capturar")
            if (ok) refreshMediaList()
        }
    }

    fun deleteAllPhotos() {
        viewModelScope.launch {
            val ok = repo.deleteAllPhotos(wifiLink.httpClient(), BASE_URL)
            _uiState.value = _uiState.value.copy(
                toastMessage = if (ok) "SD borrada" else "Error al borrar",
                mediaList = if (ok) emptyList() else _uiState.value.mediaList
            )
        }
    }

    fun selectMedia(item: MediaItem?) {
        _uiState.value = _uiState.value.copy(selectedMedia = item, selectedClipBytes = null)
        if (item != null && item.isVideo) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(loadingClip = true)
                val bytes = repo.downloadFile(wifiLink.httpClient(), item.url)
                _uiState.value = _uiState.value.copy(selectedClipBytes = bytes, loadingClip = false)
            }
        }
    }

    // ---- Visor En Vivo (snapshots) ----
    fun startLiveSnapshots() {
        if (_uiState.value.mediaSession != MediaSession.Active) return
        if (snapshotJob?.isActive == true) return
        snapshotJob = viewModelScope.launch {
            while (true) {
                val bytes = repo.fetchSnapshot(wifiLink.httpClient(), BASE_URL)
                if (bytes != null) _uiState.value = _uiState.value.copy(snapshot = bytes)
                delay(400)
            }
        }
    }

    fun stopLiveSnapshots() {
        snapshotJob?.cancel()
        snapshotJob = null
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        if (index != 1) stopLiveSnapshots()
        if (index == 2) refreshMediaList()
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveSnapshots()
        wifiLink.release()
        ble.disconnect()
    }

    // Cliente enrutado por el enlace local, para que Coil (galería) cargue imágenes por el AP.
    fun boundImageLoader(context: android.content.Context): coil.ImageLoader {
        val client = wifiLink.httpClient()
        val builder = coil.ImageLoader.Builder(context)
        return if (client != null) builder.okHttpClient(client).build() else builder.build()
    }
}
