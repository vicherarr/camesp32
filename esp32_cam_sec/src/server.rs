use esp_idf_svc::http::server::{Configuration, EspHttpServer};
use esp_idf_svc::http::Method;
use esp_idf_svc::io::Write;
use std::fs;
use std::io::Read;
use anyhow::Result;
use ::log::info;

pub struct WebServer {
    _server: EspHttpServer<'static>,
}

impl WebServer {
    pub fn new() -> Result<Self> {
        let mut server = EspHttpServer::new(&Configuration::default())?;

        server.fn_handler("/", Method::Get, |request| {
            let mut response = request.into_ok_response()?;
            let html = "<html><body><h1>ESP32-CAM Seguridad</h1><a href='/photos'>Ver Fotos</a></body></html>";
            response.write_all(html.as_bytes())?;
            Ok::<(), anyhow::Error>(())
        })?;

        server.fn_handler("/photos", Method::Get, |request| {
            let mut response = request.into_ok_response()?;
            if let Ok(mut entries) = fs::read_dir("/sdcard") {
                let mut files: Vec<String> = vec![];
                while let Some(Ok(entry)) = entries.next() {
                    files.push(entry.file_name().to_string_lossy().to_string());
                }
                files.sort_by(|a, b| b.cmp(a));

                let mut html = String::from("<html><body><h2>Lista de Fotos</h2><ul>");
                for name in files {
                    html.push_str(&format!("<li><a href='/file/{}'>{}</a></li>", name, name));
                }
                html.push_str("</ul></body></html>");
                response.write_all(html.as_bytes())?;
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
                let mut buf = Vec::new();
                file.read_to_end(&mut buf)?;
                let mut response = request.into_response(
                    200, 
                    Some("OK"), 
                    &[("Content-Type", "image/jpeg")]
                )?;
                response.write_all(&buf)?;
            } else {
                let mut response = request.into_status_response(404)?;
                response.write_all(b"Not found")?;
            }
            Ok::<(), anyhow::Error>(())
        })?;
        
        info!("Web Server started");
        Ok(Self { _server: server })
    }
}
