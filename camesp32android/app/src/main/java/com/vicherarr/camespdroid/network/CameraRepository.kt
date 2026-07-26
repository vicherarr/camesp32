package com.vicherarr.camespdroid.network

import com.vicherarr.camespdroid.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CameraRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun pingCamera(baseUrl: String, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(baseUrl)
                .header("Authorization", credential)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchMediaList(baseUrl: String, user: String, pass: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url("$baseUrl/sdcard/")
                .header("Authorization", credential)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: ""
            // Parse HTML directory listing or JSON files from SD card
            val items = mutableListOf<MediaItem>()
            val hrefRegex = Regex("href=[\"']([^\"']+\\.(?:jpg|jpeg|png|mp4|avi))[\"']", RegexOption.IGNORE_CASE)
            var index = 0
            hrefRegex.findAll(body).forEach { match ->
                val filename = match.groupValues[1]
                val fullUrl = if (filename.startsWith("http")) filename else "$baseUrl/sdcard/$filename"
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
}
