//! Status RGB LED (onboard WS2812 on GPIO48 of the Freenove ESP32-S3-WROOM board).
//!
//! Driven with the RMT peripheral (no extra crates). One addressable LED, GRB order.
//! Used to signal boot / WiFi / camera state so you can tell what the board is doing
//! without a serial cable.

use core::time::Duration;
use anyhow::Result;
use esp_idf_hal::gpio::OutputPin;
use esp_idf_hal::rmt::{
    config::TransmitConfig, FixedLengthSignal, PinState, Pulse, RmtChannel, TxRmtDriver,
};

pub struct StatusLed<'d> {
    tx: TxRmtDriver<'d>,
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

    /// Send a single 24-bit color (WS2812 expects GRB order).
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

    pub fn off(&mut self)        -> Result<()> { self.set(0, 0, 0) }
    pub fn blue(&mut self)       -> Result<()> { self.set(0, 0, 40) }   // arrancando
    pub fn purple(&mut self)     -> Result<()> { self.set(30, 0, 30) }  // WiFi arrancando
    pub fn cyan(&mut self)       -> Result<()> { self.set(0, 30, 30) }  // red lista
    pub fn green(&mut self)      -> Result<()> { self.set(0, 40, 0) }   // camara OK / idle
    pub fn green_dim(&mut self)  -> Result<()> { self.set(0, 5, 0) }    // latido idle
    pub fn red(&mut self)        -> Result<()> { self.set(45, 0, 0) }   // fallo camara
    pub fn white(&mut self)      -> Result<()> { self.set(50, 50, 50) } // captura de foto
}
