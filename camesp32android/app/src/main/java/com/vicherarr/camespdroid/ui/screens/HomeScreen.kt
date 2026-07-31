package com.vicherarr.camespdroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.EmeraldGreen
import com.vicherarr.camespdroid.ui.theme.SurfaceCard
import com.vicherarr.camespdroid.viewmodel.UiState

private val AlarmRed = Color(0xFFFF3B30)
private val AlarmAmber = Color(0xFFFFB300)

@Composable
fun HomeScreen(
    uiState: UiState,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onRetryBle: () -> Unit,
) {
    val armed = uiState.armed
    val connected = uiState.bleConnected

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Héroe: alarma + Armar/Desarmar (BLE) ──
        val accent = if (armed) AlarmRed else EmeraldGreen
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(88.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape)
                        .border(2.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (armed) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null, tint = accent, modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (armed) "ALARMA ARMADA" else "ALARMA DESARMADA",
                    color = accent, fontWeight = FontWeight.Bold, fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (armed)
                        "Bajo consumo. Graba vídeo al detectar movimiento. Control por Bluetooth."
                    else
                        "Control total por Bluetooth. El movimiento NO graba.",
                    color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { if (armed) onDisarm() else onArm() },
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (armed) EmeraldGreen else AlarmRed,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(if (armed) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (armed) "DESARMAR" else "ARMAR", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (!connected) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Acércate a la moto para conectar por Bluetooth.",
                        color = AlarmAmber, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Estado Bluetooth ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = if (connected) EmeraldGreen else Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            connected -> "Conectado (CAMSEC)"
                            uiState.bleScanning -> "Buscando la cámara…"
                            uiState.bleError != null -> "Bluetooth: ${uiState.bleError}"
                            else -> "Desconectado"
                        },
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Text("Control de alarma por BLE", color = Color.LightGray, fontSize = 12.sp)
                    // Diagnóstico de escaneo (temporal): cuántos BLE ve y si detecta la cámara.
                    if (!connected && uiState.bleScanning) {
                        Text(
                            "Escaneo: ${uiState.bleDevicesSeen} BLE vistos · cámara: ${if (uiState.bleCameraSeen) "SÍ" else "no"}",
                            color = if (uiState.bleCameraSeen) EmeraldGreen else AlarmAmber, fontSize = 11.sp
                        )
                    }
                    Text(
                        "🔒 PIN BLE de la cámara: 001989",
                        color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (!connected && !uiState.bleScanning) {
                    OutlinedButton(onClick = onRetryBle) { Text("Reintentar") }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Sensor ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.motion) Color(0x33FF3B30) else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = if (uiState.motion) AlarmRed else AccentCyan)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (uiState.motion) "¡MOVIMIENTO DETECTADO!" else "Sensor en reposo",
                        color = if (uiState.motion) AlarmRed else Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Text(
                        text = if (armed) "Armada: grabará un clip al detectar movimiento"
                        else "Desarmada: el movimiento no graba",
                        color = Color.LightGray, fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LedLegendCard()
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LedLegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Leyenda del LED de la placa", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LedRow(Color(0xFFFF3B30), "Rojo fijo", "Arrancando")
            LedRow(EmeraldGreen, "Verde (pulso)", "Desarmada")
            LedRow(Color(0xFF2196F3), "Azul (parpadeo)", "Armada")
            LedRow(Color(0xFFD500F9), "Magenta fijo", "Grabando vídeo")
            LedRow(Color(0xFF00E5FF), "Cian (mixto)", "Conectado BLE")
            LedRow(Color(0xFFFFEB3B), "Amarillo (mixto)", "Conectado WiFi")
            LedRow(Color(0xFF555555), "Apagado", "Deep sleep")
        }
    }
}

@Composable
private fun LedRow(color: Color, label: String, meaning: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
        Text(meaning, color = Color.LightGray, fontSize = 12.sp)
    }
}
