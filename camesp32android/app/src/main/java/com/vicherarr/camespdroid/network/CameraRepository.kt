package com.vicherarr.camespdroid.network

import com.vicherarr.camespdroid.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CameraStatus(val isOnline: Boolean, val mode: String = "", val isMotionDetected: Boolean = false)

class CameraRepository {

    suspend fun discoverCameraIP(): String? = withContext(Dispatchers.IO) {
        var socket: java.net.DatagramSocket? = null
        try {
            socket = java.net.DatagramSocket()
            socket.broadcast = true
            val sendData = "DISCOVER_CAMESP32".toByteArray()
            val sendPacket = java.net.DatagramPacket(
                sendData, sendData.size,
                java.net.InetAddress.getByName("255.255.255.255"), 5555
            )
            socket.send(sendPacket)

            socket.soTimeout = 2000
            val recvBuf = ByteArray(1024)
            val receivePacket = java.net.DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(receivePacket)

            val msg = String(receivePacket.data, 0, receivePacket.length)
            if (msg.trim() == "CAMESP32_HERE") {
                return@withContext receivePacket.address.hostAddress
            }
        } catch (e: Exception) {
            // timeout or error
        } finally {
            socket?.close()
        }
        null
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun pingCamera(baseUrl: String, user: String, pass: String): CameraStatus = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url("$baseUrl/info")
                .header("Authorization", credential)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val mode = json.optString("mode", "")
                val motion = json.optBoolean("motion", false)
                CameraStatus(isOnline = true, mode = mode, isMotionDetected = motion)
            } else {
                CameraStatus(isOnline = false)
            }
        } catch (e: Exception) {
            CameraStatus(isOnline = false)
        }
    }

    suspend fun fetchMediaList(baseUrl: String, user: String, pass: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url("$baseUrl/photos")
                .header("Authorization", credential)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: ""
            // Parse HTML directory listing
            val items = mutableListOf<MediaItem>()
            val hrefRegex = Regex("href=[\"'](/file/[^\"']+\\.(?:jpg|jpeg|png|mp4|avi))[\"']", RegexOption.IGNORE_CASE)
            var index = 0
            hrefRegex.findAll(body).forEach { match ->
                val uriPath = match.groupValues[1]
                val fullUrl = "$baseUrl$uriPath"
                val filename = uriPath.removePrefix("/file/")
                items.add(
                    MediaItem(
                        id = "media_${index++}",
                        filename = filename,
                        url = fullUrl
                    )
                )
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Descarga un fotograma JPEG de /photo (para la vista En Vivo por snapshots). */
    suspend fun fetchSnapshot(baseUrl: String, user: String, pass: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url("$baseUrl/photo")
                .header("Authorization", credential)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.bytes() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun triggerCapture(baseUrl: String, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url("$baseUrl/capture")
                .header("Authorization", credential)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /** Borra TODAS las fotos de la SD del dispositivo (POST /deleteall). */
    suspend fun deleteAllPhotos(baseUrl: String, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val body = "".toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$baseUrl/deleteall")
                .header("Authorization", credential)
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendEspConfig(baseUrl: String, user: String, pass: String, mode: String, ssid: String, wifiPass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val json = JSONObject()
            json.put("mode", mode)
            // ssid and wifiPass are ignored now

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url("$baseUrl/config")
                .header("Authorization", credential)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
