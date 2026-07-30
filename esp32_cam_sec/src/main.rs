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

// --- Parámetros del rediseño (Modelo A: el modo de alarma dicta el consumo) ---
/// Pin del sensor de microondas (RTC-capaz, usado como ext0 wakeup). GPIO13 chocaba con el PCLK.
const MOTION_GPIO: i32 = 14;
/// Duración del clip de vídeo grabado por cada evento de movimiento (modo armado).
const CLIP_DURATION_SECS: u64 = 10;
/// Ventana WiFi tras cada evento/arranque armado para poder DESARMAR desde la app.
const DISARM_WINDOW_SECS: u64 = 45;
/// Resolución del clip de vídeo (VGA por defecto; configurable aquí).
const VIDEO_WIDTH: u32 = 640;
const VIDEO_HEIGHT: u32 = 480;

/// RSSI (dBm) del AP al que está conectada la STA, o None si no hay enlace.
fn coverage_rssi() -> Option<i8> {
    unsafe {
        let mut ap: esp_idf_sys::wifi_ap_record_t = std::mem::zeroed();
        if esp_idf_sys::esp_wifi_sta_get_ap_info(&mut ap) == esp_idf_sys::ESP_OK {
            Some(ap.rssi)
        } else {
            None
        }
    }
}

/// Muestra un estado del sistema en el LED de a bordo (ignora silenciosamente si no hay LED).
/// `phase` alterna en cada tick para animar los estados con parpadeo/pulso.
fn show_led(status: &mut Option<led::StatusLed<'static>>, state: led::LedState, phase: bool) {
    if let Some(s) = status.as_mut() {
        let _ = s.show(state, phase);
    }
}

fn main() -> anyhow::Result<()> {
    sys::link_patches();
    // Ring-buffer logger: guarda las últimas líneas en RAM para leerlas por WiFi (/logs).
    logbuf::init();

    info!("Arrancando ESP32-CAM (Modelo A: alarma con bajo consumo)...");

    let peripherals = Peripherals::take()?;

    // Status RGB LED (WS2812 en GPIO48). Opcional: nunca abortar el boot por él.
    let mut status = match led::StatusLed::new(peripherals.rmt.channel0, peripherals.pins.gpio48) {
        Ok(l) => Some(l),
        Err(e) => { error!("Status LED init failed (continuando): {}", e); None }
    };
    show_led(&mut status, led::LedState::Booting, true); // 🔴 arrancando

    let nvs_partition = EspDefaultNvsPartition::take()?;
    let (app_config, _) = config::load_config(nvs_partition.clone());

    // Causa de despertar: distinguir un evento de movimiento (ext0) de un power-on/reset normal.
    let wakeup_cause = unsafe { esp_idf_sys::esp_sleep_get_wakeup_cause() };
    let woke_by_motion = wakeup_cause == esp_idf_sys::esp_sleep_source_t_ESP_SLEEP_WAKEUP_EXT0;
    info!(
        "Estado: alarm_armed={}, mode={}, wakeup_cause={}, woke_by_motion={}",
        app_config.alarm_armed, app_config.mode, wakeup_cause, woke_by_motion
    );

    // Sensor de movimiento en GPIO14.
    let motion_sensor = PinDriver::input(peripherals.pins.gpio14, Pull::Down)?;

    // Almacenamiento SD (necesario para clips y galería).
    let sd = match storage::Storage::new() {
        Ok(s) => Some(s),
        Err(e) => { error!("Storage init failed (continuando sin SD): {}", e); None }
    };

    if app_config.alarm_armed {
        run_armed(status, nvs_partition, app_config, motion_sensor, sd, woke_by_motion)
    } else {
        run_disarmed(status, nvs_partition, app_config, motion_sensor, sd)
    }
}

/// MODO DESARMADO: WiFi always-on, la app tiene control total, el movimiento NO graba.
fn run_disarmed(
    mut status: Option<led::StatusLed<'static>>,
    nvs: EspDefaultNvsPartition,
    config: config::AppConfig,
    motion_sensor: PinDriver<'static, Input>,
    _sd: Option<storage::Storage>,
) -> anyhow::Result<()> {
    info!("== MODO DESARMADO: WiFi always-on, sin grabación ==");

    // FASE 1: arrancar BLE PRIMERO, con memoria libre al máximo (antes de WiFi/cámara), para
    // descartar que el fallo de init del controlador BT sea por falta de RAM tras WiFi.
    let ble = ble::BleControl::start(false);

    let motion_state = Arc::new(AtomicBool::new(false));
    let camera_ready = Arc::new(AtomicBool::new(false));

    let modem = unsafe { esp_idf_hal::modem::Modem::steal() };
    let wifi_mode = if config.mode == "STA" { wifi::WifiMode::STA } else { wifi::WifiMode::AP };
    let _wifi = wifi::WifiManager::new(modem, wifi_mode, nvs.clone()).ok();

    let _server = server::WebServer::new(
        motion_state.clone(),
        nvs.clone(),
        config.mode.clone(),
        camera_ready.clone(),
        false, // armed=false
    ).ok();

    discovery::start_discovery_server();

    // Cámara para /photo, /capture y En Vivo por snapshots.
    let cam = camera::Camera::new().ok();
    let cam_ok = cam.as_ref().map(|c| c.is_real()).unwrap_or(false);
    camera_ready.store(cam_ok, Ordering::Relaxed);

    let mut tick: u32 = 0;
    loop {
        tick = tick.wrapping_add(1);
        // 🟢 verde en pulso = desarmado, control total. El movimiento solo marca estado, no graba.
        show_led(&mut status, led::LedState::Disarmed, tick % 2 == 0);
        let is_motion = motion_sensor.is_high();
        motion_state.store(is_motion, Ordering::Relaxed);

        // Procesar comandos BLE (Fase 1: solo log/estado; la lógica completa llega en Fase 2).
        if let Some(cmd) = ble.take_command() {
            info!("BLE cmd 0x{:02x} recibido en el bucle desarmado", cmd);
        }
        if let Some(t) = ble.take_set_time_ms() {
            info!("BLE set-time: {} ms epoch", t);
        }
        ble.update_state(false, is_motion, false);

        if tick % 25 == 0 {
            info!("Desarmado. motion={} rssi={:?}", is_motion, coverage_rssi());
        }
        thread::sleep(Duration::from_millis(500));
    }
}

