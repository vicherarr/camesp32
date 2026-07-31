# 📖 Manual de Usuario y Operación del Dispositivo ESP32-S3-CAM

Manual oficial de instalación, configuración, operación y mantenimiento del dispositivo físico de videovigilancia **ESP32-S3-CAM Moto Security**.

---

## 📋 1. Descripción del Dispositivo

El **ESP32-S3-CAM Moto Security** es un sistema de videovigilancia IoT de ultra bajo consumo diseñado especialmente para vehículos (motocicletas, coches) o ubicaciones remotas. El dispositivo utiliza un procesador **ESP32-S3 Dual-Core**, una cámara integrada **GC0308** (o compatible DVP), un **LED de estado RGB (WS2812)**, un sensor de presencia por microondas **RCWL-0516 (Radar)** y una ranura para tarjetas **MicroSD**.

El sistema funciona con un esquema **Híbrido de Control BLE y WiFi Bajo Demanda**:
- **Consumo Mínimo / Espera (Bluetooth Low Energy - BLE):** El dispositivo permanece escuchando por BLE con un consumo insignificante. Desde la app puedes ver el estado, armar/desarmar la alarma o sincronizar la hora.
- **Transmisión de Vídeo / Galería (WiFi AP bajo demanda):** El WiFi **solo se enciende cuando entras a las pestañas En Vivo o Galería** de la app Android. Cuando vuelves a la pestaña de Inicio, el WiFi se apaga y vuelve al modo BLE de bajo consumo.
- **Modo Alarma Armada (Deep Sleep):** Si la alarma está armada, el ESP32-S3 se apaga en **Deep Sleep (~µA)**. Cuando el radar detecta movimiento, el procesador despierta en milisegundos, **graba un clip de vídeo MJPEG-AVI en la tarjeta MicroSD**, abre una pequeña ventana de conexión y vuelve a dormir si no hay intervención.

---

## 🔌 2. Esquema de Hardware y Pines (Pinout)

### 2.1 Tabla de Conexiones Principales

| Componente / Periférico | Pin ESP32-S3 | Tipo / Función | Notas e Instrucciones |
| :--- | :--- | :--- | :--- |
| **Alimentación (VCC)** | `5V` | Entrada 5V DC | Alimentado desde batería de moto mediante regulador 5V o USB-C |
| **Masa (GND)** | `GND` | Tierra Común | Conectar masa de alimentación y sensores a esta tierra |
| **Radar RCWL-0516 (OUT)** | `GPIO14` | Entrada Digital (`ext0_wakeup`) | Salida de 3.3V del radar al detectar presencia. ⚠️ **NO usar GPIO13** (conflicto con reloj de cámara). |
| **LED RGB WS2812** | `GPIO48` | Salida RMT (Data) | LED direccionable RGB integrado en la placa |
| **Tarjeta MicroSD (SDMMC)**| CLK=39, CMD=38, D0=40 | Bus SDMMC 1-Bit | Formato FAT32 (hasta 32GB recomendados) |
| **Cámara DVP GC0308** | Bus DVP de serie | Paralelo 8 bits | Cámara conectada por cable plano |

### 2.2 Diagrama de Cableado del Sensor Radar

```text
       ┌────────────────────────┐                   ┌────────────────────────┐
       │   Sensor Radar         │                   │     ESP32-S3-CAM       │
       │    RCWL-0516           │                   │                        │
       │  ┌──────────────────┐  │                   │  ┌──────────────────┐  │
       │  │       3V3        ├──┼─── (NC)           │  │       5V         ├──┼─── (Entrada VCC 5V)
       │  │       GND        ├──┼───────────────────┼──┤       GND        │  │
       │  │       OUT        ├──┼───────────────────┼──┤      GPIO14      │  │
       │  │       VIN        ├──┼───────────────────┼──┤       5V         │  │
       │  │       CDS        ├──┼─── (NC)           │  └──────────────────┘  │
       │  └──────────────────┘  │                   └────────────────────────┘
       └────────────────────────┘
```

---

## 🚦 3. Significado del LED de Estado RGB (GPIO48)

El LED RGB de la placa muestra de forma intuitiva qué está haciendo el dispositivo en todo momento:

