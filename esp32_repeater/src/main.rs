use esp_idf_sys::{self as sys, ip_napt_enable};
use esp_idf_svc::wifi::{EspWifi, ClientConfiguration, AccessPointConfiguration, Configuration, AuthMethod};
use esp_idf_svc::nvs::EspDefaultNvsPartition;
use esp_idf_svc::eventloop::EspSystemEventLoop;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::gpio::PinDriver;
use log::{info, error, warn};
use std::thread;
use std::time::Duration;
use std::sync::Arc;
use std::sync::atomic::{AtomicU8, Ordering};
use esp_idf_sys::{esp_netif_get_handle_from_ifkey, esp_netif_get_ip_info, esp_netif_ip_info_t};

// LED States
const STATE_CONNECTING: u8 = 0; // Parpadeo medio (500ms)
const STATE_CONNECTED: u8 = 1;  // Luz fija ON
const STATE_ERROR: u8 = 2;      // Parpadeo muy rápido (100ms)

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

    // Configure Built-in LED on GPIO2
    let mut led = PinDriver::output(peripherals.pins.gpio2)?;
    let led_state = Arc::new(AtomicU8::new(STATE_CONNECTING));
    let led_state_clone = led_state.clone();

    // LED controller thread
    thread::spawn(move || {
        let mut on = false;
        loop {
            match led_state_clone.load(Ordering::Relaxed) {
                STATE_CONNECTING => {
                    let _ = led.set_level(on.into());
                    on = !on;
                    thread::sleep(Duration::from_millis(500));
                },
                STATE_CONNECTED => {
                    let _ = led.set_low(); // Active-LOW: low means ON
                    thread::sleep(Duration::from_millis(500)); // Sleep to not hog CPU, state won't change often
                },
                STATE_ERROR => {
                    let _ = led.set_level(on.into());
                    on = !on;
                    thread::sleep(Duration::from_millis(100));
                },
                _ => {
                    let _ = led.set_high(); // Active-LOW: high means OFF
                    thread::sleep(Duration::from_millis(500));
                }
            }
        }
    });

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

    info!("Configuring AP/STA Modes...");
    wifi.set_configuration(&Configuration::Mixed(
        ClientConfiguration {
            ssid: sta_ssid.try_into().unwrap(),
            password: sta_pass.try_into().unwrap(),
            auth_method: AuthMethod::WPA2Personal,
            ..Default::default()
        },
        AccessPointConfiguration {
            ssid: ap_ssid.try_into().unwrap(),
            password: ap_pass.try_into().unwrap(),
            auth_method: AuthMethod::WPA2Personal,
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
        error!("Could not connect to target network. Indicating error and rebooting.");
        led_state.store(STATE_ERROR, Ordering::Relaxed);
        thread::sleep(Duration::from_secs(5)); // Show error for 5 seconds
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
            led_state.store(STATE_ERROR, Ordering::Relaxed);
        }
    }

    info!("Repeater is now running. Connect to '{}'", ap_ssid);
    led_state.store(STATE_CONNECTED, Ordering::Relaxed);

    // Keep main thread alive and monitor connection
    loop {
        thread::sleep(Duration::from_secs(15));
        
        let is_up = wifi.is_connected().unwrap_or(false);
        if !is_up {
            warn!("Lost connection to upstream AP. Attempting to reconnect...");
            led_state.store(STATE_CONNECTING, Ordering::Relaxed);
            let _ = wifi.connect();
        } else {
            led_state.store(STATE_CONNECTED, Ordering::Relaxed);
        }
    }
}
