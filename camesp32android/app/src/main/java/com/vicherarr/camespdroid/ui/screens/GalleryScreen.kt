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
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vicherarr.camespdroid.model.MediaItem
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.SurfaceCard
import okhttp3.Credentials

@Composable
fun GalleryScreen(
    mediaList: List<MediaItem>,
    isLoading: Boolean,
    username: String,
    password: String,
    selectedMedia: MediaItem?,
    onRefresh: () -> Unit,
    onSelectMedia: (MediaItem?) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Archivos SD (${mediaList.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .background(SurfaceCard, RoundedCornerShape(12.dp))
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refrescar SD", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else if (mediaList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No se encontraron archivos en la tarjeta SD",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Conéctate al WiFi y pulsa 'Capturar Foto'",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(mediaList) { item ->
                        MediaCardItem(
                            item = item,
                            username = username,
                            password = password,
                            onClick = { onSelectMedia(item) }
                        )
                    }
                }
            }
        }

        // Fullscreen Media Modal Dialog
        selectedMedia?.let { item ->
            Dialog(onDismissRequest = { onSelectMedia(null) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.filename,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onSelectMedia(null) }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val context = LocalContext.current
                        val imageRequest = remember(item.url, username, password) {
                            ImageRequest.Builder(context)
                                .data(item.url)
                                .addHeader("Authorization", Credentials.basic(username, password))
                                .crossfade(true)
                                .build()
                        }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.filename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCardItem(
    item: MediaItem,
    username: String,
    password: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val imageRequest = remember(item.url, username, password) {
                ImageRequest.Builder(context)
                    .data(item.url)
                    .addHeader("Authorization", Credentials.basic(username, password))
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = item.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Text(
                    text = item.filename,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
