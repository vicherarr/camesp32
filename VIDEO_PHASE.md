# Fase Futura (NO prioritaria): Grabación de Vídeo

> Análisis de viabilidad realizado el 2026-07-28 con datos reales de la webcam elegida
> (**Microdia / Sonix `0c45:2006`**, MJPEG). **Prioridad actual: FOTOS**, según lo ya definido.
> Este documento queda como referencia para cuando se aborde el vídeo.

## Resumen ejecutivo

Es viable grabar vídeo en la ESP32-S3-CAM, **pero solo como Motion-JPEG (MJPEG) en contenedor AVI**.
El ESP32-S3 **no tiene codificador de vídeo por hardware** (ni H.264/H.265; eso solo lo trae el
ESP32-P4), así que "vídeo" = secuencia de fotogramas JPEG guardados en un `.avi`.

**Objetivo realista y fiable: clips AVI-MJPEG de 640×480 a ~10 fps, grabados por evento de
movimiento** (no grabación continua). Sin audio (la cámara no tiene micrófono UAC).

## Datos medidos (frames MJPEG reales de la cámara)

| Resolución | Peso real por frame |
| :--- | :--- |
| 640×480 | **~52 KB** |
| 1280×720 | ~131 KB |

## Los dos cuellos de botella

### 1. Ancho de banda USB — Full-Speed
El USB del ESP32-S3 es **solo Full-Speed** (~780 KB/s útiles con la alt-setting isócrona de 800 B/frame).
- 640×480 → 780 ÷ 52 ≈ **15 fps máximo**.
- 1280×720 → ~6 fps (descartado para vídeo fluido).

### 2. Escritura en tarjeta SD — modo 1-bit (cuello más serio)
El firmware monta la SD en **1-bit** (`src/storage.rs`: solo D0, `CLK=39 / CMD=38 / D0=40`, `width=1`).
Escritura sostenida real ≈ **0.5–1.5 MB/s**.
- 640×480 @ 15 fps = 780 KB/s → **al límite** (riesgo de frames perdidos).
- 640×480 @ 10 fps = 520 KB/s → **seguro**.

Tamaño de archivo: 640×480 @ 10 fps ≈ **1.8 GB/hora**. FAT32 limita a **4 GB por archivo**
→ hay que **trocear en clips** (por tiempo o por evento de movimiento).

## Cómo implementarlo (cuando toque)

1. **Hilo de captura USB** (el mismo driver UVC de la fase de foto) volcando frames a un
   **ring-buffer en PSRAM** (~2–4 MB; la placa tiene PSRAM octal) para desacoplar captura de escritura.
2. **Hilo escritor** que vacía el ring-buffer a un archivo `.avi` MJPEG en la SD.
3. **Contenedor AVI**: cabecera RIFF + un chunk `00dc` por frame + índice `idx1` al final, con el
   valor de fps correcto para que se reproduzca bien (VLC/ffmpeg lo abren directo).
4. **Troceado de archivos** por límite de 4 GB / por duración / por evento.

## Mejoras de hardware si el vídeo pasa a ser importante

- **Cablear la SD en 4-bit** (si la placa expone D1–D3) → ~4× la velocidad de escritura, elimina el
  cuello de la SD.
- **Migrar a ESP32-P4**: USB **High-Speed** (480 Mbps) + **codificador H.264 por hardware** →
  vídeo HD real y comprimido. Es la vía si se quiere vídeo "en serio"; el S3 siempre estará
  limitado a MJPEG de baja resolución.
