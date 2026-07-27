# Fase 2: Implementación de Cámara USB OTG (UVC) en Rust y Mejoras de Red

## Estado Actual del Proyecto (Resolución de Problemas)
- **Modo Red**: Funciona en modo Cliente (STA) por defecto apuntando a la fibra del usuario. IP por defecto en App fijada a `192.168.1.143`.
- **Sensor de Movimiento**: Conectado al GPIO 13, funcionando en un bucle asíncrono no bloqueante. Se ha ajustado para capturar solo 1 frame por evento de movimiento (en vez de hacer ráfagas) para no saturar la tarjeta SD.
- **App Android**: 
  - La Galería SD está funcionando.
  - **BUG RESUELTO**: Se producían falsas caídas de WiFi en la App al abrir la Galería porque Coil abría demasiados sockets a la vez. Se ha solucionado implementando `ImageLoaderFactory` con `maxRequestsPerHost = 1`.
- **Tarjeta SD**: 
  - **MEJORA**: Se ha devuelto la velocidad de la tarjeta SD a 20MHz (por defecto) ya que la inestabilidad de red era puramente por la saturación de conexiones desde Android, logrando así lecturas más rápidas.
  - **BUG RESUELTO**: El endpoint web `/photos` ahora responde en streaming y está limitado a los 100 archivos más recientes para evitar agotar la RAM de la placa y colgar las peticiones (`timeout` en Android).

## Siguiente Tarea Pendiente
1. ~~**Poner IP Fija real en la placa**~~: **COMPLETADO**. La placa de la cámara ahora tiene asignada la IP estática `192.168.71.220` (gestionada mediante `ipaddr_addr` de la API en C `esp_netif_set_ip_info`).
2. ~~**Crear enrutador/repetidor NAT (esp32_repeater)**~~: **COMPLETADO**. Se ha implementado un repetidor ESP32 intermedio con NAPT usando LwIP (ip4_napt) que levanta la red extendida (`DIGIFIBRA-42H6_EXT` en `192.168.71.1`). 
    - **Solución NAPT**: Se ha configurado correctamente el *Port Forwarding* usando `ip_portmap_add`, asociándolo a la interfaz WAN (STA IP: `192.168.1.220`), permitiendo el acceso HTTP transparente desde la red principal. Todo el ruteo de extremo a extremo está operativo.
3. **Implementar el driver nativo UVC (USB Video Class)**: Para capturar los frames reales cuando se enchufe la cámara física USB OTG en los pines D+/D- del ESP32-S3.

### Diseño: Captura en Ráfaga y Envío a Telegram
Para evitar perder el momento crítico sin saturar la red, la lógica de movimiento operará así:
1. Al detectar movimiento, capturar una **ráfaga de 20 fotos** (1 foto cada ~0.5 segundos).
2. **Guardar cada foto** en la tarjeta SD de forma inmediata según se van tomando.
3. **Telegram Upload**: 
   - La **primera foto** de la ráfaga se debe enviar a Telegram **inmediatamente** en un hilo paralelo.
   - Las 19 fotos restantes se meterán en una **cola de subida en segundo plano** que las irá leyendo de la SD y enviando a Telegram poco a poco (una a una, con retraso) para no saturar la memoria RAM ni colgar la placa ESP32.
   - *Nota Técnica*: Habrá que añadir la inicialización de DNS (`esp_netif_set_dns_info`) en `wifi.rs` y un cliente HTTP/TLS (como `esp_idf_svc::http::client`) para atacar la API de `api.telegram.org`.

### Pasos a seguir para la Cámara Física (Para el Agente IA que retome esto)
1. **Quitar el Dummy JPEG**: En `esp32_cam_sec/src/camera.rs`, eliminar el array de bytes simulado (`dummy_jpeg`) dentro de `take_picture()`.
2. **Integrar esp-iot-solution**: Añadir dependencias en `build.rs` para linkear el componente C `usb_stream` (o usar FFI para importar los headers del driver de Espressif UVC).
3. **Driver USB Host**: Iniciar la negociación USB y los *endpoints* Isochronous/Bulk. El ESP32-S3 debe configurarse como USB Host.
4. **Captura de Fotogramas**: Modificar `main.rs` para implementar el bucle de ráfaga (20 fotos) e implementar la cola de envío en segundo plano para Telegram.
5. **Precaución**: Todo debe probarse de manera incremental conectando la cámara física y monitorizando los logs USB para evitar que un `panic!` rompa el bucle de red y el sensor.

