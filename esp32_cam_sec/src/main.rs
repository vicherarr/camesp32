mod ble;
mod camera;
mod server;
mod storage;
mod wifi;

use esp_idf_sys::esp_sleep_enable_ext0_wakeup;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::gpio::*;
use ::log::{info, error};
use std::thread;
use std::time::{Duration, SystemTime};
use esp_idf_svc::sys;

fn main() -> anyhow::Result<()> {
    sys::link_patches();
    esp_idf_svc::log::EspLogger::initialize_default();
    
    info!("Starting ESP32-CAM Security Project...");

    let peripherals = Peripherals::take()?;
    
    let motion_sensor = PinDriver::input(peripherals.pins.gpio13, Pull::Down)?;
    
    let sd = storage::Storage::new()?;
    let cam = camera::Camera::new().map_err(|e| anyhow::anyhow!(e))?;
    let ble_mgr = ble::BleManager::new()?;
    
    let mut wifi_mgr: Option<wifi::WifiManager> = None;
    let mut web_server: Option<server::WebServer> = None;

    unsafe {
        esp_sleep_enable_ext0_wakeup(13, 1);
    }

    loop {
        if motion_sensor.is_high() {
            info!("Motion detected! Starting capture...");
            let start = SystemTime::now();
            let mut count = 0;
            while start.elapsed().unwrap().as_secs() < 10 {
                if let Some(pic) = cam.take_picture() {
                    let filename = format!("img_{}.jpg", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_secs());
                    let _ = sd.save_photo(&filename, &pic);
                    count += 1;
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

        let is_connected = *ble_mgr.is_connected.lock().unwrap();
        if is_connected {
            if wifi_mgr.is_none() {
                info!("Client connected via BLE. Starting WiFi AP and Web Server...");
                let m = unsafe { esp_idf_hal::modem::Modem::steal() };
                match wifi::WifiManager::new(m) {
                    Ok(w) => {
                        wifi_mgr = Some(w);
                        web_server = server::WebServer::new().ok();
                    }
                    Err(e) => error!("Failed to start WiFi: {}", e),
                }
            }
        } else {
            if wifi_mgr.is_some() {
                info!("Client disconnected from BLE. Stopping WiFi and Web Server...");
                web_server = None;
                wifi_mgr = None;
            }
        }

        thread::sleep(Duration::from_millis(200));
    }
}
