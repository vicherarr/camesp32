use std::net::UdpSocket;
use std::thread;
use ::log::{info, error};

pub fn start_discovery_server() {
    thread::spawn(|| {
        let socket = match UdpSocket::bind("0.0.0.0:5555") {
            Ok(s) => s,
            Err(e) => {
                error!("Failed to bind UDP socket for discovery: {}", e);
                return;
            }
        };

        info!("UDP Discovery server started on port 5555");
        let mut buf = [0; 1024];

        loop {
            match socket.recv_from(&mut buf) {
                Ok((size, src)) => {
                    let msg = String::from_utf8_lossy(&buf[..size]);
                    if msg.trim() == "DISCOVER_CAMESP32" {
                        info!("Received discovery request from {}", src);
                        let response = "CAMESP32_HERE";
                        if let Err(e) = socket.send_to(response.as_bytes(), src) {
                            error!("Failed to send discovery response: {}", e);
                        }
                    }
                }
                Err(e) => {
                    error!("UDP receive error: {}", e);
                    // Sleep briefly to prevent tight loop on persistent error
                    thread::sleep(std::time::Duration::from_secs(1));
                }
            }
        }
    });
}
