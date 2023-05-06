use crate::types::TimeStamp;
use crate::utils::unix_time::get_unix_time;
use log::{debug, error};
use parking_lot::RwLock;
use std::io::{BufRead, Cursor, Write};
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, BufReader};
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
                    result = stdout_reader.read(&mut buf_out[..]) => {
                        match result {
                            Ok(len) => {
                                stdout_line.push_str(&String::from_utf8_lossy(&buf_out[..len]));
                                if stdout_line.contains("\n") || stdout_line.len() > 1024 {
                                    debug!("stdout: {}", stdout_line);
                                    stdout_line = String::new();
                                }
                                *timestamp_lock.write() = get_unix_time();
                            },
                            _ => break
                        }
                    }
                    result = stderr_reader.read(&mut buf_err[..]) => {
                        match result {
                            Ok(len) => {
                                stderr_line.push_str(&String::from_utf8_lossy(&buf_err[..len]));
                                if stderr_line.contains("\n") || stderr_line.len() > 1024 {
                                    debug!("stderr: {}", stderr_line);
                                    stderr_line = String::new();
                                }
                                *timestamp_lock.write() = get_unix_time();
                            },
                            _ => break
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
