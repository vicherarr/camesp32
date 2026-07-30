# 🛡️ ESP32-S3-CAM Security & Remote Vision System

Un sistema de seguridad IoT de bajo consumo y visión remota compuesto por un **Firmware nativo en Rust para ESP32-S3-CAM** y una **Aplicación Móvil Android nativa en Kotlin con Jetpack Compose**.

El sistema funciona con **dos modos de alarma** que definen su consumo (Modelo A):

- **🟢 Alarma desactivada** — WiFi siempre activo. La app tiene control total (visión en vivo, galería, ajustes). El movimiento **no dispara grabación**, solo marca el estado del sensor.
- **🔴 Alarma armada** — el dispositivo entra en **deep sleep de micro-amperios**. El **sensor de microondas (GPIO14)** lo despierta al detectar presencia, **graba inmediatamente un clip de vídeo** (MJPEG-AVI) a la MicroSD, abre una **ventana WiFi corta** para poder desarmarlo desde la app, y vuelve a dormir.

El modo se cambia desde la app (botón **Armar/Desarmar**) y se guarda en NVS para sobrevivir al deep sleep.

---

## 📐 Arquitectura General del Sistema

```mermaid
graph TD
    A["Batería / Powerbank"] -->|5V / GND| B["ESP32-S3-CAM"]
    C["Radar RCWL-0516 / PIR"] -->|GPIO14 ext0 wakeup| B

    subgraph sub1 ["ESP32-S3 Firmware (Modelo A)"]
        B --> M{"¿Alarma armada?<br/>(NVS)"}
        M -->|No: Desarmada| D1["WiFi always-on<br/>Servidor HTTP"]
        D1 --> CAM["Cámara GC0308 (DVP)"]
        D1 --> SD["Almacenamiento VFS MicroSD"]
        M -->|Sí: Armada| DS["Deep Sleep ~µA"]
        DS -->|Movimiento GPIO14| REC["Graba clip MJPEG-AVI a SD"]
        REC --> WIN["Ventana WiFi (~45s)<br/>para DESARMAR"]
        WIN --> DS
    end

    subgraph sub2 ["App Android CamEspDroid"]
        K["App"] -->|Armar / Desarmar| M
        K -->|Visión en vivo (snapshots)| CAM
        K -->|Galería SD (fotos + clips AVI)| SD
    end

    D1 -.->|AP: MIWIFI / STA a repetidor| K
```


---

# 🔴 SECCIÓN I: HARDWARE Y FIRMWARE ESP32-S3-CAM (RUST)

## 1.1 Especificaciones de Hardware y Pinout

El firmware está optimizado para la placa **Freenove ESP32-S3-WROOM CAM** (procesador Xtensa LX7 Dual-Core a 240 MHz, 8 MB de Flash + 8 MB de PSRAM octal). La cámara es la **integrada de serie (sensor GC0308) por cable plano / bus DVP**, no una cámara USB.

### Esquema de Conexiones de Pines

| Componente | Pin ESP32-S3 | Función / Descripción |
| :--- | :--- | :--- |
| **Alimentación** | `5V` (vía USB-C) | Se alimenta por cualquiera de los dos USB-C de la placa |
| **Tierra** | `GND` | Referencia común de masa |
| **Radar RCWL-0516 (OUT)** | `GPIO14` | Entrada digital de movimiento (`ext0_wakeup`). ⚠️ **Antes GPIO13**, que colisionaba con el PCLK de la cámara |
| **Cámara GC0308 (DVP, de serie)** | XCLK=15, SDA=4, SCL=5, D0..D7=11/9/8/10/12/18/17/16, VSYNC=6, HREF=7, PCLK=13 | Cámara integrada por cable plano (bus paralelo DVP) |
| **LED de estado RGB (WS2812)** | `GPIO48` | LED de a bordo que indica el estado (arranque / WiFi / cámara) |
| **MicroSD (SDMMC 1-bit)** | CLK=39, CMD=38, D0=40 | Almacenamiento de fotos |
| **USB-OTG nativo** | `GPIO19/20` (D-/D+) | Reservado para USB host (no usar para otros fines) |

