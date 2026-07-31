# 🏗️ Manual Técnico Completo de Desarrollo: ESP32-S3-CAM & CamEspDroid

Este documento constituye la **guía técnica exhaustiva y exhaustivamente detallada** de la arquitectura, diseño de software, patrones de concurrencia y análisis código a código del proyecto **CamESP32 Moto Security System**.

El proyecto se divide en dos grandes subsistemas:
1. **Firmware Embebido para ESP32-S3:** Desarrollado en **Rust** sobre la capa `esp-idf-hal` / `esp-idf-svc` (ESP-IDF v5.2).
2. **Aplicación Móvil Android:** Desarrollada en **Kotlin** utilizando **Jetpack Compose**, **Coroutines**, **StateFlow** y **Clean Architecture (MVVM)**.

---

# 📚 SECCIÓN 1: INTRODUCCIÓN A RUST PARA PRINCIPIANTES EN EMBEBIDOS

Para comprender la parte del firmware, es esencial entender los conceptos fundamentales que hacen a **Rust** el lenguaje ideal para sistemas de misión crítica y de bajo consumo.

### 1.1 `no_std` frente a `std` en ESP32
En el desarrollo embebido tradicional en Rust se suele usar `#![no_std]` (sin la librería estándar, sin asignador dinámico de memoria por defecto). Sin embargo, en el ESP32-S3 con ESP-IDF utilizamos la **librería estándar de Rust (`std`)**, soportada gracias al sistema operativo en tiempo real **FreeRTOS** que subyace en ESP-IDF. Esto nos permite usar colecciones dinámicas (`Vec`, `VecDeque`), hilos de FreeRTOS (`std::thread`), primitivas de sincronización (`Mutex`) y gestión de memoria mediante punteros inteligentes (`Arc`).

### 1.2 El Sistema de Propiedad (Ownership) y Préstamo (Borrowing)
A diferencia de C o C++ donde la memoria se gestiona manualmente (`malloc`/`free`) arriesgándose a fugas de memoria o punteros colgados, Rust utiliza **Ownership**:
- Cada valor en Rust tiene un único "propietario" (una variable).
- Cuando la variable sale de su ámbito (scope), la memoria se libera automáticamente (RAII: *Resource Acquisition Is Initialization*).
- Si necesitas pasar datos sin transferir la propiedad, puedes hacer un **préstamo (Borrowing)**:
  - Referencia inmutable `&T`: Permite múltiples lectores a la vez.
  - Referencia mutable `&mut T`: Permite un solo escritor exclusivo a la vez.

### 1.3 Primitivas de Concurrencia Usadas en el Proyecto

#### `Arc<T>` (Atomically Reference Counted)
`Arc` es un puntero inteligente con contador de referencias atómico. Permite compartir la propiedad de un objeto entre múltiples hilos de ejecución de forma totalmente segura. Cuando el último `Arc` se destruye, el valor contenido se libera de la memoria.

#### `Mutex<T>` (Mutual Exclusion)
Un `Mutex` protege un dato mutable para que solo un hilo pueda acceder a él al mismo tiempo. En Rust, el datos vive **dentro** del Mutex (`Mutex<T>`). Para acceder a él, se llama a `.lock()`, lo que devuelve un `MutexGuard`. Mientras esa guardia existe, tienes acceso al dato. Al salir de scope, la guardia se destruye y el cerrojo se libera automáticamente.

#### `AtomicBool`, `AtomicU8`, `AtomicU64`
Son variables numéricas o booleanas cuya lectura y escritura se realiza directamente mediante instrucciones atómicas del procesador a nivel de hardware, sin necesidad de bloquear hilos con un `Mutex`. Usamos `Ordering::Relaxed` o `Ordering::SeqCst` para definir la consistencia de memoria.

