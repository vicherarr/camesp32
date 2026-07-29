mod camera;
mod server;
mod storage;
mod wifi;
mod config;
mod discovery;
mod logbuf;
mod led;

use esp_idf_sys::esp_sleep_enable_ext0_wakeup;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::gpio::*;
use ::log::{info, error};
use std::thread;
use std::time::{Duration, SystemTime};
use esp_idf_svc::sys;
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use esp_idf_svc::nvs::EspDefaultNvsPartition;

fn main() -> anyhow::Result<()> {
    sys::link_patches();
    // Ring-buffer logger instead of EspLogger: keeps the last lines in RAM so
    // they can be read over WiFi (/logs) after USB host mode kills the console.
    logbuf::init();

    info!("Starting ESP32-CAM Security Project (WiFi Always-On)...");

    let peripherals = Peripherals::take()?;

    // Status RGB LED (onboard WS2812 on GPIO48). Optional: never fail boot over it.
    let mut status = match led::StatusLed::new(peripherals.rmt.channel0, peripherals.pins.gpio48) {
        Ok(l) => Some(l),
        Err(e) => { error!("Status LED init failed (continuing): {}", e); None }
    };
    if let Some(s) = status.as_mut() { let _ = s.blue(); }   // azul = arrancando

    let nvs_partition = EspDefaultNvsPartition::take()?;
    let (app_config, _) = config::load_config(nvs_partition.clone());

    // Motion sensor on GPIO14. NOTE: GPIO13 was a HARDWARE CONFLICT with the camera
    // PCLK; the microwave sensor OUT must be wired to GPIO14 (free, RTC-capable).
    let motion_sensor = PinDriver::input(peripherals.pins.gpio14, Pull::Down)?;

    const ENABLE_SD: bool = true;
    let sd = if ENABLE_SD {
        match storage::Storage::new() {
            Ok(s) => Some(s),
            Err(e) => {
                error!("Storage init failed (continuing without SD): {}", e);
                None
            }
        }
    } else {
        info!("SD storage disabled at boot (no card / driver guard). Skipping mount.");
        None
    };
    
    let motion_state = Arc::new(AtomicBool::new(false));
    // Shared flag so the /photo HTTP endpoint knows the camera is initialized.
    let camera_ready = Arc::new(AtomicBool::new(false));

    if let Some(s) = status.as_mut() { let _ = s.purple(); }  // morado = WiFi arrancando
    info!("Starting WiFi and Web Server...");
    let m = unsafe { esp_idf_hal::modem::Modem::steal() };
    
    let mut wifi_mode = if app_config.mode == "STA" {
        wifi::WifiMode::STA
    } else {
        wifi::WifiMode::AP
    };
    
    let mut _wifi_mgr = match wifi::WifiManager::new(m, wifi_mode.clone(), nvs_partition.clone()) {
        Ok(w) => Some(w),
        Err(e) => {
            error!("Failed to start WiFi in configured mode: {}. Falling back to AP mode.", e);
            // Fallback to AP
            wifi_mode = wifi::WifiMode::AP;
            let m2 = unsafe { esp_idf_hal::modem::Modem::steal() };
            wifi::WifiManager::new(m2, wifi_mode.clone(), nvs_partition.clone()).ok()
        }
    };
    
    let current_mode_str = match wifi_mode {
        wifi::WifiMode::AP => "AP".to_string(),
        wifi::WifiMode::STA => "STA".to_string(),
    };

    let _web_server = server::WebServer::new(
        motion_state.clone(),
        nvs_partition.clone(),
        current_mode_str,
        camera_ready.clone(),
    ).ok();
    if let Some(s) = status.as_mut() { let _ = s.cyan(); }    // cian = red/servidor listos

    discovery::start_discovery_server();

    // Camera init happens last, after the network stack is up: when this becomes
    // a real USB-OTG host driver it takes over the USB pins (killing the serial
    // console), so we want WiFi + /logs already running to observe/diagnose it.
    let cam = match camera::Camera::new() {
        Ok(c) => Some(c),
        Err(e) => {
            error!("Camera init failed (continuing without camera): {}", e);
            None
        }
    };

    // LED verde = camara real inicializada; rojo = fallo/mock. Marca camera_ready
    // para que /photo pueda capturar en vivo por WiFi.
    let cam_ok = cam.as_ref().map(|c| c.is_real()).unwrap_or(false);
    camera_ready.store(cam_ok, Ordering::Relaxed);
    if let Some(s) = status.as_mut() {
        let _ = if cam_ok { s.green() } else { s.red() };
    }

    // Deep-sleep wakeup on the motion pin (GPIO14, matches the sensor above).
    unsafe {
        esp_sleep_enable_ext0_wakeup(14, 1);
    }

    let idle_color = cam_ok; // idle verde solo si la camara esta OK; rojo si no
    let mut tick: u32 = 0;
    loop {
        let is_motion = motion_sensor.is_high();
        motion_state.store(is_motion, Ordering::Relaxed);

        if is_motion {
            info!("Motion detected!");
            if let Some(s) = status.as_mut() { let _ = s.white(); }   // flash blanco al capturar
            if let Some(ref camera) = cam {
                if let Some(pic) = camera.take_picture() {
                    let filename = format!("img_{}.jpg", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_secs());
                    if let Some(ref storage) = sd {
                        let _ = storage.save_photo(&filename, &pic);
                    }
                    info!("Saved photo to /sdcard/{}", filename);
                }
            }

            info!("Waiting for 5s of inactivity...");
            let mut inactive_start = SystemTime::now();
            while inactive_start.elapsed().unwrap().as_secs() < 5 {
                if motion_sensor.is_high() {
                    inactive_start = SystemTime::now();
                }
                thread::sleep(Duration::from_millis(100));
            }
            info!("Entering sleep/idle mode...");
            if let Some(s) = status.as_mut() {
                let _ = if idle_color { s.green() } else { s.red() };
            }
        }

        // Latido idle: parpadeo tenue cada ~3s para indicar "vivo" sin molestar.
        tick = tick.wrapping_add(1);
        if !is_motion {
            if let Some(s) = status.as_mut() {
                if tick % 15 == 0 {
                    let _ = if idle_color { s.green_dim() } else { s.red() };
                } else if tick % 15 == 1 {
                    let _ = if idle_color { s.green() } else { s.red() };
                }
            }
        }

        thread::sleep(Duration::from_millis(200));
    }
}

