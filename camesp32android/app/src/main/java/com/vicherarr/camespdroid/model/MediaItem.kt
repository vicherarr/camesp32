package com.vicherarr.camespdroid.model

data class MediaItem(
    val id: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long = 0,
    val timestamp: String = ""
) {
    /** True si el archivo es un clip de vídeo (AVI-MJPEG grabado por evento de movimiento). */
    val isVideo: Boolean
        get() = filename.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private companion object {
        val VIDEO_EXTENSIONS = setOf("avi", "mp4", "mkv", "mov")
    }
}
