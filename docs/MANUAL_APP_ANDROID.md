# 📱 Manual de Usuario - Aplicación Android CamEspDroid

Manual del usuario de la aplicación nativa para Android **CamEspDroid**, diseñada para el control remoto y visualización de la cámara de seguridad **ESP32-S3-CAM Moto Security**.

---

## 🚀 1. Instalación y Primeros Pasos

### 1.1 Requisitos Mínimos
- Smartphone con **Android 8.0 (API level 26)** o superior.
- **Bluetooth 4.2 / 5.0+** y radio **WiFi 2.4 GHz**.
- Permisos concedidos: *Dispositivos cercanos (Bluetooth)*, *Ubicación* (en Android 11 y anteriores para escaneo BLE) y *WiFi*.

### 1.2 Primera Vinculación Bluetooth (PIN 001989)
1. Abre la aplicación **CamEspDroid**.
2. Al iniciar por primera vez, Android solicitará permiso para buscar y conectarse a dispositivos cercanos. Acepta los permisos.
3. En la pestaña **Inicio**, la app buscará automáticamente el dispositivo `CAMSEC`.
4. El sistema Android mostrará un diálogo emergente pidiendo el PIN de emparejamiento.
5. Introduce la clave: **`001989`** (o `1989`).
6. Una vez emparejado, la aplicación recordará el vínculo permanentemente. Ya no volverás a ver este diálogo aunque reinicies la cámara o la app.

---

## 📱 2. Estructura de la Aplicación y Pestañas

La aplicación cuenta con 4 pestañas en la barra de navegación inferior, diseñadas con un esquema **totalmente automático de conmutación de red**:

```text
 ┌─────────────────────────────────────────────────────────┐
 │                   CamEsp32 Moto  [🩵 CONECTADO BLE]      │
 ├─────────────────────────────────────────────────────────┤
 │                                                         │
 │                    CONTENIDO DE LA PESTAÑA              │
 │                                                         │
 ├─────────────────────────────────────────────────────────┤
 │  [🏠 Inicio]   [📹 En Vivo]   [🖼️ Galería]   [⚙️ Ajustes] │
 └─────────────────────────────────────────────────────────┘
```

---

## 🧭 3. Funcionamiento de las Pestañas

### 🏠 Pestaña 1: Inicio (Bajo Consumo por BLE)
- **Modo de Red:** Conexión **Bluetooth Low Energy (BLE)** activa. El WiFi de la cámara está **apagado** para no consumir batería.
- **Badge Superior:** Muestra `🩵 CONECTADO BLE`.
- **Botón Armar Alarma:** Cambia el modo de la cámara a **Armado (Deep Sleep)**. El dispositivo se apaga y pasa a vigilancia activa por radar.
- **Botón Desarmar Alarma:** En caso de estar armada y en ventana de recepción, desarma la alarma y la devuelve al estado normal.
- **Leyenda del LED RGB:** Muestra un resumen visual interactivo con los colores del LED de la placa para que conozcas su estado físico en todo momento.

---

### 📹 Pestaña 2: En Vivo (Visión en Tiempo Real por WiFi)
- **Transición Automática:** Al tocar esta pestaña, la app ordena por BLE a la cámara encender su punto de acceso WiFi (`MIWIFI`).
- **Diálogo de Progreso:** Aparece una ventana modal con una **cuenta atrás interactiva de 8 segundos** mientras tu smartphone se vincula a la red WiFi local de la cámara (`"Conectando WiFi de la cámara... (8 s)"`).
- **Visor de Imágenes:** Una vez conectado (`💛 CONECTADO WIFI`), la pantalla muestra la transmisión en tiempo real a través de snapshots continuos.
- **Botón Capturar Foto:** Toma una fotografía en alta resolución y la almacena directamente en la tarjeta MicroSD de la cámara.

---

### 🖼️ Pestaña 3: Galería (Explorador SD y Reproductor AVI Nativo)
- **Transición Automática:** Al igual que en *En Vivo*, al seleccionar esta pestaña la app garantiza la conexión WiFi con la cuenta atrás de 8 segundos si venías de la pestaña Inicio.
- **Grilla de Contenidos:** Muestra todas las fotos (`.JPG`) y los clips de vídeo (`.AVI`) almacenados en la tarjeta MicroSD del dispositivo con sus miniaturas.
- **Reproducción Integrada de Vídeos MJPEG-AVI:**
  - Al tocar un clip de vídeo de la lista, se abre un visor modal con un **reproductor AVI nativo integrado**.
  - **No necesitas instalar reproductores externos** como VLC o MX Player; la app decodifica y reproduce los fotogramas dinámicamente en pantalla.
- **Borrado Masivo:** Incluye un botón para vaciar la tarjeta MicroSD de forma rápida con confirmación de seguridad.

---

### ⚙️ Pestaña 4: Ajustes
- **Modo de Red:** Conexión por BLE de bajo consumo.
- Muestra diagnósticos de la señal Bluetooth (RSSI), la dirección MAC del dispositivo, parámetros de red y opción para re-sincronizar la hora de la cámara con la del smartphone.

---

## ⚡ 4. Transiciones Automáticas e Implícitas (BLE ↔ WiFi)

Para maximizar la duración de la batería de la moto y ofrecer una experiencia de usuario perfecta, **no necesitas pulsar botones para encender el WiFi ni conectar el Bluetooth**:

1. **Estar en Inicio o Ajustes** ➔ La app mantiene la conexión por **BLE** (consumo mínimo).
2. **Cambiar a En Vivo o Galería** ➔ La app envía el comando `CMD_WIFI_ON` por BLE, muestra la **pantalla de carga de 8 segundos** y enlaza la red de la cámara internamente sin quitar la conexión de datos móviles de tu teléfono.
3. **Volver a Inicio o Ajustes** ➔ La app envía el comando HTTP `/wifi_off` a la cámara. La cámara apaga su red WiFi, enciende su Bluetooth y tu móvil se reconecta a **BLE** en cuestión de un segundo.

---

## ❓ 5. Preguntas Frecuentes (FAQ)

### ¿Se desconecta el Internet de mi móvil cuando entro a En Vivo o Galería?
**No.** La aplicación utiliza la API avanzada de `ConnectivityManager` de Android para enlazar únicamente el tráfico de la cámara a la red local `MIWIFI`, manteniendo los datos móviles (4G/5G) de tu teléfono activos para WhatsApp, llamadas, etc.

### Me pide el PIN otra vez tras apagar la moto, ¿es normal?
**No.** Si introdujiste el PIN correcto **`001989`**, la clave se guarda permanentemente en la memoria del dispositivo y de Android. Si te lo vuelve a pedir, asegúrate de no haber borrado los dispositivos vinculados en los ajustes Bluetooth de tu teléfono.
