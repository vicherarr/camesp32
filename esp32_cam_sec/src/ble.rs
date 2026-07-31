//! Canal de **control por BLE** (servidor GATT NimBLE vía `esp32-nimble`).
//!
//! La media (en vivo, galería, clips) sigue yendo por WiFi; BLE solo transporta el control
//! ligero: armar/desarmar, estado (armado/movimiento/wifi), poner la hora y encender/apagar WiFi.
//! Está siempre disponible en proximidad (la moto aparcada) con muy bajo consumo.

use std::collections::VecDeque;
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

/// Control BLE: mantiene el servidor GATT y expone al `main` los comandos recibidos y la
/// actualización de estado (con notify).
pub struct BleControl {
    /// Cola FIFO de comandos recibidos (para no perder comandos enviados en ráfaga).
    cmd_queue: Arc<StdMutex<VecDeque<u8>>>,
    /// Epoch en ms recibido con CMD_SET_TIME (0 = no recibido). (El S3 no tiene AtomicU64.)
    set_time_ms: Arc<StdMutex<u64>>,
    state_char: Arc<Mutex<BLECharacteristic>>,
    /// Último estado notificado empaquetado (armed|motion<<1|wifi<<2), 0xFF = aún ninguno.
    /// Evita notificar cada iteración: solo se emite BLE cuando el estado cambia de verdad.
    last_state: Arc<AtomicU8>,
}

impl BleControl {
    /// Arranca el stack BLE, crea el servicio/características y empieza a anunciarse como "CAMSEC".
    /// Puede llamarse de nuevo tras [`shutdown`]: `BLEDevice::init()` re-inicializa el controlador.
    pub fn start(initial_armed: bool) -> Self {
        let cmd_queue = Arc::new(StdMutex::new(VecDeque::<u8>::new()));
        let set_time_ms = Arc::new(StdMutex::new(0u64));

        // Idempotente: en el primer arranque no hace nada extra; tras un shutdown re-inicializa
        // el controlador BLE (que se deinicializó para liberar RAM durante la sesión WiFi).
        BLEDevice::init();
        let device = BLEDevice::take();
        let server = device.get_server();

        server.on_connect(|_server, _desc| info!("BLE: cliente conectado"));
        server.on_disconnect(|_desc, _reason| {
            info!("BLE: cliente desconectado, reiniciando anuncios");
            let mut adv = esp32_nimble::BLEDevice::take().get_advertising().lock();
            let _ = adv.start();
        });

        let service = server.create_service(SVC_UUID);

        // Característica Estado: [armed, motion, wifi_on] (read_enc + notify).
        // Se requiere estar emparejado (bonding) para leer.
        let state_char = service
            .lock()
            .create_characteristic(STATE_UUID, NimbleProperties::READ_ENC | NimbleProperties::NOTIFY);
        state_char.lock().set_value(&[initial_armed as u8, 0, 0]);

        // Característica Comando: write_enc.
        let cmd_char = service
            .lock()
            .create_characteristic(CMD_UUID, NimbleProperties::WRITE_ENC);
        let cq = cmd_queue.clone();
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
                if let Ok(mut q) = cq.lock() {
                    q.push_back(code);
                }
                info!("BLE: comando recibido 0x{:02x}", code);
            }
        });

        // Configuración de Seguridad BLE:
        // Requiere emparejamiento (Bond) y protección MITM (Passkey).
        let mut security = device.security();
        security.set_auth(esp32_nimble::enums::AuthReq::Bond | esp32_nimble::enums::AuthReq::Mitm);
        security.set_io_cap(esp32_nimble::enums::SecurityIOCap::DisplayOnly);
        security.set_passkey(123456); // PIN de emparejamiento estático
        security.resolve_rpa();

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

        Self { cmd_queue, set_time_ms, state_char, last_state: Arc::new(AtomicU8::new(0xFF)) }
    }

    /// Devuelve y consume el siguiente comando de la cola (FIFO), o None.
    pub fn take_command(&self) -> Option<u8> {
        self.cmd_queue.lock().ok()?.pop_front()
    }

    /// True si hay un comando `code` pendiente en la cola (sin consumirlo). Útil para abortar
    /// una grabación en curso cuando llega un DESARMAR.
    pub fn has_pending(&self, code: u8) -> bool {
        self.cmd_queue.lock().map(|q| q.contains(&code)).unwrap_or(false)
    }

    /// Epoch en ms recibido por CMD_SET_TIME (y lo consume), o None.
    pub fn take_set_time_ms(&self) -> Option<u64> {
        let mut g = self.set_time_ms.lock().ok()?;
        let t = *g;
        *g = 0;
        if t == 0 { None } else { Some(t) }
    }

    /// Actualiza la característica Estado y notifica a los clientes **solo si cambió** (evita
    /// inundar la conexión BLE con una notify cada iteración del bucle).
    pub fn update_state(&self, armed: bool, motion: bool, wifi_on: bool) {
        let packed = (armed as u8) | ((motion as u8) << 1) | ((wifi_on as u8) << 2);
        if self.last_state.swap(packed, Ordering::Relaxed) == packed {
            return; // sin cambios: no notificar
        }
        let mut ch = self.state_char.lock();
        ch.set_value(&[armed as u8, motion as u8, wifi_on as u8]);
        ch.notify();
    }

}

/// Apaga por completo el stack BLE (deinit del controlador) para **liberar ~50 KB de RAM**
/// durante una sesión WiFi. Llamar tras soltar (drop) el `BleControl`. Para reanudar el BLE,
/// crear un nuevo `BleControl::start` (que re-inicializa el controlador).
pub fn shutdown() {
    match BLEDevice::deinit_full() {
        Ok(_) => info!("BLE: apagado (deinit, RAM liberada para WiFi)"),
        Err(e) => ::log::error!("BLE deinit falló: {:?}", e),
    }
}
