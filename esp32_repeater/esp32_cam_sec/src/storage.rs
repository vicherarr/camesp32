use esp_idf_sys::{
    sdmmc_host_t, sdmmc_slot_config_t, esp_vfs_fat_sdmmc_mount_config_t, sdmmc_card_t,
    esp_vfs_fat_sdmmc_mount, sdmmc_host_init, sdmmc_host_set_bus_width, sdmmc_host_get_slot_width,
    sdmmc_host_set_bus_ddr_mode, sdmmc_host_set_card_clk, sdmmc_host_do_transaction,
    sdmmc_host_io_int_enable, sdmmc_host_io_int_wait,
    sdmmc_host_set_cclk_always_on, sdmmc_host_get_real_freq, sdmmc_host_set_input_delay,
    ESP_OK, SDMMC_HOST_SLOT_1, SDMMC_FREQ_DEFAULT
};
use ::log::{info, error};
use std::ffi::{CString, c_void};
use std::fs::File;
use std::io::Write;
use anyhow::Result;

pub struct Storage {
    _mounted: bool,
}

impl Storage {
    pub fn new() -> Result<Self> {
        unsafe {
            let mut host: sdmmc_host_t = std::mem::zeroed();
            host.flags = 1;
            host.slot = SDMMC_HOST_SLOT_1 as i32;
            host.max_freq_khz = SDMMC_FREQ_DEFAULT as i32;
            host.io_voltage = 3.3;
            host.init = Some(sdmmc_host_init);
            host.set_bus_width = Some(sdmmc_host_set_bus_width);
            host.get_bus_width = Some(sdmmc_host_get_slot_width);
            host.set_bus_ddr_mode = Some(sdmmc_host_set_bus_ddr_mode);
            host.set_card_clk = Some(sdmmc_host_set_card_clk);
            // set_cclk_always_on is called by the driver during clock setup; leaving
            // it null caused an InstrFetchProhibited crash instead of a clean error.
            host.set_cclk_always_on = Some(sdmmc_host_set_cclk_always_on);
            host.do_transaction = Some(sdmmc_host_do_transaction);
            host.io_int_enable = Some(sdmmc_host_io_int_enable);
            host.io_int_wait = Some(sdmmc_host_io_int_wait);
            host.get_real_freq = Some(sdmmc_host_get_real_freq);
            host.set_input_delay = Some(sdmmc_host_set_input_delay);

            let mut slot_config: sdmmc_slot_config_t = std::mem::zeroed();
            slot_config.clk = 39;
            slot_config.cmd = 38;
            slot_config.d0 = 40;
            slot_config.d1 = -1;
            slot_config.d2 = -1;
            slot_config.d3 = -1;
            slot_config.d4 = -1;
            slot_config.d5 = -1;
            slot_config.d6 = -1;
            slot_config.d7 = -1;
            slot_config.__bindgen_anon_1.cd = -1;
            slot_config.__bindgen_anon_2.wp = -1;
            slot_config.width = 1;

            let mount_config = esp_vfs_fat_sdmmc_mount_config_t {
                format_if_mount_failed: true,
                max_files: 5,
                allocation_unit_size: 16 * 1024,
                disk_status_check_enable: false,
            };

            let base_path = CString::new("/sdcard").unwrap();
            let mut card: *mut sdmmc_card_t = std::ptr::null_mut();

            let ret = esp_vfs_fat_sdmmc_mount(
                base_path.as_ptr(),
                &host,
                &slot_config as *const _ as *const c_void,
                &mount_config,
                &mut card,
            );

            if ret != ESP_OK {
                error!("Failed to mount SD card");
                return Err(anyhow::anyhow!("SD Mount failed"));
            }
            info!("SD card mounted successfully");
        }

        Ok(Self { _mounted: true })
    }

    pub fn save_photo(&self, filename: &str, data: &[u8]) -> Result<()> {
        let path = format!("/sdcard/{}", filename);
        let mut file = File::create(&path)?;
        file.write_all(data)?;
        info!("Saved photo to {}", path);
        Ok(())
    }
}