### 🔌 Diagrama de Cableado: Sensor de Presencia <---> ESP32-S3-CAM

```text
       ┌────────────────────────┐                   ┌────────────────────────┐
       │   Sensor Radar         │                   │     ESP32-S3-CAM       │
       │    RCWL-0516           │                   │                        │
       │  ┌──────────────────┐  │                   │  ┌──────────────────┐  │
       │  │       3V3        ├──┼─── (NC)           │  │       5V         ├──┼─── (VCC 5V)
       │  │       GND        ├──┼───────────────────┼──┤       GND        │  │
       │  │       OUT        ├──┼───────────────────┼──┤      GPIO14      │  │
       │  │       VIN        ├──┼───────────────────┼──┤       5V         │  │
       │  │       CDS        ├──┼─── (NC)           │  └──────────────────┘  │
       │  └──────────────────┘  │                   └────────────────────────┘
       └────────────────────────┘
```

#### Detalle de Conexión Pin a Pin:
1. **VIN (Sensor)** ➔ Conectar al pin **`5V`** del ESP32-S3-CAM *(El sensor opera con voltaje entre 4V y 12V)*.
2. **GND (Sensor)** ➔ Conectar al pin **`GND`** del ESP32-S3-CAM *(Referencia común de masa)*.
3. **OUT (Sensor)** ➔ Conectar al pin **`GPIO14`** del ESP32-S3-CAM *(Salida digital 3.3V: emite nivel ALTO al detectar movimiento)*. ⚠️ **Importante:** NO usar GPIO13 — ese pin es el **PCLK de la cámara** y provoca conflicto (la cámara no arranca). GPIO14 está libre, no es strapping pin y es RTC-capaz (válido para `ext0_wakeup`).
4. **Pines 3V3 y CDS**: Dejar desconectados (NC). *(El pin CDS permite añadir opcionalmente un LDR para desactivar el sensor con luz de día)*.

> 💡 **LED de estado RGB (GPIO48):** cada estado del sistema tiene un color/patrón propio:
>
> | Estado | Color | Patrón | Significado |
> | :--- | :--- | :--- | :--- |
> | Arrancando | 🔴 rojo | fijo | Inicializando periféricos |
> | Desarmado | 🟢 verde | pulso lento | WiFi activo, control total, no graba |
> | Armado · ventana WiFi | 🔵 azul | parpadeo | Ventana abierta: puedes desarmar |
> | Grabando vídeo | 🟣 magenta | fijo | Grabando clip por movimiento |
> | Deep sleep | ⚫ apagado | — | Bajo consumo; despierta por el sensor |
> | Error | 🔴 rojo | parpadeo rápido | Fallo de cámara/SD |

---

## 1.2 Gestión de Bajo Consumo (Modelo A: el modo dicta la energía)

El estado de la alarma se guarda en **NVS** (sobrevive al deep sleep) y determina la política de energía. En el arranque, el firmware lee el modo y la **causa de despertar** (`esp_sleep_get_wakeup_cause`) para distinguir un evento de movimiento de un power-on normal.

**🟢 Alarma DESARMADA (WiFi always-on):**
- El módem WiFi y el servidor HTTP están siempre activos: la app tiene control total (visión en vivo, galería, ajustes).
- El movimiento en `GPIO14` **solo actualiza el estado del sensor** (`/sensor`, `/info`); **no graba**.
- Desde la app se puede **armar** (`POST /arm`): el firmware guarda el estado y reinicia hacia el modo armado.