#### `Option<T>` y `Result<T, E>`
Rust **no tiene valores nulos (`null`)**. 
- `Option<T>` representa un valor que puede existir (`Some(valor)`) o no (`None`).
- `Result<T, E>` se usa para manejo de errores. Puede ser una operación exitosa (`Ok(resultado)`) o un fallo (`Err(error)`). El operador `?` permite propagar un error hacia arriba de forma limpia.

#### FFI (Foreign Function Interface) y Bloques `unsafe`
El driver de la cámara GC0308 y las llamadas a bajo nivel de ESP-IDF están escritos en C. Rust se comunica con C mediante **FFI**. Debido a que Rust no puede garantizar lo que hace el código en C, las llamadas a funciones de C se envuelven en bloques **`unsafe { ... }`**, aislando la fragilidad del código de C del resto de la aplicación segura en Rust.

---

# 🦀 SECCIÓN 2: DESGLOSE COMPLETO DEL FIRMWARE ESP32-S3 (RUST)

El proyecto del firmware reside en el directorio `esp32_cam_sec/`.

```text
esp32_cam_sec/
├── Cargo.toml               # Dependencias de Rust y metadata de compilación
├── sdkconfig.defaults       # Configuración de FreeRTOS, PSRAM y Particiones
├── build.rs                 # Script de compilación y enlaces C con ESP-IDF
└── src/
    ├── main.rs              # Punto de entrada y máquina de estados principal
    ├── ble.rs               # Servidor GATT NimBLE, seguridad PIN 001989 y control
    ├── wifi.rs              # Gestión de AP/STA y ciclo de red WiFi
    ├── server.rs            # Servidor HTTP embebido y endpoints REST
    ├── camera.rs            # Driver DVP GC0308, FFI C y software JPEG (frame2jpg)
    ├── video.rs             # Grabador MJPEG-AVI en MicroSD (RIFF/AVI)
    ├── led.rs               # Driver RMT del LED RGB WS2812 (GPIO48)
    ├── storage.rs           # Montaje VFS FATFS para MicroSD (SDMMC 1-bit)
    ├── config.rs            # Persistencia de ajustes en NVS (Flash)
    ├── logbuf.rs            # Buffer circular de logs en RAM para diagnóstico
    └── discovery.rs         # Servidor UDP para autodescubrimiento en red local
```

---

## 2.1 `Cargo.toml` y `sdkconfig.defaults`

### `Cargo.toml`
Define las librerías de Rust (crates) clave:
- `esp-idf-sys`: Binding de bajo nivel con las cabeceras C de ESP-IDF.
- `esp-idf-hal`: Capa de abstracción de hardware (GPIO, RMT, SDMMC, I2C, SPI).
- `esp-idf-svc`: Servicios de ESP-IDF (WiFi, HTTP Server, NVS, SNTP).
- `esp32-nimble`: Crate optimizado de alto nivel para el stack de Bluetooth Low Energy (NimBLE).
- `anyhow`: Gestión simplificada de errores con contexto.
- `log`: Abstracción de registros de consola.

### `sdkconfig.defaults`
Configura los parámetros del SoC ESP32-S3:
- `CONFIG_ESP32S3_SPIRAM_SUPPORT=y`: Habilita los **8 MB de PSRAM octal**, imprescindible para alojar los buffers de fotogramas de la cámara.
- `CONFIG_PARTITION_TABLE_SINGLE_APP_LARGE=y`: Reserva una partición de **3.9 MB** para el ejecutable del firmware.
- `CONFIG_BT_NIMBLE_ENABLED=y`: Activa el stack NimBLE (ocupando mucho menos espacio en RAM que Bluedroid).

---

## 2.2 `src/main.rs` — Orquestador y Máquina de Estados

### Explicación del Código:
`main.rs` contiene la función principal `main()`. Al arrancar el chip:
1. **Inicializa parches de ESP-IDF:** `esp_idf_svc::sys::link_patches()`.
2. **Carga la configuración NVS:** Lee si el dispositivo estaba **Armado** o **Desarmado**.
3. **Causa de Despertar (`esp_sleep_get_wakeup_cause`):**
   - Si despertó por `ESP_SLEEP_WAKEUP_EXT0` (pin `GPIO14` del radar), significa que se ha detectado movimiento estando armado.
