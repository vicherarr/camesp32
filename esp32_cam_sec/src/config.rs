use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};
use serde::{Deserialize, Serialize};
use ::log::{info, error};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct AppConfig {
    pub mode: String, // "AP" or "STA"
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            mode: "STA".to_string(),
        }
    }
}

pub fn load_config(partition: EspDefaultNvsPartition) -> (AppConfig, EspNvs<NvsDefault>) {
    let nvs = match EspNvs::new(partition, "config", true) {
        Ok(n) => n,
        Err(e) => {
            error!("Failed to open NVS: {}", e);
            panic!("NVS error");
        }
    };
    
    let mut buf = [0u8; 512];
    if let Ok(Some(json_str)) = nvs.get_str("app_config", &mut buf) {
        if let Ok(config) = serde_json::from_str(json_str) {
            info!("Loaded config from NVS: {:?}", config);
            return (config, nvs);
        }
    }
    
    info!("Loading default config");
    (AppConfig::default(), nvs)
}

pub fn save_config(nvs: &mut EspNvs<NvsDefault>, config: &AppConfig) -> anyhow::Result<()> {
    let json_str = serde_json::to_string(config)?;
    nvs.set_str("app_config", &json_str)?;
    info!("Saved config to NVS: {}", json_str);
    Ok(())
}
