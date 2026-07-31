package com.vicherarr.camespdroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vicherarr.camespdroid.model.MediaItem
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.EmeraldGreen
import com.vicherarr.camespdroid.ui.theme.SurfaceCard
import com.vicherarr.camespdroid.ui.video.MjpegAviPlayer
import com.vicherarr.camespdroid.viewmodel.MediaSession
import com.vicherarr.camespdroid.viewmodel.UiState

@Composable
fun GalleryScreen(
    uiState: UiState,
    onOpenMedia: () -> Unit,
    onCloseMedia: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (MediaItem?) -> Unit,
    onDeleteAll: () -> Unit,
    imageLoader: ImageLoader,
) {
    if (uiState.mediaSession != MediaSession.Active) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Collections, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Galería en la SD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enciende el WiFi de la cámara para ver las fotos y los clips grabados.",
                color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenMedia,
                enabled = uiState.bleConnected,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) { Text("Encender cámara (WiFi)", fontWeight = FontWeight.Bold) }
        }
        return
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Archivos SD (${uiState.mediaList.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.background(SurfaceCard, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar todo", tint = Color(0xFFE57373))
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.background(SurfaceCard, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoadingMedia) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else if (uiState.mediaList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No hay archivos en la SD", color = Color.LightGray, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.mediaList) { item ->
                        MediaCard(item, imageLoader) { onSelect(item) }
                    }
                }
            }

            OutlinedButton(onClick = onCloseMedia, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.height(0.dp))
                Text("  Cerrar cámara (volver a BLE)")
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = SurfaceCard,
                title = { Text("Borrar todo", color = Color.White) },
                text = { Text("¿Borrar TODOS los archivos de la SD? No se puede deshacer.", color = Color.LightGray) },
                confirmButton = {
                    TextButton(onClick = { showDeleteConfirm = false; onDeleteAll() }) {
                        Text("Borrar", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar", color = Color.White) } }
            )
        }

        // Modal de detalle: foto (Coil) o vídeo (reproductor MJPEG in-app).
        uiState.selectedMedia?.let { item ->
            Dialog(onDismissRequest = { onSelect(null) }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.filename, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onSelect(null) }) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White) }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (item.isVideo) {
                            when {
                                uiState.loadingClip -> CircularProgressIndicator(color = AccentCyan)
                                uiState.selectedClipBytes != null -> MjpegAviPlayer(uiState.selectedClipBytes!!)
                                else -> Text("No se pudo descargar el clip", color = Color.LightGray, fontSize = 13.sp)
                            }
                        } else {
                            AsyncImage(
                                model = item.url,
                                imageLoader = imageLoader,
                                contentDescription = item.filename,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem, imageLoader: ImageLoader, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.isVideo) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B1B22)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Vídeo", tint = AccentCyan, modifier = Modifier.size(56.dp))
                }
            } else {
                AsyncImage(
                    model = item.url,
                    imageLoader = imageLoader,
                    contentDescription = item.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp)) {
                Text(
                    text = if (item.isVideo) "🎬 ${item.filename}" else item.filename,
                    color = Color.White, fontSize = 10.sp, maxLines = 1
                )
            }
        }
    }
}
