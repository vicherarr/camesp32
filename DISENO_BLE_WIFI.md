# Diseño híbrido BLE + WiFi (cámara de moto)

> Caso de uso: cámara de seguridad para **moto**, se aparca lejos de casa (sin WiFi propia),
> uso en **proximidad**. Alimentación por powerbank. Ver decisiones tomadas al final.

## Principio
- **BLE = control** (siempre disponible en proximidad, ~1 mA en light-sleep): armar, desarmar,
  estado, poner la hora, encender/apagar WiFi.
- **WiFi AP = media bajo demanda** (en vivo, galería, clips): se enciende solo cuando se pide,
  y la app se conecta con **enlace local** (Android `WifiNetworkSpecifier`) para no cambiar la
  WiFi normal del móvil ni memorizar la red. Se apaga al salir.
- `alarm_armed` (NVS) solo decide **si el movimiento graba**. No hay reinicio al armar/desarmar
  (light-sleep conserva la RAM → cambio en vivo).

## Runtime del firmware (unificado)
```
BOOT → carga NVS (alarm_armed, ap_ssid/pass, pin) → arranca BLE (advertising + GATT)
       → configura PM (automatic light sleep) + wake por GPIO14 (movimiento)
LOOP (dirigido por eventos, light-sleep entre medias ~1mA):
  · Movimiento (GPIO14):
      - si armed: init cámara → grabar clip AVI a SD (con hora) → deinit cámara
      - si desarmado: solo marca estado/notifica
  · Comando BLE:
      - arm/disarm → set NVS alarm_armed (en vivo)
      - set-time(epoch) → fija RTC
      - wifi-on → levanta AP + servidor HTTP; notifica IP por BLE; lock anti-sleep
      - wifi-off (o timeout) → baja WiFi; vuelve a light-sleep
  · Durante grabación o WiFi-AP activo: PM lock (no light-sleep). Resto: light-sleep.
```

## GATT (servicio "Alarm Control")
- Service UUID: `a1b2c3d4-1111-4a5b-8c6d-000000000001` (provisional).
- **Estado** (read + notify): bytes `[armed, motion, wifi_on, ip(4)]`.
- **Comando** (write, con auth por PIN/bonding): 1 byte + args
  - `0x00` desarmar · `0x01` armar · `0x02` wifi-on · `0x03` wifi-off · `0x04` set-time (+8 bytes epoch ms)
- Nombre BLE: `CAMSEC-xxxx` (sufijo del MAC). Bonding con PIN.

## Ficheros y hora
- **FATFS LFN activado** (`CONFIG_FATFS_LONG_FILENAMES`) → nombres largos.
- Clip: `AAAAMMDD-HHMMSS.avi` si hay hora; si no, `CLIP0001.avi`.
- La hora la fija el móvil por BLE al conectar; el RTC la mantiene en light-sleep.

## App Android (pantallas)
- **Inicio**: estado por BLE (armado/desarmado, conectado, movimiento) + botón Armar/Desarmar
  (BLE). Indicadores BLE ● / WiFi ●.
- **En Vivo**: botón "encender cámara" → BLE wifi-on → la app abre el stream por la IP con
  enlace local. Solo con WiFi.
- **Galería**: media por WiFi bajo demanda. Fotos con Coil; **vídeos con reproductor MJPEG in-app**
  (parsea el AVI, extrae los JPEG `00dc`, play/pausa).
- **Ajustes**: emparejar BLE (elegir dispositivo + PIN), SSID/clave del AP de la placa, hora.
  Sin IP manual.
- Capa nueva: `BleManager` (escaneo/bond/notify/write) + uso de `WifiNetworkSpecifier` para la media.
- Permisos: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, y los de WiFi/red ya presentes.

## Seguridad
- BLE: bonding con **PIN** (solo el móvil emparejado desarma).
- HTTP (media): añadir **auth básica** (usuario/clave) al servidor.

## Fases de implementación
1. **Firmware BLE core**: esp32-nimble, servicio GATT, arm/disarm/set-time; verificable con
   `bluetoothctl`. (Sin tocar aún el sleep.)
2. **Runtime unificado + PM light-sleep** (~1 mA) + WiFi AP on-demand + LFN/hora.
3. **App**: BLE + WifiNetworkSpecifier + reproductor de vídeo + rediseño de pantallas.
4. Auth HTTP + pulido + pruebas en placa.

## Decisiones tomadas (2026-07-30)
- WiFi: **AP bajo demanda + enlace local** (no STA; se aparca sin WiFi de casa).
- Vídeo: **reproductor in-app**.
- BLE: **emparejado con PIN**.
- Alimentación: powerbank (el ~1 mA del BLE en light-sleep es aceptable).
