use esp_idf_svc::netif::*;
use esp_idf_svc::ipv4::*;
fn test() {
    let mut config = ClientSettings::default();
    config.ip = std::net::Ipv4Addr::new(192,168,1,143);
}
