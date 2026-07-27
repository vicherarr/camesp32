use esp_idf_svc::wifi::{EspWifi, AuthMethod, AccessPointConfiguration, ClientConfiguration, Configuration};
use esp_idf_svc::hal::modem::Modem;
use ::log::info;
use anyhow::Result;
use esp_idf_svc::nvs::EspDefaultNvsPartition;

#[derive(Clone, Debug)]
pub enum WifiMode {
    AP,
    STA,
}

pub struct WifiManager<'a> {
    _wifi: EspWifi<'a>,
}

impl<'a> WifiManager<'a> {
    pub fn new(modem: Modem<'a>, mode: WifiMode, nvs: EspDefaultNvsPartition) -> Result<Self> {
        let sysloop = esp_idf_svc::eventloop::EspSystemEventLoop::take()?;

        let mut wifi = EspWifi::new(modem, sysloop, Some(nvs))?;

        match mode {
            WifiMode::AP => {
                let mut ap_config = AccessPointConfiguration::default();
                ap_config.ssid = "ESP32-CAM-Seguridad".try_into().unwrap();
                ap_config.password = "admin1234".try_into().unwrap();
                ap_config.auth_method = AuthMethod::WPA2Personal;
                ap_config.channel = 1;
                
                wifi.set_configuration(&Configuration::AccessPoint(ap_config))?;
                info!("WiFi AP configured. SSID: ESP32-CAM-Seguridad");
            }
            WifiMode::STA => {
                let mut client_config = ClientConfiguration::default();
                client_config.ssid = "DIGIFIBRA-42H6_EXT".try_into().unwrap();
                client_config.password = "Uyy4ZEPhXP".try_into().unwrap();
                client_config.auth_method = AuthMethod::WPA2Personal;
                
                wifi.set_configuration(&Configuration::Client(client_config))?;
                info!("WiFi STA configured to connect to SSID: DIGIFIBRA-42H6_EXT");
            }
        }
        
        wifi.start()?;
        
        // Connect if in STA mode
        if let Ok(Configuration::Client(_)) = wifi.get_configuration() {
            wifi.connect()?;
            info!("WiFi STA connected.");
        }
        
        Ok(Self { _wifi: wifi })
    }
}
