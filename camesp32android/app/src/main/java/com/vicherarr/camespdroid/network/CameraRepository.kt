package com.vicherarr.camespdroid.network

import com.vicherarr.camespdroid.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP de la media de la cámara. Todas las llamadas se hacen durante una **sesión WiFi**
 * (la cámara ha levantado su AP tras el comando BLE), usando el cliente enrutado por el enlace
 * local ([com.vicherarr.camespdroid.network.WifiLink]). En modo AP la IP es fija: 192.168.71.1.
 */
class CameraRepository {

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.71.1"
    }

    /** Cliente de reserva (por si no hay enlace local); normalmente se pasa el de WifiLink. */
    private val fallback: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun http(client: OkHttpClient?) = client ?: fallback

    suspend fun fetchMediaList(client: OkHttpClient?, baseUrl: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/photos").build()
            val response = http(client).newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: ""
            val items = mutableListOf<MediaItem>()
            val hrefRegex = Regex("href=[\"'](/file/[^\"']+\\.(?:jpg|jpeg|png|mp4|avi))[\"']", RegexOption.IGNORE_CASE)
            var index = 0
            hrefRegex.findAll(body).forEach { match ->
                val uriPath = match.groupValues[1]
                items.add(
                    MediaItem(
                        id = "media_${index++}",
                        filename = uriPath.removePrefix("/file/"),
                        url = "$baseUrl$uriPath"
                    )
                )
            }
            items.sortedByDescending { it.filename } // más recientes primero (nombres con fecha-hora)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Descarga un fotograma JPEG de /photo (visor En Vivo por snapshots). */
    suspend fun fetchSnapshot(client: OkHttpClient?, baseUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/photo").build()
            val response = http(client).newCall(request).execute()
            if (response.isSuccessful) response.body?.bytes() else null
        } catch (e: Exception) {
            null
        }
    }

    /** Descarga un archivo cualquiera de la SD (para el reproductor de vídeo AVI). */
    suspend fun downloadFile(client: OkHttpClient?, url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = http(client).newCall(request).execute()
            if (response.isSuccessful) response.body?.bytes() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun triggerCapture(client: OkHttpClient?, baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/capture").build()
            http(client).newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAllPhotos(cliesolnt: OkHttpClient?, baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = "".toRequestBody(null)
            val request = Request.Builder().url("$baseUrl/deleteall").post(body).build()
            http(client).newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /** Pide a la cámara cerrar la sesión WiFi y volver a BLE (POST /wifi_off). */
    suspend fun requestWifiOff(client: OkHttpClient?, baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = "".toRequestBody(null)
            val request = Request.Builder().url("$baseUrl/wifi_off").post(body).build()
            http(client).newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
