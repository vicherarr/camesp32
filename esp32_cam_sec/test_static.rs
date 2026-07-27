use esp_idf_svc::wifi::*;
use esp_idf_svc::ipv4::*;
use std::net::Ipv4Addr;

fn test(wifi: &mut EspWifi) {
    let _ = wifi.sta_netif_mut().set_ip_info(&ipv4::ClientSettings {
        ip: Ipv4Addr::new(192, 168, 71, 220),
        subnet: ipv4::Subnet {
            gateway: Ipv4Addr::new(192, 168, 71, 1),
            mask: ipv4::Mask(24),
        },
        dns: None,
        secondary_dns: None,
    });
}
