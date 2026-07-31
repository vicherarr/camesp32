package com.vicherarr.camespdroid.ui.video

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Parser mínimo de AVI-MJPEG: extrae los fotogramas JPEG (chunks `00dc`) y el ritmo (fps). */
object AviParser {
    data class Clip(val frames: List<ByteArray>, val frameDelayMs: Long)

    fun parse(data: ByteArray): Clip {
        val frames = mutableListOf<ByteArray>()
        // dwMicroSecPerFrame está en el offset 32 del header fijo que escribe el firmware.
        val micros = if (data.size > 36) le32(data, 32) else 0L
        val moviIdx = indexOf(data, "movi", 12)
        if (moviIdx < 0) return Clip(frames, delayOf(micros))
        var p = moviIdx + 4 // saltar el fourcc 'movi'
        while (p + 8 <= data.size) {
            val fourcc = ascii(data, p, 4)
            val size = le32(data, p + 4).toInt()
            val dataStart = p + 8
            if (fourcc == "idx1" || size < 0) break
            if (fourcc.endsWith("dc") && size > 0 && dataStart + size <= data.size) {
                frames.add(data.copyOfRange(dataStart, dataStart + size))
            }
            p = dataStart + size + (size and 1) // alineación a 2 bytes
        }
        return Clip(frames, delayOf(micros))
    }

    private fun delayOf(micros: Long): Long =
        if (micros in 1..2_000_000) micros / 1000 else 300L

    private fun le32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    private fun ascii(b: ByteArray, o: Int, n: Int): String =
        if (o + n <= b.size) String(b, o, n, Charsets.US_ASCII) else ""

    private fun indexOf(haystack: ByteArray, needle: String, from: Int): Int {
        val n = needle.toByteArray(Charsets.US_ASCII)
        var i = from
        outer@ while (i + n.size <= haystack.size) {
            for (j in n.indices) if (haystack[i + j] != n[j]) { i++; continue@outer }
            return i
        }
        return -1
    }
}

/** Reproductor in-app de un clip AVI-MJPEG ya descargado en memoria (play/pausa + barra). */
@Composable
fun MjpegAviPlayer(clipBytes: ByteArray, modifier: Modifier = Modifier) {
    val clip = remember(clipBytes) { AviParser.parse(clipBytes) }
    var index by remember(clip) { mutableIntStateOf(0) }
    var playing by remember(clip) { mutableStateOf(true) }

    LaunchedEffect(clip, playing) {
        if (clip.frames.isEmpty() || !playing) return@LaunchedEffect
        while (true) {
            delay(clip.frameDelayMs)
            index = (index + 1) % clip.frames.size
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (clip.frames.isEmpty()) {
            Text("No se pudieron extraer fotogramas del clip", color = Color.LightGray, fontSize = 13.sp)
            return
        }
        val bmp = remember(index, clip) {
            val f = clip.frames[index]
            runCatching { BitmapFactory.decodeByteArray(f, 0, f.size) }.getOrNull()
        }
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { playing = !playing }) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausa" else "Reproducir",
                    tint = Color.White
                )
            }
            Slider(
                value = index.toFloat(),
                onValueChange = { playing = false; index = it.toInt().coerceIn(0, clip.frames.lastIndex) },
                valueRange = 0f..clip.frames.lastIndex.toFloat().coerceAtLeast(0f),
                modifier = Modifier.weight(1f)
            )
            Text("${index + 1}/${clip.frames.size}", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}
