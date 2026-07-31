package com.vicherarr.camespdroid.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicherarr.camespdroid.ui.screens.GalleryScreen
import com.vicherarr.camespdroid.ui.screens.HomeScreen
import com.vicherarr.camespdroid.ui.screens.LiveStreamScreen
import com.vicherarr.camespdroid.ui.screens.SettingsScreen
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.DarkBg
import com.vicherarr.camespdroid.ui.theme.EmeraldGreen
import com.vicherarr.camespdroid.ui.theme.SurfaceDark
import com.vicherarr.camespdroid.viewmodel.MainViewModel
import com.vicherarr.camespdroid.viewmodel.MediaSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permisos BLE (Android 12+: SCAN/CONNECT; anteriores: ubicación).
    val blePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) viewModel.connectBle()
        else Toast.makeText(context, "Se necesitan permisos de Bluetooth", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) { permLauncher.launch(blePerms) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val inMedia = uiState.mediaSession == MediaSession.Active
    val badgeColor = when {
        uiState.armed -> Color(0xFFFF3B30)
        inMedia -> AccentCyan
        uiState.bleConnected -> EmeraldGreen
        else -> Color.Gray.copy(alpha = 0.6f)
    }
    val badgeText = when {
        inMedia -> "WIFI MEDIA"
        uiState.armed -> "ARMADA (BLE)"
        uiState.bleConnected -> "BLE OK"
        uiState.bleScanning -> "BUSCANDO…"
        else -> "SIN CONEXIÓN"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CamESP32 Moto", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .background(color = badgeColor, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(badgeText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                val tabs = listOf(
                    Triple(0, Icons.Default.Home, "Inicio"),
                    Triple(1, Icons.Default.Videocam, "En Vivo"),
                    Triple(2, Icons.Default.Collections, "Galería"),
                    Triple(3, Icons.Default.Settings, "Ajustes"),
                )
                tabs.forEach { (idx, icon, label) ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == idx,
                        onClick = { viewModel.selectTab(idx) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentCyan, unselectedIconColor = Color.Gray)
                    )
                }
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState.selectedTab) {
                0 -> HomeScreen(
                    uiState = uiState,
                    onArm = { viewModel.arm() },
                    onDisarm = { viewModel.disarm() },
                    onRetryBle = { viewModel.connectBle() },
                )
                1 -> LiveStreamScreen(
                    uiState = uiState,
                    onOpenMedia = { viewModel.openMediaSession() },
                    onCloseMedia = { viewModel.closeMediaSession() },
                    onStartLive = { viewModel.startLiveSnapshots() },
                    onStopLive = { viewModel.stopLiveSnapshots() },
                    onCapture = { viewModel.triggerPhotoCapture() },
                )
                2 -> {
                    val imageLoader = androidx.compose.runtime.remember(uiState.mediaSession) {
                        viewModel.boundImageLoader(context)
                    }
                    GalleryScreen(
                        uiState = uiState,
                        onOpenMedia = { viewModel.openMediaSession() },
                        onCloseMedia = { viewModel.closeMediaSession() },
                        onRefresh = { viewModel.refreshMediaList() },
                        onSelect = { viewModel.selectMedia(it) },
                        onDeleteAll = { viewModel.deleteAllPhotos() },
                        imageLoader = imageLoader,
                    )
                }
                3 -> SettingsScreen(uiState = uiState)
            }
        }
    }
}