/// MODO ARMADO: bajo consumo. Graba clip por evento y abre ventana WiFi para desarmar; luego duerme.
fn run_armed(
    mut status: Option<led::StatusLed<'static>>,
    nvs: EspDefaultNvsPartition,
    config: config::AppConfig,
    motion_sensor: PinDriver<'static, Input>,
    sd: Option<storage::Storage>,
    woke_by_motion: bool,
) -> anyhow::Result<()> {
    info!("== MODO ARMADO: bajo consumo (deep sleep) ==");

    // Cámara para grabar el evento.
    let cam = camera::Camera::new().ok();

    // 1) Si el despertar fue por movimiento, graba un clip inmediatamente.
    if woke_by_motion {
        show_led(&mut status, led::LedState::Recording, true); // 🟣 magenta = grabando
        record_event_clip(cam.as_ref(), sd.as_ref());
    } else {
        info!("Arranque armado sin movimiento (arm/power-on): ventana de gracia antes de dormir");
    }

    // 2) Ventana WiFi para poder DESARMAR desde la app (POST /disarm reinicia a desarmado).
    let motion_state = Arc::new(AtomicBool::new(false));
    let cam_ok = cam.as_ref().map(|c| c.is_real()).unwrap_or(false);
    let camera_ready = Arc::new(AtomicBool::new(cam_ok));

    let modem = unsafe { esp_idf_hal::modem::Modem::steal() };
    let wifi_mode = if config.mode == "STA" { wifi::WifiMode::STA } else { wifi::WifiMode::AP };
    let _wifi = wifi::WifiManager::new(modem, wifi_mode, nvs.clone()).ok();
    let _server = server::WebServer::new(
        motion_state.clone(),
        nvs.clone(),
        config.mode.clone(),
        camera_ready.clone(),
        true, // armed=true
    ).ok();
    discovery::start_discovery_server();

    info!("Ventana WiFi de {}s abierta (conecta la app para DESARMAR)...", DISARM_WINDOW_SECS);
    let window_start = Instant::now();
    while window_start.elapsed().as_secs() < DISARM_WINDOW_SECS {
        // Si la app pulsa /disarm, guarda NVS(armed=false) y reinicia: esta espera no vuelve.
        motion_state.store(motion_sensor.is_high(), Ordering::Relaxed);
        // 🔵 azul parpadeante = ventana WiFi activa (se puede desarmar).
        let phase = (window_start.elapsed().as_millis() / 400) % 2 == 0;
        show_led(&mut status, led::LedState::ArmedWindow, phase);
        thread::sleep(Duration::from_millis(200));
    }

    // 3) Nadie desarmó: preparar deep sleep. ext0 despierta por nivel ALTO, así que si el sensor
    // sigue activo esperamos a que baje para no despertar al instante (con tope de seguridad).
    info!("Ventana cerrada. Preparando DEEP SLEEP...");
    let wait_low = Instant::now();
    while motion_sensor.is_high() && wait_low.elapsed().as_secs() < 15 {
        thread::sleep(Duration::from_millis(200));
    }

    if let Some(c) = cam.as_ref() { c.deinit(); }
    show_led(&mut status, led::LedState::Sleeping, true); // ⚫ apagado
    info!("Entrando en DEEP SLEEP. Despertará por movimiento (GPIO{}).", MOTION_GPIO);
    thread::sleep(Duration::from_millis(100));
    unsafe {
        esp_idf_sys::esp_sleep_enable_ext0_wakeup(MOTION_GPIO, 1);
        esp_idf_sys::esp_deep_sleep_start();
    }
    // esp_deep_sleep_start() no retorna.
    #[allow(unreachable_code)]
    Ok(())
}

/// Busca el siguiente índice de clip libre en la SD escaneando los "CLIPnnnn.AVI" existentes.
/// (No podemos usar el uptime: se reinicia en cada deep sleep y sobreescribiría los clips.)
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
    next % 10000 // "CLIPnnnn" = 8 chars (límite FAT 8.3 con LFN off)
}

/// Graba un clip AVI-MJPEG a la SD durante `CLIP_DURATION_SECS`.
fn record_event_clip(cam: Option<&camera::Camera>, sd: Option<&storage::Storage>) {
    let cam = match cam {
        Some(c) if c.is_real() => c,
        _ => { error!("Sin cámara real: no se graba el clip"); return; }
    };
    if sd.is_none() {
        error!("Sin SD montada: no se puede guardar el clip");
        return;
    }

    // Nombre corto FAT 8.3 (LFN off, máx 8 chars): "CLIPnnnn.AVI" (4 + 4 dígitos = 8).
    let idx = next_clip_index();
    let path = format!("/sdcard/CLIP{:04}.AVI", idx);

    let mut rec = match video::VideoRecorder::new(&path, VIDEO_WIDTH, VIDEO_HEIGHT, 3) {
        Ok(r) => r,
        Err(e) => { error!("No se pudo crear {}: {}", path, e); return; }
    };
    info!("Grabando clip {} durante {}s...", path, CLIP_DURATION_SECS);
    let t0 = Instant::now();
    while t0.elapsed().as_secs() < CLIP_DURATION_SECS {
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
