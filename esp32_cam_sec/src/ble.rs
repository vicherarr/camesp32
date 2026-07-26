use esp32_nimble::BLEDevice;
use log::info;
use std::sync::{Arc, Mutex};

pub struct BleManager {
    pub is_connected: Arc<Mutex<bool>>,
}

impl BleManager {
    pub fn new() -> anyhow::Result<Self> {
        let device = BLEDevice::take();
        BLEDevice::set_device_name("ESP32-CAM-WiFi-Trigger")?;

        let server = device.get_server();
        let is_connected = Arc::new(Mutex::new(false));

        let is_connected_clone = is_connected.clone();
        server.on_connect(move |_, _| {
            info!("BLE Client connected! Waking up WiFi...");
            *is_connected_clone.lock().unwrap() = true;
        });

        let is_connected_clone2 = is_connected.clone();
        server.on_disconnect(move |_, _| {
            info!("BLE Client disconnected.");
            *is_connected_clone2.lock().unwrap() = false;
        });

        let ble_advertising = device.get_advertising();
        ble_advertising.lock().start()?;

        info!("BLE Advertising started.");

        Ok(Self { is_connected })
    }
}