4. **Instanciación del LED RGB (`src/led.rs`):** Arranca el driver RMT en `GPIO48` y pone el color inicial a 🔴 **Rojo (Booting)**.
5. **Inicialización de Almacenamiento y Cámara:** Monta la MicroSD (`src/storage.rs`) y la cámara DVP (`src/camera.rs`).
6. **Bucle de Control / Lógica de Estados:**

```rust
// Fragmento simplificado del flujo principal en main.rs
let mut ble_control = BleControl::start(initial_armed);
let mut wifi_session: Option<(EspWifi, EspHttpServer)> = None;

loop {
    // 1. Procesar comandos recibidos por BLE (Armar, Desarmar, Encender WiFi, Hora)
    if let Some(cmd) = ble_control.take_command() {
        match cmd {
            CMD_ARM => { config.set_armed(true); enter_deep_sleep(); },
            CMD_DISARM => { config.set_armed(false); },
            CMD_WIFI_ON => { 
                // Apagar BLE para liberar ~50KB RAM antes de encender WiFi
                drop(ble_control);
                ble::shutdown();
                wifi_session = Some(start_wifi_and_http());
            },
            ...
        }
    }
    ...
}
```

---

## 2.3 `src/ble.rs` — Servidor GATT NimBLE y Seguridad PIN 001989

### Explicación del Código:
Este módulo implementa el canal de control por Bluetooth Low Energy usando `esp32-nimble`.

#### Estructuras y Constantes:
- `SVC_UUID`: UUID de 128 bits que identifica el servicio de control de la alarma (`a1b2c3d4-1111-4a5b-8c6d-000000000001`).
- `STATE_UUID`: Característica de lectura y notificación (`NOTIFY`) que envía el estado comprimido de la cámara `[armed, motion, wifi_on]`.
- `CMD_UUID`: Característica de escritura (`WRITE`) donde la app Android envía los códigos de comando (`0x00` Desarmar, `0x01` Armar, `0x02` WiFi ON, `0x03` WiFi OFF, `0x04` Poner hora).

#### Seguridad por Passkey (PIN 001989):
```rust
let mut security = device.security();
security.set_auth(esp32_nimble::enums::AuthReq::Bond | esp32_nimble::enums::AuthReq::Mitm);
security.set_io_cap(esp32_nimble::enums::SecurityIOCap::DisplayOnly);
security.set_passkey(1989); // PIN estático de emparejamiento (001989)
```
Al requerir `Bond` y `Mitm` con `DisplayOnly`, Android obliga al usuario a introducir la clave `001989`. El vínculo cifrado se almacena en la memoria NVS del ESP32-S3.

#### Optimización de RAM (`pub fn shutdown()`):
El ESP32-S3 comparte la radio entre Bluetooth y WiFi. Mantener ambos activos consume más de 100 KB de RAM. Cuando la app solicita encender el WiFi (`CMD_WIFI_ON`), el firmware ejecuta `ble::shutdown()`, invocando `BLEDevice::deinit_full()`. Esto **destruye el stack Bluetooth y libera ~50 KB de RAM**, asegurando que el servidor HTTP y la cámara tengan memoria suficiente para los buffers de fotogramas.

---

## 2.4 `src/wifi.rs` y `src/server.rs` — WiFi y Servidor HTTP REST

### `src/wifi.rs`
Inicializa el driver de WiFi en modo **Access Point (AP)** con SSID `MIWIFI` y clave `moto1112` usando la pila TCP/IP LwIP de ESP-IDF.