**🔴 Alarma ARMADA (deep sleep de µA):**
1. El dispositivo entra en **deep sleep** con activación por nivel alto en `GPIO14` (`esp_sleep_enable_ext0_wakeup` + `esp_deep_sleep_start`).
2. Al detectar movimiento, **despierta y graba inmediatamente** un clip **MJPEG-AVI** a la MicroSD (`CLIPnnnnn.AVI`, ~10 s).
3. Abre una **ventana WiFi (~45 s)** con el servidor HTTP para que la app pueda **desarmar** (`POST /disarm`). Si nadie desarma, apaga la cámara y vuelve a **deep sleep**.

> ℹ️ **Realidad de la grabación:** la cámara GC0308 no tiene JPEG por hardware; cada fotograma se codifica por software, así que los clips salen a **~2-4 fps reales** (a saltos pero válidos como evidencia). El header AVI se ajusta a los fps medidos para que la reproducción vaya a velocidad correcta.
>
> ⚠️ **Desarmar estando armado:** como en deep sleep el WiFi está apagado, para desarmar hay que **provocar un evento de movimiento** (que abre la ventana WiFi) y pulsar *Desarmar* en la app dentro de esa ventana.

---

## 1.3 Arquitectura del Código del Firmware (`esp32_cam_sec/`)

El código está escrito en **Rust puro** (`xtensa-esp32s3-espidf`) utilizando la capa HAL y SVC de ESP-IDF v5.2, e integrando el driver C oficial de Espressif (`esp32-camera`).

### Módulos Principales:

* [build.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/build.rs): Propaga el entorno de ESP-IDF (`embuild`). El driver de cámara se integra vía el **componente gestionado** `espressif/esp32-camera` (declarado en `idf_component.yml`), del que `esp-idf-sys` genera el módulo de bindings `camera_sys` (ver `src/camera_bindings.h`).
* [src/main.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/main.rs): Orquestador principal. Es una **máquina de estados (Modelo A)**: lee el modo de alarma (NVS) y la causa de despertar, y ejecuta la rama **desarmada** (WiFi always-on, sin grabar) o **armada** (graba clip por evento, ventana WiFi para desarmar y **deep sleep**).
* [src/camera.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/camera.rs): Inicializa la **cámara DVP integrada de serie**. Detecta el sensor: si soporta JPEG (OV2640/OV3660) lo usa directo; si no (como el **GC0308**, PID `0x9b`), captura en **RGB565** y convierte a JPEG por software (`frame2jpg`). Cae a modo *mock* sin bloquear si no hay cámara. Incluye `deinit()` para apagar la cámara antes del deep sleep.
* [src/video.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/video.rs): `VideoRecorder` que escribe **clips MJPEG en contenedor AVI** a la SD (header RIFF/AVI, un chunk `00dc` por frame, índice `idx1`) y parchea al cerrar los tamaños y los **fps reales medidos**. Reproducible en VLC/ffmpeg.
* [src/led.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/led.rs): Driver del **LED RGB WS2812 de a bordo (GPIO48)** vía RMT. Define un **enum `LedState`** con un color/patrón por estado del sistema (ver la tabla del LED arriba).
* [src/wifi.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/wifi.rs): Gestión dual de WiFi: punto de acceso SoftAP (**SSID `MIWIFI`, clave `moto1112`**, modo por defecto) o cliente de una red existente (STA mode, con IP fija para el repetidor).
* [src/server.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/server.rs): Servidor HTTP empotrado: foto en vivo (`/photo`), captura a SD (`/capture`), armar/desarmar la alarma (`/arm`, `/disarm`), listado/servido de archivos SD (`/photos`, `/file/*`, con Content-Type de vídeo para `.avi`), estado (`/sensor`, `/info` con campo `armed`) y logs (`/logs`).
* [src/storage.rs](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/src/storage.rs): Controlador del sistema de archivos VFS FATFS para lectura/escritura en tarjeta MicroSD.
* [sdkconfig.defaults](file:///home/victor/develop/iot/camesp32/esp32_cam_sec/sdkconfig.defaults): Configuración de ESP-IDF para 16MB de memoria Flash y asignación de la tabla de particiones ampliada `CONFIG_PARTITION_TABLE_SINGLE_APP_LARGE` (3.9 MB de aplicación).

### 🌐 Endpoints HTTP de la Cámara
La cámara expone un servidor HTTP en el puerto 80 con las siguientes rutas:
- **`GET /`**: Devuelve un Dashboard HTML sencillo.
- **`GET /photo`**: **Captura una foto en vivo** de la cámara integrada y la devuelve como `image/jpeg` (VGA 640×480). Si el sensor no da JPEG nativo (GC0308), se codifica a JPEG por software. Útil para comprobar la cámara sin el sensor conectado.
- **`GET /capture`**: Captura una foto **y la guarda en la SD** (`/sdcard/CAP_<ms>.JPG`), devolviendo JSON `{"status":"ok",...}`. Es el endpoint que usa el botón *"capturar foto"* de la app Android.
- **`GET /logs`**: Devuelve en texto plano el buffer de logs en RAM (para diagnóstico por WiFi cuando no hay consola serie).
- **`GET /photos`**: Devuelve una lista HTML en streaming con los archivos de la MicroSD (fotos `.JPG` y clips de vídeo `.AVI`; límite de 100 archivos).
- **`GET /file/*`**: Sirve en streaming el archivo solicitado. Content-Type según extensión: `image/jpeg` para fotos, `video/x-msvideo` para los clips `.avi`.
- **`GET /info`**: Estado de la placa en JSON `{"device": "ESP32-CAM", "mode": "AP", "motion": true/false, "rssi": -60, "linked": true, "armed": true/false}`.
- **`GET /sensor`**: JSON rápido del radar `{"motion": true/false}`.
- **`POST /arm`**: **Arma** la alarma. Guarda el estado en NVS y reinicia → el dispositivo pasa a bajo consumo (deep sleep) y solo graba por movimiento.
- **`POST /disarm`**: **Desarma** la alarma. Guarda el estado en NVS y reinicia → WiFi always-on, sin grabar.
- **`POST /config`**: Recibe `{"mode": "STA"|"AP"}` para guardar el modo de red en NVS e iniciar un reinicio automático.

---

## 1.4 Compilación y Flasheo del ESP32-S3

### Requisitos Previos
- Toolchain de Rust para Xtensa (`espup`).
- Herramienta de flasheo `cargo-espflash`.

### Comandos de Instalación y Carga:

```bash
# 1. Navegar al directorio del firmware
cd esp32_cam_sec

# 2. Cargar variables de entorno del toolchain Xtensa
source ~/.cargo/env
source ~/export-esp.sh

# 3. Compilar y flashear en modo Release (Optimizado)
cargo espflash flash --release --port /dev/ttyACM0 --monitor
```

---

# 🔵 SECCIÓN II: APLICACIÓN MÓVIL ANDROID (KOTLIN + JETPACK COMPOSE)

## 2.1 Arquitectura del Software (`camesp32android/`)

La aplicación móvil está desarrollada siguiendo **Android Clean Architecture**, **MVVM (Model-View-ViewModel)** y **Unidirectional Data Flow (UDF)** usando **Jetpack Compose UI**.

```text
com.vicherarr.camespdroid
 ├── model/
 │    └── MediaItem.kt         # Modelo de datos SD; distingue foto de clip de vídeo (isVideo)
 ├── network/
 │    └── CameraRepository.kt  # Cliente HTTP OkHttp (ping /info, arm/disarm, galería, captura)
 ├── viewmodel/
 │    └── MainViewModel.kt     # ViewModel centralizado y gestión de StateFlow (incluye isArmed)
 └── ui/
      ├── screens/
      │    ├── HomeScreen.kt        # Panel de alarma con interruptor Armar/Desarmar + leyenda del LED
      │    ├── LiveStreamScreen.kt  # Visor En Vivo por snapshots
      │    ├── GalleryScreen.kt     # Galería SD (fotos con Coil; clips AVI a reproductor externo)
      │    └── SettingsScreen.kt    # Configuración de IP y credenciales
      ├── theme/
      │    ├── Color.kt             # Sistema de diseño con paleta HSL oscura
      │    └── Theme.kt             # Configuración de MaterialTheme Material3
      └── MainScreen.kt             # Scaffold, BottomBar y badge de estado (ARMADA/WIFI ONLINE)
```

---

## 2.2 Componentes Técnicos Detallados

### 1. Control de Alarma (`HomeScreen.kt` + `MainViewModel.kt`)
- Panel principal con un **interruptor grande Armar/Desarmar** que refleja el estado real (`armed` leído de `/info`).
- **Armar** llama a `POST /arm` y **Desarmar** a `POST /disarm`; el ViewModel conserva el último estado conocido mientras el dispositivo duerme.
- Incluye una **leyenda del LED de estado** para interpretar de un vistazo qué hace la placa.

### 2. Capa de Red (`CameraRepository.kt`)
- Utiliza **OkHttp 4** sobre HTTP en la red local (AP `MIWIFI` o STA a través del repetidor).
- Sondea `/info` cada 5 s (online, modo, movimiento, armado), descubre la IP por broadcast UDP, y expone `arm/disarm`, captura, galería y borrado.

### 3. Multimedia (`LiveStreamScreen.kt` & `GalleryScreen.kt`)
- **Coil Compose** para el renderizado asíncrono de las fotos JPEG de la SD y del visor En Vivo por snapshots.
- La galería **distingue fotos de clips de vídeo**: las fotos se ven en un modal; los clips `.AVI` se abren en un **reproductor externo** (VLC/MX Player) porque Android no decodifica MJPEG-AVI de forma nativa.

---

## 2.3 Permisos Requeridos (`AndroidManifest.xml`)

La aplicación solo necesita permisos de red (ya **no usa Bluetooth ni ubicación**):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

---

## 2.4 Compilación del APK de Android

### Requisitos:
- JDK 17
- Gradle 8.8+ / AGP 8.8.0

### Comando de Compilación:
```bash
# Entrar al directorio Android
cd camesp32android

# Compilar el binario APK de depuración
./gradlew assembleDebug
```
El archivo APK resultante se generará en: `app/build/outputs/apk/debug/app-debug.apk`.

---

# 🟢 SECCIÓN III: GUÍA DE OPERACIÓN PASO A PASO

1. **Alimentar el Dispositivo**: Conecta el módulo ESP32-S3-CAM a tu batería o fuente de 5V. Arranca **desarmado**: el LED hace un **pulso verde** y levanta su red WiFi.
2. **Conectar al WiFi**: Desde el móvil, únete a la red WiFi de la cámara — **SSID `MIWIFI`, clave `moto1112`** (modo AP por defecto). La app ofrece un botón para abrir los ajustes WiFi.
3. **Abrir la App Android**: Inicia **CamEspDroid**. El panel de **Inicio** mostrará *Alarma desarmada* y el estado del enlace.
4. **Uso desarmado (control total)**:
   - **En Vivo**: mira la cámara en tiempo real (por snapshots).
   - **Galería SD**: explora fotos y clips de vídeo; los `.AVI` se abren en un reproductor externo.
   - **Capturar Foto**: toma una foto instantánea a la SD.
5. **Armar la alarma**: pulsa **ARMAR**. El dispositivo entra en **bajo consumo (deep sleep)** — el LED se apaga — y solo despertará por movimiento.
6. **Evento de movimiento**: al detectar presencia, la placa **graba un clip de vídeo** (LED magenta), abre una **ventana WiFi ~45 s** (LED azul parpadeante) y vuelve a dormir.
7. **Desarmar**: dentro de esa ventana (provócala pasando delante del sensor), pulsa **DESARMAR** en la app para volver al modo de control total.

---

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Desarrollado para uso en proyectos IoT de seguridad y videovigilancia de bajo consumo.
