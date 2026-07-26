use esp_idf_svc::wifi::{EspWifi, AuthMethod, AccessPointConfiguration, Configuration};
use esp_idf_svc::hal::modem::Modem;
use ::log::info;
use anyhow::Result;

pub struct WifiManager<'a> {
    _wifi: EspWifi<'a>,
}

impl<'a> WifiManager<'a> {
    pub fn new(modem: Modem<'a>) -> Result<Self> {
        let sysloop = esp_idf_svc::eventloop::EspSystemEventLoop::take()?;
        let nvs = esp_idf_svc::nvs::EspDefaultNvsPartition::take()?;

        let mut wifi = EspWifi::new(modem, sysloop, Some(nvs))?;

        let mut ap_config = AccessPointConfiguration::default();
        ap_config.ssid = "ESP32-CAM-Seguridad".try_into().unwrap();
        ap_config.password = "admin1234".try_into().unwrap();
        ap_config.auth_method = AuthMethod::WPA2Personal;
        ap_config.channel = 1;

        wifi.set_configuration(&Configuration::AccessPoint(ap_config))?;
        wifi.start()?;
        info!("WiFi AP started. SSID: ESP32-CAM-Seguridad");

        Ok(Self { _wifi: wifi })
    }
}
