//! Grabador de clips de vídeo **MJPEG en contenedor AVI** para la SD.
//!
//! El ESP32-S3 no tiene codificador de vídeo por hardware, así que "vídeo" = secuencia de
//! fotogramas JPEG (Motion-JPEG) dentro de un `.avi`. La cámara GC0308 tampoco tiene JPEG por
//! hardware, por lo que cada frame se codifica por software (`frame2jpg`, ~0,5 s/frame): el clip
//! sale a ~2-4 fps reales. Para que la reproducción vaya a velocidad correcta, el header se
//! parchea al final con los **fps medidos reales** (frames / duración).
//!
//! Estructura AVI escrita: RIFF/AVI + LIST hdrl (avih + LIST strl(strh+strf)) + LIST movi
//! (un chunk `00dc` por frame) + índice `idx1`. VLC y ffmpeg lo abren directo.

use std::fs::File;
use std::io::{Seek, SeekFrom, Write};
use anyhow::Result;
use ::log::info;

// --- Offsets absolutos dentro del header fijo de 224 bytes (para parchear por seek) ---
const HEADER_LEN: u64 = 224;
const OFF_RIFF_SIZE: u64 = 4; // dwFileSize (total - 8)
const OFF_MICROSEC: u64 = 32; // avih.dwMicroSecPerFrame
const OFF_TOTAL_FRAMES: u64 = 48; // avih.dwTotalFrames
const OFF_STRH_RATE: u64 = 132; // strh.dwRate (con dwScale=1 -> fps)
const OFF_STRH_LENGTH: u64 = 140; // strh.dwLength (nº de frames)
const OFF_MOVI_SIZE: u64 = 216; // tamaño de la LIST 'movi'
const MOVI_FOURCC_POS: u64 = 220; // posición del fourcc 'movi' (los chunks van justo después)

/// Escribe un clip AVI-MJPEG a la SD, un frame JPEG cada vez.
pub struct VideoRecorder {
    file: File,
    /// (offset del chunk relativo al fourcc 'movi', tamaño del JPEG sin padding) por frame.
    index: Vec<(u32, u32)>,
    /// Posición de escritura actual dentro del archivo.
    pos: u64,
    start: std::time::Instant,
}

impl VideoRecorder {
    /// Crea el archivo y escribe el header con placeholders (se parchean en `finish`).
    /// `fps_hint` solo sirve como valor inicial del header; se corrige al cerrar.
    pub fn new(path: &str, width: u32, height: u32, fps_hint: u32) -> Result<Self> {
        let mut file = File::create(path)?;
        let header = build_header(width, height, fps_hint.max(1));
        debug_assert_eq!(header.len() as u64, HEADER_LEN);
        file.write_all(&header)?;
        info!("VideoRecorder: grabando {} ({}x{})", path, width, height);
        Ok(Self {
            file,
            index: Vec::new(),
            pos: HEADER_LEN,
            start: std::time::Instant::now(),
        })
    }

    /// Añade un fotograma JPEG como chunk `00dc` (+ byte de padding si su tamaño es impar).
    pub fn add_frame(&mut self, jpeg: &[u8]) -> Result<()> {
        let len = jpeg.len() as u32;
        // offset del chunk relativo al fourcc 'movi' (convención AVI clásica: primer chunk = 4).
        let chunk_offset = (self.pos - MOVI_FOURCC_POS) as u32;

        self.file.write_all(b"00dc")?;
        self.file.write_all(&len.to_le_bytes())?;
        self.file.write_all(jpeg)?;
        let mut written = 8 + len as u64;
        if len % 2 == 1 {
            self.file.write_all(&[0u8])?; // los chunks AVI se alinean a 2 bytes
            written += 1;
        }
        self.pos += written;
        self.index.push((chunk_offset, len));
        Ok(())
    }

    /// Nº de frames grabados hasta ahora.
    pub fn frame_count(&self) -> usize {
        self.index.len()
    }

    /// Escribe el índice `idx1`, parchea todos los tamaños y los fps reales, y cierra.
    pub fn finish(mut self) -> Result<()> {
        let n = self.index.len() as u32;
        let idx1_pos = self.pos;

        // --- Índice idx1: una entrada de 16 bytes por frame ---
        self.file.write_all(b"idx1")?;
        self.file.write_all(&(16u32 * n).to_le_bytes())?;
        for (offset, size) in &self.index {
            self.file.write_all(b"00dc")?; // dwChunkId
            self.file.write_all(&0x10u32.to_le_bytes())?; // dwFlags = AVIIF_KEYFRAME
            self.file.write_all(&offset.to_le_bytes())?; // dwOffset (relativo a 'movi')
            self.file.write_all(&size.to_le_bytes())?; // dwChunkLength
        }
        let file_size = idx1_pos + 8 + 16 * n as u64;

        // --- fps reales medidos (para que la reproducción no vaya ni rápida ni lenta) ---
        let secs = self.start.elapsed().as_secs_f64().max(0.001);
        let fps = ((n as f64) / secs).round().max(1.0) as u32;
        let micros_per_frame = (1_000_000.0 / fps as f64).round() as u32;

        // --- Parcheo de campos por seek ---
        // RIFF size = tamaño total - 8
        self.patch_u32(OFF_RIFF_SIZE, (file_size - 8) as u32)?;
        // movi LIST size = desde el fourcc 'movi' hasta justo antes de idx1
        self.patch_u32(OFF_MOVI_SIZE, (idx1_pos - MOVI_FOURCC_POS) as u32)?;
        self.patch_u32(OFF_TOTAL_FRAMES, n)?;
        self.patch_u32(OFF_STRH_LENGTH, n)?;
        self.patch_u32(OFF_MICROSEC, micros_per_frame)?;
        self.patch_u32(OFF_STRH_RATE, fps)?;

        self.file.flush()?;
        info!("VideoRecorder: clip cerrado, {} frames a ~{} fps ({} bytes)", n, fps, file_size);
        Ok(())
    }

