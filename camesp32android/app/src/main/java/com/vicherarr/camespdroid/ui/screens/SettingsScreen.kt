package com.vicherarr.camespdroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.SurfaceCard
import com.vicherarr.camespdroid.viewmodel.MainViewModel
import com.vicherarr.camespdroid.viewmodel.UiState

@Composable
fun SettingsScreen(uiState: UiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        InfoCard(Icons.Default.Bluetooth, "Bluetooth (control)") {
            Text(
                "La app se conecta a la cámara por Bluetooth (dispositivo \"CAMSEC\") para armar/desarmar y ver el estado. Emparéjalo la primera vez desde los ajustes de Bluetooth del móvil si te lo pide.",
                color = Color.LightGray, fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Estado: " + if (uiState.bleConnected) "conectado" else if (uiState.bleScanning) "buscando…" else "desconectado",
                color = if (uiState.bleConnected) AccentCyan else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }

        InfoCard(Icons.Default.Wifi, "WiFi de la cámara (media)") {
            Text(
                "Para ver en vivo o la galería, la app enciende el WiFi de la cámara y el móvil se enlaza a él automáticamente, sin cambiar tu red normal.",
                color = Color.LightGray, fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Red: ${MainViewModel.AP_SSID}   ·   Clave: ${MainViewModel.AP_PASS}", color = Color.White, fontSize = 12.sp)
        }

        InfoCard(Icons.Default.Info, "Cómo funciona") {
            Text(
                "• Armada: la cámara duerme en bajo consumo y graba un clip de vídeo al detectar movimiento.\n" +
                    "• Desarmada: el movimiento no graba.\n" +
                    "• El control (armar/desarmar) va por Bluetooth y funciona en proximidad, aunque la cámara esté dormida.\n" +
                    "• La hora se sincroniza automáticamente por Bluetooth para nombrar los clips con fecha y hora.",
                color = Color.LightGray, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
