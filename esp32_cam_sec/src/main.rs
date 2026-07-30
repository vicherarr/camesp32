mod camera;
mod server;
mod storage;
mod wifi;
mod config;
mod discovery;
mod logbuf;
mod led;
mod video;
mod ble;

use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::gpio::*;
use ::log::{info, error};
use std::thread;
use std::time::{Duration, Instant};
use esp_idf_svc::sys;
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use esp_idf_svc::nvs::EspDefaultNvsPartition;

// --- Parámetros ---
/// Pin del sensor de microondas (RTC-capaz).
const MOTION_GPIO: i32 = 14;
/// Duración del clip de vídeo por evento de movimiento.
const CLIP_DURATION_SECS: u64 = 10;
/// Tras grabar, espera este tiempo de inactividad antes de permitir otro clip.
const RECORD_COOLDOWN_SECS: u64 = 3;
/// Resolución del clip (VGA por defecto).
const VIDEO_WIDTH: u32 = 640;
const VIDEO_HEIGHT: u32 = 480;

/// Ajusta el reloj del sistema (RTC) a partir de un epoch en milisegundos (recibido por BLE).
fn set_system_time_ms(ms: u64) {
    let tv = esp_idf_svc::sys::timeval {
        tv_sec: (ms / 1000) as esp_idf_svc::sys::time_t,
        tv_usec: ((ms % 1000) * 1000) as esp_idf_svc::sys::suseconds_t,
    };
    unsafe { esp_idf_svc::sys::settimeofday(&tv, std::ptr::null()); }
    info!("Reloj ajustado por BLE a {} ms epoch", ms);
}

/// Muestra un estado del sistema en el LED (ignora si no hay LED).
fn show_led(status: &mut Option<led::StatusLed<'static>>, state: led::LedState, phase: bool) {
    if let Some(s) = status.as_mut() {
        let _ = s.show(state, phase);
    }
}

fn main() -> anyhow::Result<()> {
    sys::link_patches();
    logbuf::init();
    info!("Arrancando ESP32-CAM (híbrido BLE control + WiFi media)...");

    let peripherals = Peripherals::take()?;

    let mut status = match led::StatusLed::new(peripherals.rmt.channel0, peripherals.pins.gpio48) {
        Ok(l) => Some(l),
        Err(e) => { error!("Status LED init failed (continuando): {}", e); None }
    };
    show_led(&mut status, led::LedState::Booting, true);

    let nvs_partition = EspDefaultNvsPartition::take()?;
    let (mut cfg, mut nvs_handle) = config::load_config(nvs_partition.clone());
    info!("Config: alarm_armed={}, mode={}", cfg.alarm_armed, cfg.mode);

    let motion_sensor = PinDriver::input(peripherals.pins.gpio14, Pull::Down)?;

    let sd = match storage::Storage::new() {
        Ok(s) => Some(s),
        Err(e) => { error!("Storage init failed (continuando sin SD): {}", e); None }
    };

    // BLE PRIMERO (antes de WiFi): si no, el controlador BT se queda sin RAM y crashea.
    let ble = ble::BleControl::start(cfg.alarm_armed);

    // Cámara (una vez; se usa para grabar y para /photo cuando hay WiFi).
    let cam = camera::Camera::new().ok();
    let cam_ok = cam.as_ref().map(|c| c.is_real()).unwrap_or(false);

    let motion_state = Arc::new(AtomicBool::new(false));
    let camera_ready = Arc::new(AtomicBool::new(cam_ok));
    let armed_flag = Arc::new(AtomicBool::new(cfg.alarm_armed));

    // WiFi inicializado UNA vez (deja lwip + netif listos) y radio apagada de inmediato.
    // Toggleamos solo la radio (esp_wifi_start/stop): sin re-enlazar el puerto 80 ni caer lwip.
    let wifi_mode = if cfg.mode == "STA" { wifi::WifiMode::STA } else { wifi::WifiMode::AP };
    let modem = unsafe { esp_idf_hal::modem::Modem::steal() };
    let mut wifi_mgr = wifi::WifiManager::new(modem, wifi_mode, nvs_partition.clone()).ok();
    // NOTA: por ahora el WiFi AP arranca ENCENDIDO y estable (togglear la radio con BLE
    // conectado da inestabilidad de coexistencia; el apagado on-demand/light-sleep es fase aparte).
    let mut wifi_on = wifi_mgr.is_some();

    // Servidor HTTP creado UNA vez (escucha siempre en :80; lwip ya está arriba).
    let _server = server::WebServer::new(
        motion_state.clone(), nvs_partition.clone(), cfg.mode.clone(), camera_ready.clone(), armed_flag.clone(),
    ).ok();

    let mut last_record = Instant::now() - Duration::from_secs(RECORD_COOLDOWN_SECS + 1);
    let mut tick: u32 = 0;

    info!("Runtime híbrido listo. armed={}, cámara={}", cfg.alarm_armed, cam_ok);

    loop {
        tick = tick.wrapping_add(1);
        let is_motion = motion_sensor.is_high();
        motion_state.store(is_motion, Ordering::Relaxed);

        // --- Hora recibida por BLE ---
        if let Some(ms) = ble.take_set_time_ms() {
            set_system_time_ms(ms);
        }

        // --- Comandos BLE (control): drenar toda la cola ---
        while let Some(cmd) = ble.take_command() {
            match cmd {
                ble::CMD_ARM => {
                    armed_flag.store(true, Ordering::Relaxed); cfg.alarm_armed = true;
                    let _ = config::save_config(&mut nvs_handle, &cfg);
                    info!("BLE: ARMADO");
                }
                ble::CMD_DISARM => {
                    armed_flag.store(false, Ordering::Relaxed); cfg.alarm_armed = false;
                    let _ = config::save_config(&mut nvs_handle, &cfg);
                    info!("BLE: DESARMADO");
                }
                ble::CMD_WIFI_ON => {
                    if !wifi_on {
                        if let Some(w) = wifi_mgr.as_mut() {
                            match w.radio_on() {
                                Ok(_) => { wifi_on = true; info!("Media WiFi ENCENDIDA."); }
                                Err(e) => error!("radio_on falló: {}", e),
                            }
                        }
                    }
                }
                ble::CMD_WIFI_OFF => {
                    if wifi_on {
                        if let Some(w) = wifi_mgr.as_mut() { let _ = w.radio_off(); }
                        wifi_on = false;
                        info!("Media WiFi APAGADA.");
                    }
                }
                other => info!("BLE: comando desconocido 0x{:02x}", other),
            }
        }
        let armed = armed_flag.load(Ordering::Relaxed);

        // --- Grabación por movimiento (solo armado, con enfriamiento) ---
        if armed && is_motion && last_record.elapsed().as_secs() >= RECORD_COOLDOWN_SECS {
            show_led(&mut status, led::LedState::Recording, true);
            record_event_clip(cam.as_ref(), sd.as_ref(), &ble);
            last_record = Instant::now();
            // Esperar inactividad para no encadenar clips.
            let mut quiet = Instant::now();
            while quiet.elapsed().as_secs() < RECORD_COOLDOWN_SECS {
                if ble.has_pending(ble::CMD_DISARM) { break; } // desarme inmediato
                if motion_sensor.is_high() { quiet = Instant::now(); }
                ble.update_state(armed, motion_sensor.is_high(), wifi_on);
                thread::sleep(Duration::from_millis(150));
            }
        }

        // --- Estado BLE + LED ---
        ble.update_state(armed, is_motion, wifi_on);
        let led_state = if armed { led::LedState::ArmedWindow } else { led::LedState::Disarmed };
        show_led(&mut status, led_state, tick % 2 == 0);

        if tick % 40 == 0 {
            info!("armed={} motion={} wifi={}", armed, is_motion, wifi_on);
        }

        thread::sleep(Duration::from_millis(250));
    }
}