| Color / Patrón | Estado del Sistema | Modo de Conexión | Significado / Acción |
| :--- | :--- | :--- | :--- |
| 🔴 **Rojo Fijo** | Inicializando | Ninguno | Arrancando hardware, NVS, cámara y SD. |
| 🟢 **Verde (Pulso)** | Alarma Desarmada | Ninguno | Sistema listo en standby. Esperando comandos por BLE. |
| 🩵 **Cian Intercalado (1s)**| Alarma Desarmada | **Conectado BLE** | App conectada mediante Bluetooth de bajo consumo. |
| 💛 **Amarillo Intercalado (1s)**| Modo Galería / Vivo | **Conectado WiFi** | El punto de acceso WiFi (`MIWIFI`) está activo y transmitiendo a la app. |
| 🔵 **Azul Parpadeante** | Ventana Armada | WiFi Activo breve | Alarma armada: despertó por movimiento o ventana abierta para desarmar. |
| 🟣 **Magenta Fijo** | Grabando | Ninguno | Escribiendo clip de vídeo MJPEG-AVI en la MicroSD por detección de presencia. |
| ⚫ **Apagado** | Deep Sleep | Ninguno | Alarma armada durmiendo (~µA). Despertará al detectar movimiento. |
| 🔴 **Rojo Parpadeo Rápido**| Error Crítico | Ninguno | Fallo al montar MicroSD o fallo al inicializar el sensor de cámara. |

---

## 🔒 4. Seguridad y Emparejamiento Bluetooth (PIN 001989)

Para evitar que extraños accedan a la cámara, el canal Bluetooth Low Energy utiliza **seguridad por clave PIN (Passkey Pairing) con Bonding guardado en NVS**:

- **Nombre del dispositivo BLE:** `CAMSEC`
- **PIN estático de emparejamiento:** `001989`
- **Comportamiento del PIN:**
  1. La primera vez que te conectas desde la app Android (o desde los ajustes Bluetooth de Android), el sistema operativo te pedirá vincular el dispositivo introduciendo el PIN.
  2. Escribe **`001989`**.
  3. El ESP32 guardará la clave de cifrado (bonding) en su memoria flash no volátil (NVS).
  4. Aunque resetees o quites la batería a la placa, **el emparejamiento se recuerda** y no volverá a pedir el PIN.

---

## ⚙️ 5. Modos de Funcionamiento y Operación

### 5.1 Modo Alarma Desarmada (Uso Diario / Monitoreo)
1. Conecta la alimentación al ESP32-S3. El LED se encenderá en 🔴 Rojo brevemente y cambiará a 🟢 Verde (pulso).
2. Abre la app **CamEspDroid** en tu smartphone.
3. El móvil se vinculará por BLE de forma transparente (LED cambiará a 🩵 Cian).
4. Cuando entres en la pestaña **En Vivo** o **Galería**, la app ordenará encender el WiFi. El LED pasará a 💛 Amarillo intercalado. Al salir a la pestaña **Inicio**, el WiFi se apagará automáticamente para ahorrar batería.

### 5.2 Modo Alarma Armada (Vigilancia / Aparcamiento)
1. En la pestaña **Inicio** de la app Android, pulsa el botón **ARMAR ALARMA**.
2. La placa guardará el estado en NVS y el LED se apagará (⚫ **Deep Sleep**).
3. Si alguien pasa delante del radar de la moto:
   - El sensor `GPIO14` despierta al procesador en milisegundos.
   - El LED se enciende en 🟣 **Magenta** y graba inmediatamente un clip `.AVI` en la MicroSD (aprox. 10 segundos).
   - A continuación, el LED parpadeará en 🔵 **Azul** abriendo una ventana de WiFi durante unos 45 segundos por si el propietario desea desarmar la alarma desde la app.
   - Si no se recibe orden de desarmar, la placa se vuelve a apagar en ⚫ **Deep Sleep**.

---

## 🛠️ 6. Mantenimiento y Solución de Problemas

### 🛠️ La cámara parpadea en Rojo Rápido
- **Causa:** La tarjeta MicroSD no está insertada, está llena o el formato no es FAT32.
- **Solución:** Extrae la tarjeta MicroSD, formatéala en FAT32 en tu PC e insértala firmemente antes de encender la placa.

### 🛠️ La app no se conecta al Bluetooth
- **Causa:** El Bluetooth del smartphone está desactivado o la placa está en Deep Sleep (Armada).
- **Solución:** Verifica que el Bluetooth del móvil esté encendido. Si la alarma estaba armada, pasa la mano por delante del radar para despertar la placa y abrir la ventana de conexión.

### 🛠️ Olvidé el PIN de emparejamiento
- El PIN por defecto del sistema es siempre **`001989`** (o `1989` según el diálogo de Android). Viene preconfigurado en el firmware.
