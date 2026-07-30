//! Canal de **control por BLE** (servidor GATT NimBLE vía `esp32-nimble`).
//!
//! La media (en vivo, galería, clips) sigue yendo por WiFi; BLE solo transporta el control
//! ligero: armar/desarmar, estado (armado/movimiento/wifi), poner la hora y encender/apagar WiFi.
//! Está siempre disponible en proximidad (la moto aparcada) con muy bajo consumo.

use std::sync::{Arc, Mutex as StdMutex, atomic::{AtomicU8, Ordering}};
use esp32_nimble::{BLEDevice, BLEAdvertisementData, BLECharacteristic, NimbleProperties, uuid128};
use esp32_nimble::utilities::{BleUuid, mutex::Mutex};
use ::log::info;

// UUIDs del servicio "Alarm Control" (provisionales, 128-bit).
const SVC_UUID: BleUuid = uuid128!("a1b2c3d4-1111-4a5b-8c6d-000000000001");
const STATE_UUID: BleUuid = uuid128!("a1b2c3d4-1111-4a5b-8c6d-000000000002");
const CMD_UUID: BleUuid = uuid128!("a1b2c3d4-1111-4a5b-8c6d-000000000003");

// Códigos de comando (primer byte del write a la característica Comando).
pub const CMD_DISARM: u8 = 0x00;
pub const CMD_ARM: u8 = 0x01;
pub const CMD_WIFI_ON: u8 = 0x02;
pub const CMD_WIFI_OFF: u8 = 0x03;
pub const CMD_SET_TIME: u8 = 0x04; // seguido de 8 bytes: epoch en ms (little-endian)

const CMD_NONE: u8 = 0xFF;

/// Control BLE: mantiene el servidor GATT y expone al `main` los comandos recibidos y la
/// actualización de estado (con notify).
pub struct BleControl {
    /// Último comando recibido (CMD_NONE = ninguno). El main lo consume con `take_command`.
    last_cmd: Arc<AtomicU8>,
    /// Epoch en ms recibido con CMD_SET_TIME (0 = no recibido). (El S3 no tiene AtomicU64.)
    set_time_ms: Arc<StdMutex<u64>>,
    state_char: Arc<Mutex<BLECharacteristic>>,
}

impl BleControl {
    /// Arranca el stack BLE, crea el servicio/características y empieza a anunciarse como "CAMSEC".
    pub fn start(initial_armed: bool) -> Self {
        let last_cmd = Arc::new(AtomicU8::new(CMD_NONE));
        let set_time_ms = Arc::new(StdMutex::new(0u64));

        let device = BLEDevice::take();
        let server = device.get_server();

        server.on_connect(|_server, _desc| info!("BLE: cliente conectado"));
        server.on_disconnect(|_desc, _reason| info!("BLE: cliente desconectado"));

        let service = server.create_service(SVC_UUID);

        // Característica Estado: [armed, motion, wifi_on] (read + notify).
        let state_char = service
            .lock()
            .create_characteristic(STATE_UUID, NimbleProperties::READ | NimbleProperties::NOTIFY);
        state_char.lock().set_value(&[initial_armed as u8, 0, 0]);

        // Característica Comando: write. Guarda el comando para que lo procese el main.
        let cmd_char = service
            .lock()
            .create_characteristic(CMD_UUID, NimbleProperties::WRITE);
        let lc = last_cmd.clone();
        let st = set_time_ms.clone();
        cmd_char.lock().on_write(move |args| {
            let data = args.recv_data();
            if let Some(&code) = data.first() {
                if code == CMD_SET_TIME && data.len() >= 9 {
                    let mut ms = [0u8; 8];
                    ms.copy_from_slice(&data[1..9]);
                    if let Ok(mut g) = st.lock() {
                        *g = u64::from_le_bytes(ms);
                    }
                }
                lc.store(code, Ordering::SeqCst);
                info!("BLE: comando recibido 0x{:02x}", code);
            }
        });

        let advertising = device.get_advertising();
        advertising
            .lock()
            .set_data(
                BLEAdvertisementData::new()
                    .name("CAMSEC")
                    .add_service_uuid(SVC_UUID),
            )
            .expect("set BLE adv data");
        advertising.lock().start().expect("start BLE advertising");

        info!("BLE: anunciando como 'CAMSEC' (servicio Alarm Control)");

        Self { last_cmd, set_time_ms, state_char }
    }

    /// Devuelve y consume el último comando recibido por BLE, o None.
    pub fn take_command(&self) -> Option<u8> {
        let c = self.last_cmd.swap(CMD_NONE, Ordering::SeqCst);
        if c == CMD_NONE { None } else { Some(c) }
    }

    /// Epoch en ms recibido por CMD_SET_TIME (y lo consume), o None.
    pub fn take_set_time_ms(&self) -> Option<u64> {
        let mut g = self.set_time_ms.lock().ok()?;
        let t = *g;
        *g = 0;
        if t == 0 { None } else { Some(t) }
    }

    /// Actualiza la característica Estado y notifica a los clientes suscritos.
    pub fn update_state(&self, armed: bool, motion: bool, wifi_on: bool) {
        let mut ch = self.state_char.lock();
        ch.set_value(&[armed as u8, motion as u8, wifi_on as u8]);
        ch.notify();
    }
}