/// Busca el siguiente índice de clip libre en la SD (los CLIPnnnn.avi existentes).
fn next_clip_index() -> u32 {
    let mut next = 0u32;
    if let Ok(entries) = std::fs::read_dir("/sdcard") {
        for e in entries.flatten() {
            let name = e.file_name().to_string_lossy().to_ascii_uppercase();
            if let Some(rest) = name.strip_prefix("CLIP") {
                let digits = rest.split('.').next().unwrap_or("");
                if let Ok(n) = digits.parse::<u32>() {
                    if n + 1 > next { next = n + 1; }
                }
            }
        }
    }
    next % 10000
}

/// Graba un clip AVI-MJPEG a la SD durante `CLIP_DURATION_SECS`.
fn record_event_clip(cam: Option<&camera::Camera>, sd: Option<&storage::Storage>, ble: &ble::BleControl) {
    let cam = match cam {
        Some(c) if c.is_real() => c,
        _ => { error!("Sin cámara real: no se graba el clip"); return; }
    };
    if sd.is_none() {
        error!("Sin SD montada: no se puede guardar el clip");
        return;
    }

    // Nombre con fecha-hora si el reloj está puesto (LFN); si no, índice corto de reserva.
    let path = match storage::now_stamp() {
        Some(ts) => format!("/sdcard/{}.avi", ts),
        None => format!("/sdcard/CLIP{:04}.avi", next_clip_index()),
    };

    let mut rec = match video::VideoRecorder::new(&path, VIDEO_WIDTH, VIDEO_HEIGHT, 3) {
        Ok(r) => r,
        Err(e) => { error!("No se pudo crear {}: {}", path, e); return; }
    };
    info!("Grabando clip {} durante {}s...", path, CLIP_DURATION_SECS);
    let t0 = Instant::now();
    while t0.elapsed().as_secs() < CLIP_DURATION_SECS {
        // Cortar la grabación si llega un DESARMAR por BLE (respuesta rápida al usuario).
        if ble.has_pending(ble::CMD_DISARM) {
            info!("DESARMAR pendiente: cierro el clip antes de tiempo");
            break;
        }
        if let Some(jpeg) = cam.take_picture() {
            if let Err(e) = rec.add_frame(&jpeg) {
                error!("Error escribiendo frame: {}", e);
                break;
            }
        }
    }
    let frames = rec.frame_count();
    match rec.finish() {
        Ok(_) => info!("Clip {} guardado ({} frames)", path, frames),
        Err(e) => error!("Error cerrando clip {}: {}", path, e),
    }
}
