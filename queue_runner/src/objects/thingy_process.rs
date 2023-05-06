use crate::types::TimeStamp;
use crate::utils::unix_time::get_unix_time;
use log::{debug, error};
use parking_lot::RwLock;
use std::io::{BufRead, Cursor, Read, Write};
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncRead, AsyncReadExt, BufReader};
use tokio::process::Child;
use tokio::spawn;
use tokio::task::spawn_local;

pub struct ThingyProcess {
    pub process: Child,
    pub script_id: String,
    pub timestamp: Arc<RwLock<TimeStamp>>,
}

impl ThingyProcess {
    pub fn new(script_id: String, process: Child) -> Self {
        return Self {
            script_id,
            process,
            timestamp: Arc::new(RwLock::new(get_unix_time())),
        };
    }

    async fn process_std_stream<R: AsyncRead + Unpin + Send + 'static>(
        name: &str,
        reader: &mut BufReader<R>,
        line_buf: &mut String,
        buf: &mut [u8],
        timestamp_lock: &Arc<RwLock<TimeStamp>>,
    ) -> bool {
        let result = reader.read(&mut buf[..]).await;
        return match result {
            Ok(len) => {
                line_buf.push_str(&String::from_utf8_lossy(&buf[..len]));
                if line_buf.contains("\n") || line_buf.len() > 1024 {
                    debug!("{}: {}", name, line_buf);
                    line_buf.clear();
                }
                *timestamp_lock.write() = get_unix_time();
                true
            }
            _ => false,
        };
    }

    pub fn init_read_loop(&mut self) {
        let stdout = self
            .process
            .stdout
            .take()
            .expect("child did not have a handle to stdout");
        let stderr = self
            .process
            .stderr
            .take()
            .expect("child did not have a handle to stderr");
        let timestamp_lock = self.timestamp.clone();

        spawn(async move {
            let mut stdout_reader = BufReader::new(stdout);
            let mut stderr_reader = BufReader::new(stderr);
            let mut buf_out = [0u8; 8];
            let mut buf_err = [0u8; 8];
            let mut stdout_line = String::new();
            let mut stderr_line = String::new();

            loop {
                tokio::select! {
                    result = Self::process_std_stream("stdout", &mut stdout_reader, &mut stdout_line, &mut buf_out, &timestamp_lock) => {
                        if !result {
                            break;
                        }
                    }
                    result = Self::process_std_stream("stderr", &mut stderr_reader, &mut stderr_line, &mut buf_err, &timestamp_lock) => {
                        if !result {
                            break;
                        }
                    }
                }
            }
        });
    }

    pub async fn kill(&mut self) {
        match self.process.kill().await {
            Err(e) => {
                error!("kill error: {} (ignored)", e);
            }
            _ => {}
        };
    }
}
