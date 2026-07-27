# 📡 ESP32 WiFi Repeater (Extensor de Rango NAT)

Este es un proyecto diseñado específicamente para la placa **NodeMCU ESP32 con módulo ESP32-WROOM-32D (30 pines)**. Su función principal es actuar como un repetidor WiFi (NAT Router) ultraestable para extender el rango de red de un router principal, permitiendo así que dispositivos como el ESP32-S3-CAM se conecten con una señal robusta.

---

## 🛠️ Especificaciones Técnicas

- **Microcontrolador**: ESP32 (Xtensa LX6 Dual-Core a 240 MHz).
- **Firmware**: Escrito en Rust nativo utilizando el framework `esp-idf` y la pila de red `lwIP`.
- **Topología**: 
  - **STA (Cliente)**: Se conecta a la red origen (`DIGIFIBRA-42H6`).
  - **AP (Punto de Acceso)**: Levanta una red extendida (`DIGIFIBRA-42H6_EXT`).
- **Enrutamiento (NAPT)**: Implementa Network Address and Port Translation (NAPT) a nivel de núcleo LwIP, asegurando alta velocidad y bajo consumo de CPU.

---

## ⚡ Optimizaciones de Estabilidad y Alcance

Para cumplir el objetivo de **máximo alcance y estabilidad absoluta**, se han aplicado los siguientes parámetros en la configuración profunda del chip (`sdkconfig.defaults`):

1. **Potencia de Transmisión (TX) al Máximo**: Se ha forzado a la antena a emitir a 20 dBm (el límite máximo físico del hardware ESP32) mediante `CONFIG_ESP_PHY_MAX_WIFI_TX_POWER=20`.
2. **Limitación de Velocidad (Baud Rate) para Ganar Sensibilidad**: Se ha activado `CONFIG_ESP_WIFI_TX_BAUD_RATE_1MBPS=y`. Al obligar a transmitir con esquemas de modulación más bajos y lentos (802.11b), la onda de radio penetra mejor los obstáculos y el router receptor puede "escucharla" a distancias donde el 802.11n se caería. (Ideal para máxima penetración en exteriores o paredes gruesas).
3. **Optimización de Memoria de Red LwIP**: Se han triplicado los buffers TCP (`TCPIP_RECVMBOX_SIZE`, `TCP_WND_DEFAULT`) y los hilos de red para asegurar que los paquetes de vídeo y peticiones de la cámara no se saturen, permitiendo hasta 4 dispositivos conectados simultáneamente de forma impecable.
4. **Ciclo de Vigilancia Activa (Watchdog)**: El hilo principal revisa periódicamente el enlace (cada 15s). Si la red principal (DIGIFIBRA) cae, se reconecta automáticamente sin intervención humana.

---

## 🚀 Compilación y Carga del Firmware

### Requisitos
El entorno asume que se cuenta con el toolchain de Rust para arquitecturas Xtensa (instalado vía `espup`).

### Comandos de Flasheo
```bash
# 1. Navegar al directorio del repetidor
cd esp32_repeater

# 2. Cargar variables del entorno (si aplica)
source ~/.cargo/env
source ~/export-esp.sh

# 3. Flashear la placa ESP32 por el puerto serial
cargo espflash flash --release --port /dev/ttyUSB0 --monitor
```
*(Nota: Las placas NodeMCU ESP32 suelen presentarse como `/dev/ttyUSB0` o `/dev/ttyUSB1`, a diferencia de la placa S3-CAM que es `ttyACM0`).*

---

## 🔌 Conexión y Uso

1. Conecta la placa ESP32 (NodeMCU) a un cargador de pared o PowerBank genérico de 5V por su puerto Micro-USB. 
2. Coloca la placa en un punto intermedio entre el router principal y la zona oscura donde irá la cámara.
3. El repetidor se conectará automáticamente.
4. Ve a la App de la cámara o a tu ordenador, y conéctate a la nueva red WiFi:
   - **SSID**: `DIGIFIBRA-42H6_EXT`
   - **Clave**: `Uyy4ZEPhXP`

## 💡 Indicador LED (GPIO2)
El repetidor cuenta con un sistema visual de diagnóstico a través del LED rojo integrado en la placa:
- **Parpadeo Medio (cada medio segundo)**: Buscando conexión o reconectando a `DIGIFIBRA-42H6`.
- **Luz Fija Encendida**: Conexión establecida con éxito y red extendida (NAT) operativa.
- **Parpadeo Rápido (estroboscópico)**: Error grave (ej. no se encuentra la red tras varios intentos). La placa se reiniciará automáticamente.
