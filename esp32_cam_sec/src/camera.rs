use esp_idf_sys::{esp_err_t, ESP_OK};
use std::ptr;

pub const PIXFORMAT_JPEG: u32 = 4;
pub const FRAMESIZE_VGA: u32 = 8;
pub const CAMERA_FB_IN_PSRAM: u32 = 0;
pub const CAMERA_GRAB_WHEN_EMPTY: u32 = 0;

#[repr(C)]
#[derive(Debug)]
pub struct camera_config_t {
    pub pin_pwdn: i32,
    pub pin_reset: i32,
    pub pin_xclk: i32,
    pub pin_sccb_sda: i32,
    pub pin_sccb_scl: i32,
    pub pin_d7: i32,
    pub pin_d6: i32,
    pub pin_d5: i32,
    pub pin_d4: i32,
    pub pin_d3: i32,
    pub pin_d2: i32,
    pub pin_d1: i32,
    pub pin_d0: i32,
    pub pin_vsync: i32,
    pub pin_href: i32,
    pub pin_pclk: i32,
    pub xclk_freq_hz: i32,
    pub ledc_timer: u32,
    pub ledc_channel: u32,
    pub pixel_format: u32,
    pub frame_size: u32,
    pub jpeg_quality: i32,
    pub fb_count: usize,
    pub fb_location: u32,
    pub grab_mode: u32,
}

#[repr(C)]
pub struct camera_fb_c_t {
    pub buf: *mut u8,
    pub len: usize,
    pub width: usize,
    pub height: usize,
    pub format: u32,
    pub timestamp: [u64; 2],
}

extern "C" {
    fn esp_camera_init(config: *const camera_config_t) -> esp_err_t;
    fn esp_camera_fb_get() -> *mut camera_fb_c_t;
    fn esp_camera_fb_return(fb: *mut camera_fb_c_t);
}

pub struct Camera {
    _initialized: bool,
}

impl Camera {
    pub fn new() -> Result<Self, &'static str> {
        // ESP32-S3-CAM pinout configuration
        let config = camera_config_t {
            pin_pwdn: -1,
            pin_reset: -1,
            pin_xclk: 15,
            pin_sccb_sda: 4,
            pin_sccb_scl: 5,
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
            ledc_timer: 0,
            ledc_channel: 0,
            pixel_format: PIXFORMAT_JPEG,
            frame_size: FRAMESIZE_VGA,
            jpeg_quality: 12,
            fb_count: 2,
            fb_location: CAMERA_FB_IN_PSRAM,
            grab_mode: CAMERA_GRAB_WHEN_EMPTY,
        };

        let err = unsafe { esp_camera_init(&config) };
        if err == ESP_OK {
            Ok(Camera { _initialized: true })
        } else {
            Err("Failed to initialize camera")
        }
    }

    pub fn take_picture(&self) -> Option<Vec<u8>> {
        let fb = unsafe { esp_camera_fb_get() };
        if fb.is_null() {
            return None;
        }

        let len = unsafe { (*fb).len };
        let buf_ptr = unsafe { (*fb).buf };
        let mut buf = Vec::with_capacity(len);
        unsafe {
            ptr::copy_nonoverlapping(buf_ptr, buf.as_mut_ptr(), len);
            buf.set_len(len);
            esp_camera_fb_return(fb);
        }

        Some(buf)
    }
}
