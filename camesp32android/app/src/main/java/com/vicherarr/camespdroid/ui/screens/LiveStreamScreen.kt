package com.vicherarr.camespdroid.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.EmeraldGreen
import com.vicherarr.camespdroid.ui.theme.SurfaceCard
import com.vicherarr.camespdroid.viewmodel.MediaSession
import com.vicherarr.camespdroid.viewmodel.UiState

@Composable
fun LiveStreamScreen(
    uiState: UiState,
    onOpenMedia: () -> Unit,
    onCloseMedia: () -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit,
    onCapture: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        when (uiState.mediaSession) {
            MediaSession.Active -> {
                DisposableEffect(Unit) {
                    onStartLive()
                    onDispose { onStopLive() }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                        contentAlignment = Alignment.Center
                    ) {
                        val snap = uiState.snapshot
                        val bmp = if (snap != null) runCatching { BitmapFactory.decodeByteArray(snap, 0, snap.size) }.getOrNull() else null
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "En vivo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                        } else {
                            CircularProgressIndicator(color = AccentCyan)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onCapture,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capturar", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onCloseMedia, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar cámara")
                    }
                }
            }
            MediaSession.Opening -> CenterInfo("Encendiendo el WiFi de la cámara…", spinner = true)
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ver la cámara en vivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Se encenderá el WiFi de la cámara y el móvil se enlazará a él (sin cambiar tu red). Requiere estar conectado por Bluetooth.",
                            color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onOpenMedia,
                            enabled = uiState.bleConnected,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Text("Encender cámara (WiFi)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterInfo(text: String, spinner: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (spinner) {
            CircularProgressIndicator(color = AccentCyan)
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(text, color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