    fn patch_u32(&mut self, offset: u64, value: u32) -> Result<()> {
        self.file.seek(SeekFrom::Start(offset))?;
        self.file.write_all(&value.to_le_bytes())?;
        Ok(())
    }
}

/// Construye el header AVI fijo (224 bytes) con los tamaños/frames a 0 (se parchean al cerrar).
fn build_header(width: u32, height: u32, fps: u32) -> Vec<u8> {
    let mut h: Vec<u8> = Vec::with_capacity(HEADER_LEN as usize);
    let micros = (1_000_000.0 / fps as f64).round() as u32;

    // RIFF
    h.extend_from_slice(b"RIFF");
    h.extend_from_slice(&0u32.to_le_bytes()); // [off 4] file size (patch)
    h.extend_from_slice(b"AVI ");

    // LIST hdrl (content = 192 bytes)
    h.extend_from_slice(b"LIST");
    h.extend_from_slice(&192u32.to_le_bytes());
    h.extend_from_slice(b"hdrl");

    // avih chunk (56 bytes de datos)
    h.extend_from_slice(b"avih");
    h.extend_from_slice(&56u32.to_le_bytes());
    h.extend_from_slice(&micros.to_le_bytes()); // [off 32] dwMicroSecPerFrame (patch)
    h.extend_from_slice(&0u32.to_le_bytes()); // dwMaxBytesPerSec
    h.extend_from_slice(&0u32.to_le_bytes()); // dwPaddingGranularity
    h.extend_from_slice(&0x10u32.to_le_bytes()); // dwFlags = AVIF_HASINDEX
    h.extend_from_slice(&0u32.to_le_bytes()); // [off 48] dwTotalFrames (patch)
    h.extend_from_slice(&0u32.to_le_bytes()); // dwInitialFrames
    h.extend_from_slice(&1u32.to_le_bytes()); // dwStreams
    h.extend_from_slice(&0u32.to_le_bytes()); // dwSuggestedBufferSize
    h.extend_from_slice(&width.to_le_bytes()); // dwWidth
    h.extend_from_slice(&height.to_le_bytes()); // dwHeight
    h.extend_from_slice(&[0u8; 16]); // dwReserved[4]

    // LIST strl (content = 116 bytes)
    h.extend_from_slice(b"LIST");
    h.extend_from_slice(&116u32.to_le_bytes());
    h.extend_from_slice(b"strl");

    // strh chunk (56 bytes de datos)
    h.extend_from_slice(b"strh");
    h.extend_from_slice(&56u32.to_le_bytes());
    h.extend_from_slice(b"vids"); // fccType
    h.extend_from_slice(b"MJPG"); // fccHandler
    h.extend_from_slice(&0u32.to_le_bytes()); // dwFlags
    h.extend_from_slice(&0u16.to_le_bytes()); // wPriority
    h.extend_from_slice(&0u16.to_le_bytes()); // wLanguage
    h.extend_from_slice(&0u32.to_le_bytes()); // dwInitialFrames
    h.extend_from_slice(&1u32.to_le_bytes()); // dwScale
    h.extend_from_slice(&fps.to_le_bytes()); // [off 132] dwRate (patch)
    h.extend_from_slice(&0u32.to_le_bytes()); // dwStart
    h.extend_from_slice(&0u32.to_le_bytes()); // [off 140] dwLength (patch)
    h.extend_from_slice(&0u32.to_le_bytes()); // dwSuggestedBufferSize
    h.extend_from_slice(&0u32.to_le_bytes()); // dwQuality
    h.extend_from_slice(&0u32.to_le_bytes()); // dwSampleSize
    h.extend_from_slice(&0i16.to_le_bytes()); // rcFrame.left
    h.extend_from_slice(&0i16.to_le_bytes()); // rcFrame.top
    h.extend_from_slice(&(width as i16).to_le_bytes()); // rcFrame.right
    h.extend_from_slice(&(height as i16).to_le_bytes()); // rcFrame.bottom

    // strf chunk = BITMAPINFOHEADER (40 bytes de datos)
    h.extend_from_slice(b"strf");
    h.extend_from_slice(&40u32.to_le_bytes());
    h.extend_from_slice(&40u32.to_le_bytes()); // biSize
    h.extend_from_slice(&(width as i32).to_le_bytes()); // biWidth
    h.extend_from_slice(&(height as i32).to_le_bytes()); // biHeight
    h.extend_from_slice(&1u16.to_le_bytes()); // biPlanes
    h.extend_from_slice(&24u16.to_le_bytes()); // biBitCount
    h.extend_from_slice(b"MJPG"); // biCompression
    h.extend_from_slice(&(width * height * 3).to_le_bytes()); // biSizeImage
    h.extend_from_slice(&0i32.to_le_bytes()); // biXPelsPerMeter
    h.extend_from_slice(&0i32.to_le_bytes()); // biYPelsPerMeter
    h.extend_from_slice(&0u32.to_le_bytes()); // biClrUsed
    h.extend_from_slice(&0u32.to_le_bytes()); // biClrImportant

    // LIST movi (size = patch, fourcc justo después en MOVI_FOURCC_POS = 220)
    h.extend_from_slice(b"LIST");
    h.extend_from_slice(&0u32.to_le_bytes()); // [off 216] movi size (patch)
    h.extend_from_slice(b"movi"); // [off 220]

    h
}
