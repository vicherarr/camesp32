package com.vicherarr.camespdroid.model

data class MediaItem(
    val id: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long = 0,
    val timestamp: String = ""
)
