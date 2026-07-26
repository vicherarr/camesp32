package com.vicherarr.camespdroid.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicherarr.camespdroid.ble.BleState
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.AccentTeal
import com.vicherarr.camespdroid.ui.theme.EmeraldGreen
import com.vicherarr.camespdroid.ui.theme.SurfaceCard

@Composable
fun HomeScreen(
    bleState: BleState,
    isCameraOnline: Boolean,
    onTriggerWakeup: () -> Unit,
    onNavigateToLive: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = if (isCameraOnline) EmeraldGreen else Color.Gray,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isCameraOnline) "Cámara Conectada (WiFi)" else "Cámara en Reposo (BLE)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (isCameraOnline) "Conectado a IP: 192.168.4.1" else "Pulsa para despertar a distancia",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Giant Pulse Wakeup Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .scale(if (bleState is BleState.Scanning) pulseScale else 1f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentCyan.copy(alpha = 0.8f),
                                AccentTeal.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable { onTriggerWakeup() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(AccentCyan, AccentTeal)),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = when (bleState) {
                            is BleState.Scanning -> Icons.Default.BluetoothSearching
                            is BleState.WakeupSent -> Icons.Default.CheckCircle
                            else -> Icons.Default.PowerSettingsNew
                        },
                        contentDescription = "Despertar BLE",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (bleState) {
                    is BleState.Idle -> "Despertar Cámara (BLE)"
                    is BleState.Scanning -> "Escaneando 'CAM-ACTIVATE'..."
                    is BleState.Connecting -> "Enviando pulso de activación..."
                    is BleState.WakeupSent -> "¡Señal enviada! Conectando WiFi..."
                    is BleState.Error -> bleState.message
                    else -> "Despertar Cámara"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Cero botones físicos • Control ultra bajo consumo",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Quick Connect Banner / Action
        if (isCameraOnline) {
            Button(
                onClick = onNavigateToLive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir Visor en Vivo (HTTP Stream)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Info de Red WiFi", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("SSID: ESP32-CAM-Seguridad", color = Color.LightGray, fontSize = 13.sp)
                    Text("Contraseña: 12345678", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }
    }
}
