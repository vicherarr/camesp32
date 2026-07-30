package com.vicherarr.camespdroid.ui

import android.widget.Toast
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CamESP32 S3", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                        // Status Badge Pill
                        val badgeColor = when {
                            uiState.isArmed -> Color(0xFFFF3B30)
                            uiState.isCameraOnline -> EmeraldGreen
                            else -> Color.Gray.copy(alpha = 0.6f)
                        }
                        val badgeText = when {
                            uiState.isArmed -> "ARMADA"
                            uiState.isCameraOnline -> "WIFI ONLINE"
                            else -> "OFFLINE"
                        }
                        Box(
                            modifier = Modifier
                                .background(color = badgeColor, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentCyan, unselectedIconColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "En Vivo") },
                    label = { Text("En Vivo") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentCyan, unselectedIconColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Collections, contentDescription = "Galería SD") },
                    label = { Text("Galería SD") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentCyan, unselectedIconColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                    label = { Text("Ajustes") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentCyan, unselectedIconColor = Color.Gray)
                )
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                0 -> HomeScreen(
                    isCameraOnline = uiState.isCameraOnline,
                    currentCameraMode = uiState.currentCameraMode,
                    isMotionDetected = uiState.isMotionDetected,
                    isArmed = uiState.isArmed,
                    onSetArmed = { armed -> viewModel.setArmed(armed) },
                    onNavigateToLive = { viewModel.selectTab(1) }
                )
                1 -> LiveStreamScreen(
                    baseUrl = viewModel.baseUrl,
                    username = uiState.username,
                    password = uiState.password,
                    isCameraOnline = uiState.isCameraOnline,
                    onTriggerCapture = { viewModel.triggerPhotoCapture() }
                )
                2 -> GalleryScreen(
                    mediaList = uiState.mediaList,
                    isLoading = uiState.isLoadingMedia,
                    username = uiState.username,
                    password = uiState.password,
                    selectedMedia = uiState.selectedMedia,
                    onRefresh = { viewModel.refreshMediaList() },
                    onSelectMedia = { viewModel.selectMediaItem(it) },
                    onDeleteAll = { viewModel.deleteAllPhotos() }
                )
                3 -> SettingsScreen(
                    currentIp = uiState.ipAddress,
                    currentPort = uiState.httpPort,
                    currentUser = uiState.username,
                    currentPass = uiState.password,
                    onSaveSettings = { ip, port, user, pass ->
                        viewModel.updateSettings(ip, port, user, pass)
                    },
                    onConfigEspWifi = { mode, ssid, pass ->
                        viewModel.sendEspConfig(mode, ssid, pass)
                    }
                )
            }
        }
    }
}
