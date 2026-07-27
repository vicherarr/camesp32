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
                
                // Configurar IP Fija (192.168.71.220) via C API
                unsafe {
                    let sta_netif = esp_idf_sys::esp_netif_get_handle_from_ifkey(b"WIFI_STA_DEF\0".as_ptr() as *const _);
                    if !sta_netif.is_null() {
                        esp_idf_sys::esp_netif_dhcpc_stop(sta_netif);
                        let mut info: esp_idf_sys::esp_netif_ip_info_t = std::mem::zeroed();
                        info.ip.addr = esp_idf_sys::ipaddr_addr(b"192.168.71.220\0".as_ptr() as *const _);
                        info.netmask.addr = esp_idf_sys::ipaddr_addr(b"255.255.255.0\0".as_ptr() as *const _);
                        info.gw.addr = esp_idf_sys::ipaddr_addr(b"192.168.71.1\0".as_ptr() as *const _);
                        esp_idf_sys::esp_netif_set_ip_info(sta_netif, &info);
                    }
                }

                info!("WiFi STA configured to connect to SSID: DIGIFIBRA-42H6_EXT with static IP 192.168.71.220");
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
