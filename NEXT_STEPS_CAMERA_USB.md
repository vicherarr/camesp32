# Fase 2: Implementación de Cámara USB OTG (UVC) en Rust y Mejoras de Red

## Estado Actual del Proyecto (Resolución de Problemas)
- **Modo Red**: Funciona en modo Cliente (STA) por defecto apuntando a la fibra del usuario. IP por defecto en App fijada a `192.168.1.143`.
- **Sensor de Movimiento**: Conectado al GPIO 13, funcionando en un bucle asíncrono no bloqueante. Se ha ajustado para capturar solo 1 frame por evento de movimiento (en vez de hacer ráfagas) para no saturar la tarjeta SD.
- **App Android**: 
  - La Galería SD está funcionando.
  - **BUG RESUELTO**: Se producían falsas caídas de WiFi en la App al abrir la Galería porque Coil abría demasiados sockets a la vez. Se ha solucionado implementando `ImageLoaderFactory` con `maxRequestsPerHost = 1`.
- **Tarjeta SD**: 
  - **BUG RESUELTO**: Se ha reducido el `max_freq_khz` de la tarjeta a 10MHz en `storage.rs` para prevenir picos de voltaje que desconectaban el WiFi del ESP32.
  - **BUG RESUELTO**: El endpoint web `/photos` ahora responde en streaming y está limitado a los 100 archivos más recientes para evitar agotar la RAM de la placa y colgar las peticiones (`timeout` en Android).

## Siguiente Tarea Pendiente
1. **Poner IP Fija real en la placa**: La app apunta a `192.168.1.143` pero falta asegurarse de que la placa ESP32 realmente negocie y reclame siempre esa IP fija en `wifi.rs` en el router del usuario.
2. **Implementar el driver nativo UVC (USB Video Class)**: Para capturar los frames reales cuando se enchufe la cámara física USB OTG en los pines D+/D- del ESP32-S3.

### Pasos a seguir para la Cámara Física (Para el Agente IA que retome esto)
1. **Quitar el Dummy JPEG**: En `esp32_cam_sec/src/camera.rs`, eliminar el array de bytes simulado (`dummy_jpeg`) dentro de `take_picture()`.
2. **Integrar esp-iot-solution**: Añadir dependencias en `build.rs` para linkear el componente C `usb_stream` (o usar FFI para importar los headers del driver de Espressif UVC).
3. **Driver USB Host**: Iniciar la negociación USB y los *endpoints* Isochronous/Bulk. El ESP32-S3 debe configurarse como USB Host.
4. **Captura de Fotogramas**: Al solicitar `take_picture()`, recuperar un frame en formato MJPEG real desde el stream USB e inyectarlo en la SD.
5. **Precaución**: Todo debe probarse de manera incremental conectando la cámara física y monitorizando los logs USB para evitar que un `panic!` rompa el bucle de red y el sensor.
