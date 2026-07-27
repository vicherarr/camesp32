use std::path::{Path, PathBuf};

fn collect_includes(dir: &Path, includes: &mut Vec<PathBuf>) {
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                if let Some(name) = path.file_name() {
                    let name_str = name.to_string_lossy();
                    if name_str == "esp32" || name_str == "esp32s2" || name_str == "esp32c2" || name_str == "esp32c3" || name_str == "esp32c6" || name_str == "esp32h2" || name_str == "esp32p4" || name_str == "riscv" {
                        continue;
                    }
                    if name_str == "include" || name_str == "private_include" || name_str == "config" || name_str == "freertos" {
                        includes.push(path.clone());
                    }
                }
                collect_includes(&path, includes);
            }
        }
    }
}

fn main() {
    embuild::espidf::sysenv::output();

    let idf_path = PathBuf::from("/home/victor/develop/iot/camesp32/esp32_cam_sec/.embuild/espressif/esp-idf/v5.2.3/components");
    let root = PathBuf::from("components/esp32-camera");

    let mut build = cc::Build::new();

    build.compiler("xtensa-esp32s3-elf-gcc");
    build.flag("-w");
    build.flag("-ffunction-sections");
    build.flag("-fdata-sections");
    build.flag("-mlongcalls");

    // Defines required by ESP-IDF headers & camera driver
    build.define("ESP_PLATFORM", "1");
    build.define("_GNU_SOURCE", "1");
    build.define("IDF_TARGET_ESP32S3", "1");
    build.define("_RETARGETABLE_LOCKING", "1");
    build.define("__ESP_FILE_USE_STATIC_LOCK", "1");
    build.define("_LOCK_RECURSIVE_T", "void *");
    build.define("_LOCK_T", "void *");
    build.define("CONFIG_CAMERA_JPEG_MODE_FRAME_SIZE", "8192");
    build.define("CONFIG_CAMERA_DMA_BUFFER_SIZE_MAX", "32768");
    build.define("CONFIG_SCCB_CLK_FREQ", "100000");
    build.define("CONFIG_SCCB_HARDWARE_I2C_PORT1", "1");

    // Include camera driver & conversions headers
    build.include(root.join("driver/include"));
    build.include(root.join("driver/private_include"));
    build.include(root.join("conversions/include"));
    build.include(root.join("conversions/private_include"));
    build.include(root.join("sensors/private_include"));
    build.include(root.join("target/private_include"));
    build.include(root.join("target/esp32s3/private_include"));

    // Include Xtensa & FreeRTOS headers
    build.include(idf_path.join("xtensa/include"));
    build.include(idf_path.join("xtensa/esp32s3/include"));
    build.include(idf_path.join("freertos/config/include/freertos"));
    build.include(idf_path.join("freertos/config/include"));

    // Find generated sdkconfig.h in target directory
    let target_build = PathBuf::from("target/xtensa-esp32s3-espidf/debug/build");
    if let Ok(entries) = std::fs::read_dir(&target_build) {
        for entry in entries.flatten() {
            let config_dir = entry.path().join("out/build/config");
            if config_dir.join("sdkconfig.h").exists() {
                build.include(&config_dir);
            }
        }
    }

    // Collect all ESP-IDF component include directories for ESP32-S3
    let mut idf_includes = Vec::new();
    collect_includes(&idf_path, &mut idf_includes);
    for inc in idf_includes {
        build.include(inc);
    }

    // Core camera driver C source files
    build.file(root.join("driver/esp_camera.c"));
    build.file(root.join("driver/cam_hal.c"));
    build.file(root.join("driver/sensor.c"));
    build.file(root.join("driver/sccb.c"));
    build.file(root.join("driver/esp_camera_af.c"));
    build.file(root.join("target/esp32s3/ll_cam.c"));
    build.file(root.join("sensors/ov2640.c"));
    build.file(root.join("sensors/ov3660.c"));
    build.file(root.join("sensors/ov5640.c"));

    build.compile("esp32_camera_c");
}