### `src/server.rs`
Levanta un servidor HTTP multihilo en el puerto 80 (`EspHttpServer`). Define los siguientes controladores de rutas:
- **`GET /photo`**: Captura un cuadro y lo devuelve como `image/jpeg`.
- **`GET /capture`**: Guarda una foto en la SD (`/sdcard/CAP_<ms>.JPG`).
- **`POST /arm` & `POST /disarm`**: Modifican el estado de la alarma en NVS.
- **`POST /wifi_off`**: Ordena el apagado del servidor HTTP y de la interfaz WiFi, provocando que el bucle principal de `main.rs` vuelva a arrancar el servidor BLE.
- **`GET /photos`**: Devuelve una lista JSON/HTML de los archivos multimedia en la SD.
- **`GET /file/*`**: Transmite archivos de la SD a la app mediante **streaming por bloques (Chunked Response)**, evitando cargar archivos enteros en la memoria RAM.

---

## 2.5 `src/camera.rs` — Driver DVP y Software JPEG (GC0308)

### Explicación del Código:
La cámara GC0308 no posee un codificador JPEG por hardware integrado (a diferencia de la OV2640).
1. El driver en C (`esp32-camera`) captura la imagen en formato plano de píxeles **RGB565** o **YUV422** a resolución VGA (640x480).
2. `src/camera.rs` utiliza FFI para llamar a la función C `frame2jpg()` de Espressif:

```rust
pub fn capture_jpeg(&self) -> Result<Vec<u8>> {
    let fb = unsafe { camera_sys::esp_camera_fb_get() };
    if fb.is_null() { anyhow::bail!("Error obteniendo frame buffer"); }
    
    let mut out_buf: *mut u8 = std::ptr::null_mut();
    let mut out_len: usize = 0;
    
    // Conversión de RGB565/YUV a JPEG por software
    let ok = unsafe {
        camera_sys::frame2jpg(fb, 80, &mut out_buf, &mut out_len)
    };
    
    // Copiamos el buffer a un Vec<u8> seguro de Rust y liberamos el buffer de C
    let jpeg_bytes = unsafe { std::slice::from_raw_parts(out_buf, out_len).to_vec() };
    unsafe {
        camera_sys::free(out_buf as *mut _);
        camera_sys::esp_camera_fb_return(fb);
    }
    Ok(jpeg_bytes)
}
```

---

## 2.6 `src/video.rs` — Grabador de Vídeo MJPEG en Contenedor AVI

### Explicación del Código:
Dado que el procesador genera tramas JPEG individuales mediante software, este módulo empaqueta los fotogramas en un archivo `.AVI` estándar que cualquier reproductor puede interpretar:

1. **Cabecera RIFF/AVI:** Escribe la estructura estándar de contenedores de vídeo (encabezados `hdrl`, `avih`, `strh`, `strf`).
2. **Cuerpo del Vídeo (`LIST movi`):** Por cada fotograma capturado, escribe la etiqueta de chunk `00dc` seguida del tamaño en bytes y del buffer JPEG.
3. **Tabla de Índices (`idx1`):** Al finalizar la grabación, añade al final del archivo una tabla con los offsets exactos de cada fotograma para permitir el rebobinado y avance rápido.
4. **Parcheado Dinámico de FPS:** Dado que la velocidad de codificación por software varía según la carga (~3-5 fps), al cerrar el archivo el grabador calcula los FPS reales medidos (`frames_totales / segundos_transcurridos`) y sobrescribe la cabecera `avih` para que el vídeo se reproduzca a la velocidad correcta sin acelerarse ni ralentizarse.

---

## 2.7 `src/led.rs` — Driver RMT para LED RGB WS2812 (GPIO48)

### Explicación del Código:
El LED inteligente WS2812 requiere una precisión temporal de **nanosegundos** para enviar los bits `0` y `1`:
- **Bit 0:** Pulso ALTO de ~350 ns + Pulso BAJO de ~800 ns.
- **Bit 1:** Pulso ALTO de ~700 ns + Pulso BAJO de ~600 ns.

El código utiliza el periférico **RMT (Remote Control Peripheral)** del ESP32-S3 a través del driver moderno `esp_idf_hal::rmt::TxRmtDriver` y `FixedLengthSignal`:

```rust
let color: u32 = ((g as u32) << 16) | ((r as u32) << 8) | (b as u32);
let mut signal = FixedLengthSignal::<24>::new();
for i in 0..24 {
    let bit_set = (color >> (23 - i)) & 1 != 0;
    let (high, low) = if bit_set { (t1h, t1l) } else { (t0h, t0l) };
    signal.set(i as usize, &(high, low))?;
}
self.tx.start_blocking(&signal)?;
```

---

# 🤖 SECCIÓN 3: DESGLOSE COMPLETO DE LA APP ANDROID (KOTLIN)

La aplicación reside en `camesp32android/` y sigue la arquitectura **Clean Architecture + MVVM + Jetpack Compose**.

```text
camesp32android/app/src/main/java/com/vicherarr/camespdroid/
├── MainActivity.kt           # Activity principal de Android
├── ble/
│   └── BleManager.kt         # Cliente GATT Bluetooth Low Energy (Android Bluetooth API)
├── network/
│   ├── WifiLink.kt           # Gestor de red local con NetworkRequest / ConnectivityManager
│   └── CameraRepository.kt   # Cliente REST HTTP con OkHttp 4
├── model/
│   └── MediaItem.kt          # Modelo de datos para fotos y vídeos SD
├── viewmodel/
│   └── MainViewModel.kt      # ViewModel centralizado y gestión de máquina de estados
└── ui/
    ├── MainScreen.kt         # Scaffold, barra de navegación y Dialog de carga (8s)
    ├── screens/
    │   ├── HomeScreen.kt     # Panel de armar/desarmar y leyenda de leds
    │   ├── LiveStreamScreen.kt # Visor en vivo por polling de snapshots
    │   ├── GalleryScreen.kt  # Grilla de archivos de la tarjeta SD
    │   └── SettingsScreen.kt # Pantalla de diagnósticos
    └── video/
        └── MjpegAviPlayer.kt # Reproductor nativo de vídeos MJPEG-AVI en Kotlin
```

---

## 3.1 `BleManager.kt` — Comunicación Bluetooth en Android

### Explicación del Código:
Encargado de escanear, conectar y comunicarse con el servidor GATT `CAMSEC`:
- **Escaneo por UUID:** Filtra los dispositivos Bluetooth para encontrar únicamente el servicio `SVC_UUID`.
- **Escritura de Comandos:** Envía bytes directos al característico `CMD_UUID` (`0x01` Armar, `0x02` WiFi ON, etc.).
- **Sincronización de Hora (`CMD_SET_TIME`):** Convierte el tiempo epoch actual del smartphone en ms a 8 bytes en formato **Little Endian** y los transmite a la cámara.
- **Suscripción a Notificaciones:** Escucha los cambios en `STATE_UUID` para actualizar en tiempo real los iconos de estado de la app.

---

## 3.2 `WifiLink.kt` — Vinculación Local de Red sin Perder Datos Móviles

### Explicación del Código:
Cuando un móvil Android se conecta al WiFi de la cámara (`MIWIFI`), al notar que dicho WiFi no posee acceso a Internet, Android tiende a ignorar esa conexión o desconectar los datos móviles.

`WifiLink.kt` soluciona esto usando la API `ConnectivityManager` con un `NetworkRequest`:

```kotlin
val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .build()

connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
    override onAvailable(network: Network) {
        // Enlaza únicamente las peticiones de nuestra app a la red de la cámara
        connectivityManager.bindProcessToNetwork(network)
    }
})
```
Esto permite a la app comunicarse con la cámara por HTTP en `192.168.4.1` **sin interrumpir la conexión 4G/5G del resto del smartphone**.

---

## 3.3 `MainViewModel.kt` — Máquina de Estados y Prevención de Carreras

### Explicación del Código:
El `MainViewModel` mantiene el estado global en un `StateFlow<UiState>`.

