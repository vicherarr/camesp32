//! LED de estado RGB de a bordo (WS2812 en GPIO48 de la Freenove ESP32-S3-WROOM).
//!
//! Se controla con el periférico RMT (sin crates extra). Un solo LED direccionable, orden GRB.
//!
//! # Estados del sistema señalizados por el LED
//!
//! Cada estado del firmware tiene **un color y un patrón propios** para poder entender de un
//! vistazo qué está haciendo la placa sin cable serie:
//!
//! | Estado         | Color        | Patrón            | Significado                                        |
//! |----------------|--------------|-------------------|----------------------------------------------------|
//! | `Booting`      | 🔴 rojo      | fijo              | Arrancando / inicializando periféricos             |
//! | `Disarmed`     | 🟢 verde     | pulso lento       | Desarmado: WiFi activo, control total, NO graba    |
//! | `ArmedWindow`  | 🔵 azul      | parpadeo          | Armado: ventana WiFi abierta, puedes DESARMAR      |
//! | `Recording`    | 🟣 magenta   | fijo              | Grabando clip de vídeo por movimiento              |
//! | `Sleeping`     | ⚫ apagado   | —                 | Deep sleep (bajo consumo); despierta por el sensor |
//! | `Error`        | 🔴 rojo      | parpadeo rápido   | Fallo de cámara o SD                               |
//!
//! Los patrones con parpadeo/pulso usan el argumento `phase` de [`StatusLed::show`]: el bucle
//! principal alterna ese booleano en cada tick, y el LED alterna brillo/encendido en consecuencia.

use core::time::Duration;
use anyhow::Result;
use esp_idf_hal::gpio::OutputPin;
use esp_idf_hal::rmt::{
    config::TransmitConfig, FixedLengthSignal, PinState, Pulse, RmtChannel, TxRmtDriver,
};

/// Estado global del sistema que se representa en el LED de a bordo. Ver la tabla del módulo.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LedState {
    /// Arrancando / inicializando (rojo fijo).
    Booting,
    /// Desarmado: WiFi always-on, control total desde la app, el movimiento no graba (verde, pulso).
    Disarmed,
    /// Armado con la ventana WiFi abierta: la app puede desarmar (azul, parpadeo).
    ArmedWindow,
    /// Grabando un clip de vídeo por evento de movimiento (magenta, fijo).
    Recording,
    /// A punto de dormir / dormido en deep sleep (apagado).
    Sleeping,
    /// Fallo de cámara/SD (rojo, parpadeo rápido).
    Error,
}

pub struct StatusLed<'d> {
    tx: TxRmtDriver<'d>,
}

pub enum ConnMode {
    None,
    Ble,
    Wifi,
}

impl<'d> StatusLed<'d> {
    pub fn new<C: RmtChannel + 'd>(
        channel: C,
        pin: impl OutputPin + 'd,
    ) -> Result<Self> {
        let config = TransmitConfig::new().clock_divider(1);
        let tx = TxRmtDriver::new(channel, pin, &config)?;
        Ok(Self { tx })
    }

    /// Envía un color de 24 bits (WS2812 espera orden GRB).
    pub fn set(&mut self, r: u8, g: u8, b: u8) -> Result<()> {
        let color: u32 = ((g as u32) << 16) | ((r as u32) << 8) | (b as u32);
        let hz = self.tx.counter_clock()?;
        let t0h = Pulse::new_with_duration(hz, PinState::High, &Duration::from_nanos(350))?;
        let t0l = Pulse::new_with_duration(hz, PinState::Low, &Duration::from_nanos(800))?;
        let t1h = Pulse::new_with_duration(hz, PinState::High, &Duration::from_nanos(700))?;
        let t1l = Pulse::new_with_duration(hz, PinState::Low, &Duration::from_nanos(600))?;

        let mut signal = FixedLengthSignal::<24>::new();
        for i in 0..24 {
            let bit_set = (color >> (23 - i)) & 1 != 0;
            let (high, low) = if bit_set { (t1h, t1l) } else { (t0h, t0l) };
            signal.set(i as usize, &(high, low))?;
        }
        self.tx.start_blocking(&signal)?;
        Ok(())
    }

    /// Apaga el LED.
    pub fn off(&mut self) -> Result<()> {
        self.set(0, 0, 0)
    }

    /// Muestra el color/patrón correspondiente al estado del sistema, intercalado con
    /// el estado de conexión si `show_conn` es true.
    pub fn show(&mut self, state: LedState, phase: bool, conn: ConnMode, show_conn: bool) -> Result<()> {
        if show_conn {
            match conn {
                ConnMode::Ble => return self.set(0, 40, 60),  // 🩵 cian (BLE)
                ConnMode::Wifi => return self.set(60, 40, 0), // 💛 amarillo (WiFi)
                ConnMode::None => {} // Si no hay conexión, sigue con el estado normal
            }
        }

        match state {
            LedState::Booting => self.set(60, 0, 0), // 🔴 rojo fijo
            LedState::Disarmed => {
                // 🟢 pulso verde suave: alterna brillo para indicar "vivo y en control".
                if phase { self.set(0, 60, 0) } else { self.set(0, 16, 0) }
            }
            LedState::ArmedWindow => {
                // 🔵 azul parpadeante: ventana WiFi abierta, se puede desarmar.
                if phase { self.set(0, 0, 70) } else { self.off() }
            }
            LedState::Recording => self.set(60, 0, 55), // 🟣 magenta fijo
            LedState::Sleeping => self.off(),           // ⚫ apagado
            LedState::Error => {
                // 🔴 parpadeo rápido de aviso (el llamante debe alternar phase rápido).
                if phase { self.set(80, 0, 0) } else { self.off() }
            }
        }
    }
}
