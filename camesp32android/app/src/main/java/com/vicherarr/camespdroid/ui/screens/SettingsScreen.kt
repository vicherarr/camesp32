package com.vicherarr.camespdroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicherarr.camespdroid.ui.theme.AccentCyan
import com.vicherarr.camespdroid.ui.theme.SurfaceCard

@Composable
fun SettingsScreen(
    currentIp: String,
    currentPort: String,
    currentUser: String,
    currentPass: String,
    currentBleName: String,
    onSaveSettings: (ip: String, port: String, user: String, pass: String, bleName: String) -> Unit
) {
    var ip by remember { mutableStateOf(currentIp) }
    var port by remember { mutableStateOf(currentPort) }
    var user by remember { mutableStateOf(currentUser) }
    var pass by remember { mutableStateOf(currentPass) }
    var bleName by remember { mutableStateOf(currentBleName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Configuración del Dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Red & Cámara ESP32", color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Dirección IP Cámara") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Puerto HTTP") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Autenticación HTTP (Basic Auth)", color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Nombre Dispositivo BLE", color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bleName,
                    onValueChange = { bleName = it },
                    label = { Text("Nombre Anuncio BLE") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSaveSettings(ip, port, user, pass, bleName) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
        ) {
            Text("Guardar Cambios", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
