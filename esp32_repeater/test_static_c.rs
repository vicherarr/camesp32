use esp_idf_sys::*;

fn test() {
    unsafe {
        let sta_netif = esp_netif_get_handle_from_ifkey(b"WIFI_STA_DEF\0".as_ptr() as *const _);
        if !sta_netif.is_null() {
            esp_netif_dhcpc_stop(sta_netif);
            let mut info: esp_netif_ip_info_t = std::mem::zeroed();
            // 192.168.1.220
            info.ip.addr = esp_ip4addr_aton(b"192.168.1.220\0".as_ptr() as *const _);
            info.netmask.addr = esp_ip4addr_aton(b"255.255.255.0\0".as_ptr() as *const _);
            info.gw.addr = esp_ip4addr_aton(b"192.168.1.1\0".as_ptr() as *const _);
            esp_netif_set_ip_info(sta_netif, &info);
        }
    }
}
