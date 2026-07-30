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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.EmeraldGreen
import com.vicherarr.camespdroid.ui.theme.SurfaceCard

// Colores semánticos locales para los estados de alarma.
private val AlarmRed = Color(0xFFFF3B30)
private val AlarmAmber = Color(0xFFFFB300)

@Composable
fun HomeScreen(
    isCameraOnline: Boolean,
    currentCameraMode: String,
    isMotionDetected: Boolean,
    isArmed: Boolean,
    onSetArmed: (Boolean) -> Unit,
    onNavigateToLive: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Tarjeta HÉROE: estado de la alarma + interruptor Armar/Desarmar ──
        AlarmHeroCard(
            isArmed = isArmed,
            isCameraOnline = isCameraOnline,
            onSetArmed = onSetArmed
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Estado de la cámara / enlace ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            color = if (isCameraOnline) EmeraldGreen else Color.Gray,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isCameraOnline) {
                            when (currentCameraMode) {
                                "AP" -> "Cámara conectada (Punto de Acceso)"
                                "STA" -> "Cámara conectada (Red cliente)"
                                else -> "Cámara conectada"
                            }
                        } else "Cámara no accesible",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isCameraOnline) "Enlace WiFi activo"
                        else if (isArmed) "Dormida en bajo consumo (armada)"
                        else "Buscando en la red...",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Estado del sensor de movimiento ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!isCameraOnline) SurfaceCard
                else if (isMotionDetected) Color(0x33FF3B30)
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = if (!isCameraOnline) Color.Gray
                    else if (isMotionDetected) AlarmRed
                    else AccentCyan
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (!isCameraOnline) "Sensor inaccesible (sin WiFi)"
                        else if (isMotionDetected) "¡MOVIMIENTO DETECTADO!"
                        else "Sensor en reposo",
                        color = if (!isCameraOnline) Color.LightGray
                        else if (isMotionDetected) AlarmRed
                        else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isArmed) "Armada: al detectar movimiento grabará un clip de vídeo"
                        else "Desarmada: el movimiento no dispara grabación",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Botón para abrir el visor en vivo (solo si hay enlace) ──
        if (isCameraOnline) {
            Button(
                onClick = onNavigateToLive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Icon(imageVector = Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir visor en vivo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        } else {
            WifiConnectHelper(context)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Leyenda del LED de estado de la placa ──
        LedLegendCard()

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AlarmHeroCard(
    isArmed: Boolean,
    isCameraOnline: Boolean,
    onSetArmed: (Boolean) -> Unit
) {
    val accent = if (isArmed) AlarmRed else EmeraldGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(accent.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isArmed) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isArmed) "ALARMA ARMADA" else "ALARMA DESARMADA",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isArmed)
                    "Bajo consumo. Despierta y graba vídeo al detectar movimiento. Solo accesible por WiFi durante la ventana tras cada evento."
                else
                    "WiFi activo y control total. El movimiento NO graba: úsalo para ver en vivo, revisar la galería y configurar.",
                color = Color.LightGray,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSetArmed(!isArmed) },
                enabled = isCameraOnline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isArmed) EmeraldGreen else AlarmRed,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    imageVector = if (isArmed) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isArmed) "DESARMAR" else "ARMAR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            if (!isCameraOnline) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isArmed)
                        "Provoca movimiento delante del sensor para abrir la ventana WiFi y poder desarmar."
                    else
                        "Conéctate al WiFi de la cámara para poder armarla.",
                    color = AlarmAmber,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Tarjeta con la leyenda de colores/patrones del LED de estado de a bordo (WS2812, GPIO48). */
@Composable
private fun LedLegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Leyenda del LED de estado", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LedLegendRow(Color(0xFFFF3B30), "Rojo fijo", "Arrancando")
            LedLegendRow(EmeraldGreen, "Verde (pulso)", "Desarmado: WiFi activo, no graba")
            LedLegendRow(Color(0xFF2196F3), "Azul (parpadeo)", "Armado: ventana WiFi, puedes desarmar")
            LedLegendRow(Color(0xFFD500F9), "Magenta fijo", "Grabando clip de vídeo")
            LedLegendRow(Color(0xFF555555), "Apagado", "Deep sleep (bajo consumo)")
            LedLegendRow(Color(0xFFFF3B30), "Rojo (parpadeo rápido)", "Error de cámara/SD")
        }
    }
}

@Composable
private fun LedLegendRow(color: Color, label: String, meaning: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
        Text(meaning, color = Color.LightGray, fontSize = 12.sp)
    }
}

/** Ayuda para conectarse a la red WiFi que crea la cámara en modo AP (SSID MIWIFI). */
@Composable
private fun WifiConnectHelper(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Conectar al WiFi de la cámara", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Red: MIWIFI · Clave: moto1112. Pulsa el botón para abrir los ajustes WiFi y unirte a la red de la cámara.",
                color = Color.LightGray,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val suggestion = WifiNetworkSuggestion.Builder()
                            .setSsid("MIWIFI")
                            .setWpa2Passphrase("moto1112")
                            .build()
                        wifiManager.addNetworkSuggestions(listOf(suggestion))
                        context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
                    } else {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Abrir ajustes WiFi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
