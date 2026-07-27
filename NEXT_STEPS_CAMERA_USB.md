# Fase 2: Implementación de Cámara USB OTG (UVC) en Rust

## Estado Actual del Proyecto (Aprobado y Probado)
- **Modo Red**: Funciona en modo Cliente (STA) por defecto apuntando a la fibra del usuario. Si falla, hace *fallback* automático a AP (Access Point). Reinicios automatizados desde la app.
- **Sensor de Movimiento**: Conectado al GPIO 13, funcionando en un bucle asíncrono no bloqueante.
- **App Android**: Muestra el estado en tiempo real. Configurada para descubrir la IP por UDP (`DISCOVER_CAMESP32`), o en su defecto conectarse por configuración manual.
- **Tarjeta SD**: Configurada para inicializarse en el arranque. Actualmente guarda un "Dummy JPEG" cuando el sensor de movimiento salta, para poder testear la Galería de la app de Android.

## Siguiente Tarea Pendiente
Implementar el driver nativo UVC (USB Video Class) en Rust para capturar los frames reales cuando se enchufe la cámara física USB OTG en los pines del ESP32-S3.

### Pasos a seguir (Para el Agente IA que retome esto)
1. **Quitar el Dummy JPEG**: En `esp32_cam_sec/src/camera.rs`, eliminar el array de bytes simulado (`dummy_jpeg`) dentro de `take_picture()`.
2. **Integrar esp-iot-solution**: Añadir dependencias en `build.rs` para linkear el componente C `usb_stream` (o usar FFI para importar los headers del driver de Espressif UVC).
3. **Driver USB Host**: Iniciar la negociación USB y los *endpoints* Isochronous/Bulk. El ESP32-S3 debe configurarse como USB Host.
4. **Captura de Fotogramas**: Al solicitar `take_picture()`, recuperar un frame en formato MJPEG real desde el stream USB e inyectarlo en la SD.
5. **Precaución**: Todo debe probarse de manera incremental conectando la cámara física y monitorizando los logs USB para evitar que un `panic!` rompa el bucle de red y el sensor.
