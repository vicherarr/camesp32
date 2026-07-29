use ::log::{info, error, warn};
use esp_idf_sys::camera_sys::*;

/// Valid 1x1 pixel JPEG byte sequence for mock/testing mode
const DUMMY_JPEG: &[u8] = &[
    0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48,
    0x00, 0x48, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43, 0x00, 0x03, 0x02, 0x02, 0x03, 0x02, 0x02, 0x03,
    0x03, 0x03, 0x03, 0x04, 0x03, 0x03, 0x04, 0x05, 0x08, 0x05, 0x05, 0x04, 0x04, 0x05, 0x0A, 0x07,
    0x07, 0x06, 0x08, 0x0C, 0x0A, 0x0C, 0x0C, 0x0B, 0x0A, 0x0B, 0x0B, 0x0D, 0x0E, 0x12, 0x0F, 0x0D,
    0x0E, 0x11, 0x0E, 0x0B, 0x0B, 0x10, 0x16, 0x10, 0x11, 0x13, 0x14, 0x15, 0x15, 0x15, 0x0C, 0x0F,
    0x17, 0x18, 0x16, 0x14, 0x18, 0x12, 0x14, 0x15, 0x14, 0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01,
    0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xFF, 0xC4, 0x00, 0x1F, 0x00, 0x00, 0x01, 0x05, 0x01, 0x01,
    0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04,
    0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F,
    0x00, 0xD2, 0xCF, 0x20, 0xFF, 0xD9,
];

/// Freenove ESP32-S3-WROOM CAM DVP pinout (onboard camera, NOT USB).
fn make_config(
    pixel_format: pixformat_t,
    frame_size: framesize_t,
    fb_location: camera_fb_location_t,
    fb_count: usize,
) -> camera_config_t {
    camera_config_t {
        pin_pwdn: -1,
        pin_reset: -1,
        pin_xclk: 15,
        __bindgen_anon_1: camera_config_t__bindgen_ty_1 { pin_sccb_sda: 4 },
        __bindgen_anon_2: camera_config_t__bindgen_ty_2 { pin_sccb_scl: 5 },
        pin_d7: 16,
        pin_d6: 17,
        pin_d5: 18,
        pin_d4: 12,
        pin_d3: 10,
        pin_d2: 8,
        pin_d1: 9,
        pin_d0: 11,
        pin_vsync: 6,
        pin_href: 7,
        pin_pclk: 13,

        xclk_freq_hz: 20_000_000,
        ledc_timer: ledc_timer_t_LEDC_TIMER_0,
        ledc_channel: ledc_channel_t_LEDC_CHANNEL_0,

        pixel_format,
        frame_size,
        jpeg_quality: 12, // 0-63, lower is better quality
        fb_count,
        fb_location,
        grab_mode: camera_grab_mode_t_CAMERA_GRAB_WHEN_EMPTY,

        sccb_i2c_port: -1,
    }
}

pub struct Camera {
    is_mock: bool,
    /// True when the sensor delivers JPEG directly; false when we capture a raw
    /// format (RGB565) and encode to JPEG in software (e.g. GC0308).
    is_jpeg: bool,
}

impl Camera {
    pub fn new() -> Result<Self, &'static str> {
        info!("Initializing onboard DVP camera (Freenove ESP32-S3)...");

        // Attempt 1: JPEG @ VGA in PSRAM (OV2640 / OV3660 support JPEG natively).
        let mut err = unsafe {
            esp_camera_init(&make_config(
                pixformat_t_PIXFORMAT_JPEG,
                framesize_t_FRAMESIZE_VGA,
                camera_fb_location_t_CAMERA_FB_IN_PSRAM,
                2,
            ))
        };
        let mut is_jpeg = true;

        // Attempt 2: some sensors (GC0308...) don't do JPEG -> retry RGB565 @ QVGA.
        if err != esp_idf_sys::ESP_OK {
            warn!("JPEG camera init failed (0x{:x}); retrying RGB565 QVGA...", err);
            unsafe { esp_camera_deinit() };
            err = unsafe {
                esp_camera_init(&make_config(
                    pixformat_t_PIXFORMAT_RGB565,
                    framesize_t_FRAMESIZE_VGA, // GC0308 max = VGA 640x480
                    camera_fb_location_t_CAMERA_FB_IN_PSRAM,
                    1,
                ))
            };
            is_jpeg = false;
        }

        if err != esp_idf_sys::ESP_OK {
            warn!("Camera init failed in both modes (0x{:x}). Falling back to Mock Camera mode!", err);
            return Ok(Camera { is_mock: true, is_jpeg: false });
        }

        // Log the detected sensor so we can identify it over /logs.
        unsafe {
            let s = esp_camera_sensor_get();
            if !s.is_null() {
                let id = (*s).id;
                info!(
                    "Camera OK. Sensor PID=0x{:02x} VER=0x{:02x} MIDH=0x{:02x} MIDL=0x{:02x} (jpeg_native={})",
                    id.PID, id.VER, id.MIDH, id.MIDL, is_jpeg
                );
            }
        }
        info!("Camera hardware initialized successfully! (jpeg_native={})", is_jpeg);
        Ok(Camera { is_mock: false, is_jpeg })
    }

    /// True when a real DVP camera was initialized (not the mock fallback).
    pub fn is_real(&self) -> bool {
        !self.is_mock
    }

    /// Takes a picture and returns JPEG bytes (encoding in software if needed).
    pub fn take_picture(&self) -> Option<Vec<u8>> {
        if self.is_mock {
            info!("[MOCK CAMERA] Returning fake test photo JPEG");
            return Some(DUMMY_JPEG.to_vec());
        }

        let fb_ptr = unsafe { esp_camera_fb_get() };
        if fb_ptr.is_null() {
            error!("Failed to get camera frame buffer");
            return None;
        }

        let result = if self.is_jpeg {
            let fb = unsafe { &*fb_ptr };
            if !fb.buf.is_null() && fb.len > 0 {
                Some(unsafe { std::slice::from_raw_parts(fb.buf, fb.len) }.to_vec())
            } else {
                None
            }
        } else {
            // Raw frame -> encode to JPEG in software.
            let mut out: *mut u8 = std::ptr::null_mut();
            let mut out_len: usize = 0;
            let ok = unsafe { frame2jpg(fb_ptr, 80, &mut out, &mut out_len) };
            if ok && !out.is_null() && out_len > 0 {
                let v = unsafe { std::slice::from_raw_parts(out, out_len) }.to_vec();
                unsafe { free(out as *mut core::ffi::c_void) };
                Some(v)
            } else {
                error!("frame2jpg (software JPEG) failed");
                None
            }
        };

        unsafe { esp_camera_fb_return(fb_ptr) };
        result.filter(|v| !v.is_empty())
    }
}
