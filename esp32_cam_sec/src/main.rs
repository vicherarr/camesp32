mod camera;
mod server;
mod storage;
mod wifi;
mod config;
mod discovery;

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
    esp_idf_svc::log::EspLogger::initialize_default();
    
    info!("Starting ESP32-CAM Security Project (WiFi Always-On)...");

    let peripherals = Peripherals::take()?;
    
    let nvs_partition = EspDefaultNvsPartition::take()?;
    let (app_config, _) = config::load_config(nvs_partition.clone());
    
    let motion_sensor = PinDriver::input(peripherals.pins.gpio13, Pull::Down)?;

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
    
    let cam = match camera::Camera::new() {
        Ok(c) => Some(c),
        Err(e) => {
            error!("Camera init failed (continuing without camera): {}", e);
            None
        }
    };
    
    let motion_state = Arc::new(AtomicBool::new(false));

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

    let _web_server = server::WebServer::new(motion_state.clone(), nvs_partition.clone(), current_mode_str).ok();

    discovery::start_discovery_server();

    unsafe {
        esp_sleep_enable_ext0_wakeup(13, 1);
    }

    loop {
        let is_motion = motion_sensor.is_high();
        motion_state.store(is_motion, Ordering::Relaxed);

        if is_motion {
            info!("Motion detected!");
            let start = SystemTime::now();
            let mut count = 0;
            while start.elapsed().unwrap().as_secs() < 10 {
                if let Some(ref camera) = cam {
                    if let Some(pic) = camera.take_picture() {
                        let filename = format!("img_{}.jpg", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_secs());
                        if let Some(ref storage) = sd {
                            let _ = storage.save_photo(&filename, &pic);
                        }
                        count += 1;
                    }
                }
                thread::sleep(Duration::from_millis(500));
            }
            info!("Capture finished. Took {} photos.", count);
            
            info!("Waiting for 5s of inactivity...");
            let mut inactive_start = SystemTime::now();
            while inactive_start.elapsed().unwrap().as_secs() < 5 {
                if motion_sensor.is_high() {
                    inactive_start = SystemTime::now();
                }
                thread::sleep(Duration::from_millis(100));
            }
            info!("Entering sleep/idle mode...");
        }

        thread::sleep(Duration::from_millis(200));
    }
}

