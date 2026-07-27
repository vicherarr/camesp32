use esp_idf_sys::{self as sys, ip_napt_enable};
use esp_idf_svc::wifi::{EspWifi, ClientConfiguration, AccessPointConfiguration, Configuration, AuthMethod};
use esp_idf_svc::nvs::EspDefaultNvsPartition;
use esp_idf_svc::eventloop::EspSystemEventLoop;
use esp_idf_hal::peripherals::Peripherals;
use log::{info, error, warn};
use std::thread;
use std::time::Duration;
use esp_idf_sys::{esp_netif_get_handle_from_ifkey, esp_netif_get_ip_info, esp_netif_ip_info_t};

fn main() -> anyhow::Result<()> {
    // Patches for ESP-IDF
    sys::link_patches();
    esp_idf_svc::log::EspLogger::initialize_default();

    info!("--------------------------------------------------");
    info!("Starting ESP32 WiFi Repeater (NAT) Extender");
    info!("Optimized for Stability and Maximum Range");
    info!("--------------------------------------------------");

    let peripherals = Peripherals::take()?;
    let sys_loop = EspSystemEventLoop::take()?;
    let nvs = EspDefaultNvsPartition::take()?;

    let mut wifi = EspWifi::new(
        peripherals.modem,
        sys_loop.clone(),
        Some(nvs)
    )?;

    // Extender Configuration
    // Target Network (Client STA)
    let sta_ssid = "DIGIFIBRA-42H6";
    let sta_pass = "Uyy4ZEPhXP";

    // Repeated Network (AP)
    let ap_ssid = "DIGIFIBRA-42H6_EXT";
    let ap_pass = "Uyy4ZEPhXP";
    let ap_channel = 6; // Fixed channel for stability

    info!("Configuring AP/STA Modes...");
    wifi.set_configuration(&Configuration::Mixed(
        ClientConfiguration {
            ssid: sta_ssid.try_into().unwrap(),
            password: sta_pass.try_into().unwrap(),
            ..Default::default()
        },
        AccessPointConfiguration {
            ssid: ap_ssid.try_into().unwrap(),
            password: ap_pass.try_into().unwrap(),
            auth_method: AuthMethod::WPA2Personal,
            channel: ap_channel,
            max_connections: 4, // Maximize memory stability
            ..Default::default()
        }
    ))?;

    info!("Starting WiFi...");
    wifi.start()?;
    
    // Connect to target network
    info!("Connecting to target network: {}...", sta_ssid);
    wifi.connect()?;

    let mut connected = false;
    for i in 0..60 {
        if wifi.is_connected().unwrap_or(false) {
            info!("Successfully connected to {}!", sta_ssid);
            connected = true;
            break;
        }
        thread::sleep(Duration::from_millis(500));
        if i % 10 == 0 {
            info!("Waiting for connection... {}s", i / 2);
        }
    }

    if !connected {
        error!("Could not connect to target network. Rebooting.");
        unsafe { sys::esp_restart(); }
    }
    
    info!("Waiting for DHCP IP assignment on STA interface...");
    // Wait until STA actually gets an IP address (important for NAT)
    thread::sleep(Duration::from_secs(3));

    // Enable NAPT on the AP interface
    unsafe {
        info!("Retrieving AP interface handle...");
        // In ESP-IDF, the default AP interface key is "WIFI_AP_DEF"
        let ap_netif = esp_netif_get_handle_from_ifkey(b"WIFI_AP_DEF\0".as_ptr() as *const _);
        
        if !ap_netif.is_null() {
            let mut ap_info: esp_netif_ip_info_t = std::mem::zeroed();
            esp_netif_get_ip_info(ap_netif, &mut ap_info);
            
            let ap_ip = ap_info.ip.addr;
            info!("AP IP Address Hex: {:X}", ap_ip);
            
            // Enable NAPT
            info!("Enabling NAPT (Network Address and Port Translation)...");
            ip_napt_enable(ap_ip, 1);
            info!("NAPT Enabled Successfully!");
        } else {
            error!("CRITICAL: Could not find AP netif! NAT will not work.");
        }
    }

    info!("Repeater is now running. Connect to '{}'", ap_ssid);

    // Keep main thread alive and monitor connection
    loop {
        thread::sleep(Duration::from_secs(15));
        
        let is_up = wifi.is_connected().unwrap_or(false);
        if !is_up {
            warn!("Lost connection to upstream AP. Attempting to reconnect...");
            let _ = wifi.connect();
        } else {
            info!("Repeater health check: OK");
        }
    }
}
