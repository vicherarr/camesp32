use std::collections::VecDeque;
use std::sync::Mutex;
use ::log::{Log, Metadata, Record, LevelFilter};

/// How many recent log lines to keep in RAM.
const MAX_LINES: usize = 400;

/// Ring buffer of recent log lines. `Mutex::new` / `VecDeque::new` are const,
/// so this needs no lazy initialization.
static RING: Mutex<VecDeque<String>> = Mutex::new(VecDeque::new());

/// Logger that echoes to the serial console (stdout -> UART / USB-Serial-JTAG)
/// AND keeps the last `MAX_LINES` in a RAM ring buffer.
///
/// The ring buffer is the important part: once the camera takes over the USB
/// pins in USB-OTG host mode, the USB-Serial-JTAG console dies, so these lines
/// are read back over WiFi via the `/logs` HTTP endpoint instead.
struct RingLogger;

impl Log for RingLogger {
    fn enabled(&self, _metadata: &Metadata) -> bool {
        true
    }

    fn log(&self, record: &Record) {
        let ts = unsafe { esp_idf_svc::sys::esp_log_timestamp() };
        let line = format!(
            "[{:>8} ms] {:<5} {}: {}",
            ts,
            record.level(),
            record.target(),
            record.args()
        );

        // Echo to serial (visible while USB-Serial-JTAG is still connected).
        println!("{}", line);

        // Keep the tail in RAM for readback over HTTP.
        if let Ok(mut ring) = RING.lock() {
            if ring.len() >= MAX_LINES {
                ring.pop_front();
            }
            ring.push_back(line);
        }
    }

    fn flush(&self) {}
}

static LOGGER: RingLogger = RingLogger;

/// Install the ring-buffer logger as the global `log` sink. Call once at boot
/// instead of `EspLogger::initialize_default()`.
pub fn init() {
    if log::set_logger(&LOGGER).is_ok() {
        log::set_max_level(LevelFilter::Info);
    }
}

/// Return the buffered log lines (oldest first) as a single string, for `/logs`.
pub fn dump() -> String {
    match RING.lock() {
        Ok(ring) => {
            let mut out = String::with_capacity(ring.len() * 80);
            for line in ring.iter() {
                out.push_str(line);
                out.push('\n');
            }
            out
        }
        Err(_) => String::from("<log buffer poisoned>\n"),
    }
}
