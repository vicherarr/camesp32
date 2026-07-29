use esp_idf_svc::http::server::{Configuration, EspHttpServer};
use serde::Deserialize;
use esp_idf_svc::http::Method;
use esp_idf_svc::io::Write;
use std::fs;
use std::io::Read;
use anyhow::Result;
use ::log::info;
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use esp_idf_svc::nvs::EspDefaultNvsPartition;
use crate::config::{AppConfig, load_config, save_config};

pub struct WebServer {
    _server: EspHttpServer<'static>,
}

impl WebServer {
    pub fn new(motion_state: Arc<AtomicBool>, partition: EspDefaultNvsPartition, current_mode: String, camera_ready: Arc<AtomicBool>) -> Result<Self> {
        let mut server = EspHttpServer::new(&Configuration::default())?;

        server.fn_handler("/", Method::Get, |request| {
            let mut response = request.into_ok_response()?;
            let html = "<html><body><h1>ESP32-CAM Seguridad</h1><a href='/photo'>Foto en vivo</a><br/><a href='/photos'>Ver Fotos (SD)</a><br/><a href='/sensor'>Estado del Sensor</a><br/><a href='/logs'>Logs</a></body></html>";
            response.write_all(html.as_bytes())?;
            Ok::<(), anyhow::Error>(())
        })?;

        // Live capture: grabs one JPEG straight from the camera driver on demand.
        // Lets you verify the onboard camera over WiFi without the motion sensor.
        let cam_ready = camera_ready.clone();
        server.fn_handler("/photo", Method::Get, move |request| {
            if !cam_ready.load(Ordering::Relaxed) {
                let mut r = request.into_status_response(503)?;
                r.write_all(b"camera not ready")?;
                return Ok::<(), anyhow::Error>(());
            }
            let data: Option<Vec<u8>> = unsafe {
                let fb = esp_idf_sys::camera_sys::esp_camera_fb_get();
                if fb.is_null() {
                    None
                } else {
                    let fbr = &*fb;
                    let v = if fbr.format == esp_idf_sys::camera_sys::pixformat_t_PIXFORMAT_JPEG {
                        // Sensor gives JPEG directly (OV2640/OV3660).
                        if !fbr.buf.is_null() && fbr.len > 0 {
                            Some(std::slice::from_raw_parts(fbr.buf, fbr.len).to_vec())
                        } else {
                            None
                        }
                    } else {
                        // Non-JPEG sensor (e.g. GC0308 in RGB565): encode to JPEG in software.
                        let mut out: *mut u8 = std::ptr::null_mut();
                        let mut out_len: usize = 0;
                        if esp_idf_sys::camera_sys::frame2jpg(fb, 80, &mut out, &mut out_len)
                            && !out.is_null()
                            && out_len > 0
                        {
                            let jpeg = std::slice::from_raw_parts(out, out_len).to_vec();
                            esp_idf_sys::camera_sys::free(out as *mut core::ffi::c_void);
                            Some(jpeg)
                        } else {
                            None
                        }
                    };
                    esp_idf_sys::camera_sys::esp_camera_fb_return(fb);
                    v
                }
            };
            match data {
                Some(jpeg) => {
                    let mut response = request.into_response(200, Some("OK"), &[("Content-Type", "image/jpeg")])?;
                    response.write_all(&jpeg)?;
                }
                None => {
                    let mut response = request.into_status_response(500)?;
                    response.write_all(b"capture failed")?;
                }
            }
            Ok::<(), anyhow::Error>(())
        })?;

        server.fn_handler("/photos", Method::Get, |request| {
            let mut response = request.into_ok_response()?;
            if let Ok(mut entries) = fs::read_dir("/sdcard") {
                response.write_all(b"<html><body><h2>Lista de Fotos</h2><ul>")?;
                
                let mut count = 0;
                while let Some(Ok(entry)) = entries.next() {
                    let name = entry.file_name().to_string_lossy().to_string();
                    let item = format!("<li><a href='/file/{}'>{}</a></li>", name, name);
                    if let Err(e) = response.write_all(item.as_bytes()) {
                        ::log::error!("Network write error: {}", e);
                        break;
                    }
                    count += 1;
                    if count >= 100 {
                        // Limit to 100 files to avoid massive HTML pages
                        break;
                    }
                }
                response.write_all(b"</ul></body></html>")?;
            } else {
                response.write_all(b"Error leyendo SD card")?;
            }
            Ok::<(), anyhow::Error>(())
        })?;

        server.fn_handler("/file/*", Method::Get, |request| {
            let uri = request.uri();
            let file_name = uri.trim_start_matches("/file/");
            let path = format!("/sdcard/{}", file_name);
            
            if let Ok(mut file) = fs::File::open(&path) {
                let mut response = request.into_response(
                    200, 
                    Some("OK"), 
                    &[("Content-Type", "image/jpeg")]
                )?;
                let mut buf = [0u8; 4096];
                loop {
                    match file.read(&mut buf) {
                        Ok(0) => break,
                        Ok(n) => {
                            if let Err(e) = response.write_all(&buf[..n]) {
                                ::log::error!("Network write error: {}", e);
                                break;
                            }
                        },
                        Err(e) => {
                            ::log::error!("File read error: {}", e);
                            break;
                        }
                    }
                }
            } else {
                let mut response = request.into_status_response(404)?;
                response.write_all(b"Not found")?;
            }
            Ok::<(), anyhow::Error>(())
        })?;

        let mode_clone = current_mode.clone();
        let motion_state_info = motion_state.clone();
        server.fn_handler("/info", Method::Get, move |request| {
            let is_detecting = motion_state_info.load(Ordering::Relaxed);
            let mut response = request.into_response(200, Some("OK"), &[("Content-Type", "application/json")])?;
            let json = format!("{{\"device\": \"ESP32-CAM\", \"mode\": \"{}\", \"motion\": {}}}", mode_clone, is_detecting);
            response.write_all(json.as_bytes())?;
            Ok::<(), anyhow::Error>(())
        })?;
        
        // Debug log readback: returns the RAM ring buffer as plain text. This is
        // our only window once USB-OTG host mode kills the serial console.
        server.fn_handler("/logs", Method::Get, |request| {
            let logs = crate::logbuf::dump();
            let mut response = request.into_response(
                200,
                Some("OK"),
                &[("Content-Type", "text/plain; charset=utf-8")],
            )?;
            response.write_all(logs.as_bytes())?;
            Ok::<(), anyhow::Error>(())
        })?;

        // New endpoint to report sensor state
        let motion_state_clone = motion_state.clone();
        server.fn_handler("/sensor", Method::Get, move |request| {
            let is_detecting = motion_state_clone.load(Ordering::Relaxed);
            let mut response = request.into_response(
                200,
                Some("OK"),
                &[("Content-Type", "application/json")]
            )?;
            let json = format!("{{\"motion\": {}}}", is_detecting);
            response.write_all(json.as_bytes())?;
            Ok::<(), anyhow::Error>(())
        })?;
        
        #[derive(Deserialize)]
        struct ConfigReq {
            mode: String,
        }

        // Config endpoint
        server.fn_handler("/config", Method::Post, move |mut request| {
            let mut buf = [0u8; 256];
            let bytes_read = request.read(&mut buf)?;
            if bytes_read > 0 {
                let json_str = std::str::from_utf8(&buf[..bytes_read]).unwrap_or("");
                if let Ok(req) = serde_json::from_str::<ConfigReq>(json_str) {
                    let new_config = AppConfig { mode: req.mode };
                    let (_, mut nvs) = load_config(partition.clone());
                    if let Ok(_) = save_config(&mut nvs, &new_config) {
                        let mut response = request.into_response(200, Some("OK"), &[])?;
                        response.write_all(b"{\"status\":\"ok\"}")?;
                        
                        // Schedule restart to apply changes
                        std::thread::spawn(|| {
                            ::log::info!("Configuration saved. Rebooting in 1.5 seconds...");
                            std::thread::sleep(std::time::Duration::from_millis(1500));
                            unsafe { esp_idf_svc::sys::esp_restart() };
                        });
                        
                    } else {
                        let mut response = request.into_status_response(500)?;
                        response.write_all(b"Internal Error")?;
                    }
                } else {
                    let mut response = request.into_status_response(400)?;
                    response.write_all(b"Invalid JSON")?;
                }
            }
            Ok::<(), anyhow::Error>(())
        })?;

        info!("Web Server started");
        Ok(Self { _server: server })
    }
}