#### Los Estados de Sesión (`MediaSession`):
- `None`: Conexión por BLE activa. WiFi apagado.
- `Opening`: En transición hacia WiFi (mostrando el diálogo con cuenta atrás de 8s).
- `Active`: WiFi enlazado y listo para En Vivo / Galería.
- `Closing`: En transición de vuelta a BLE.

#### Conmutación Implícita por Pestaña (`selectTab(index)`):
```kotlin
fun selectTab(index: Int) {
    _uiState.value = _uiState.value.copy(selectedTab = index)
    
    if (index == 1 || index == 2) { // En Vivo o Galería
        if (_uiState.value.mediaSession == MediaSession.None && _uiState.value.bleConnected) {
            openMediaSession()
        }
    } else if (index == 0 || index == 3) { // Inicio o Ajustes
        if (_uiState.value.mediaSession != MediaSession.None && _uiState.value.mediaSession != MediaSession.Closing) {
            closeMediaSession()
        }
    }
}
```

#### Manejo de Interrupciones y Evitación de Desincronización:
Si el usuario toca una pestaña mientras se está estableciendo la conexión, la corrutina `mediaJob` rastrea y cancela las tareas obsoletas para garantizar que la cámara y el móvil nunca queden colgados en estados inconsistentes.

---

## 3.4 `MainScreen.kt` — UI y Overlay Modal con Cuenta Atrás de 8s

### Explicación del Código:
Construido con Jetpack Compose. Renderiza el contenedor principal (`Scaffold`) y gestiona el **diálogo de carga modal ininterrumpible**:

```kotlin
var wifiCountdown by remember { mutableStateOf(8) }

LaunchedEffect(uiState.mediaSession) {
    if (uiState.mediaSession == MediaSession.Opening) {
        wifiCountdown = 8
        while (wifiCountdown > 0) {
            delay(1000)
            wifiCountdown--
        }
    }
}

if (uiState.mediaSession == MediaSession.Opening || uiState.mediaSession == MediaSession.Closing) {
    Dialog(onDismissRequest = { /* No cancelable */ }) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceDark) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentCyan)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (uiState.mediaSession == MediaSession.Opening) 
                        "Conectando WiFi de la cámara... ($wifiCountdown s)" 
                    else "Cerrando WiFi y volviendo a BLE...",
                    color = Color.White, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
```

---

## 3.5 `MjpegAviPlayer.kt` — Reproductor AVI Nativo en Kotlin

### Explicación del Código:
Android no incluye un decodificador nativo para archivos de vídeo `.AVI` codificados en formato MJPEG. Para evitar obligar al usuario a instalar reproductores de terceros (como VLC), se desarrolló `MjpegAviPlayer.kt`.

#### Algoritmo de Parsing Binario en Kotlin:
1. **Verificación de Cabecera:** Lee los primeros bytes buscando las marcas `RIFF` y `AVI `.
2. **Escaneo de Chunks `00dc`:** Recorre los bytes del archivo buscando la secuencia mágica `00dc` (que indica el inicio de un fotograma JPEG).
3. **Extracción y Decodificación:**
   - Extrae el bloque de bytes del JPEG entre `00dc` y el siguiente marcador.
   - Decodifica los bytes en un bitmap de Android usando `BitmapFactory.decodeByteArray()`.
4. **Bucle de Reproducción:** Un coroutine renderiza los bitmaps secuencialmente a la tasa de fotogramas adecuada en un canvas de Compose.

---

# 📝 SECCIÓN 4: CONCLUSIÓN Y MANTENIBILIDAD

El sistema **CamESP32 Moto Security System** representa un ejemplo avanzado de ingeniería de sistemas embebidos e IoT:
- Maximiza la autonomía mediante el apagado inteligente de radios y hardware.
- Protege la comunicación utilizando algoritmos de cifrado nativos y claves PIN persistentes en NVS.
- Proporciona una interfaz de usuario fluida, automatizada e intuitiva en Android sin requerir configuraciones complejas por parte del usuario final.
