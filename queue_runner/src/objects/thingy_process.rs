use std::sync::Arc;
use log::{debug, error};
use parking_lot::RwLock;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Child;
use tokio::spawn;
use tokio::task::spawn_local;
use crate::types::TimeStamp;
use crate::utils::unix_time::get_unix_time;

pub struct ThingyProcess {
    pub process: Child,
    pub script_id: String,
    pub timestamp: Arc<RwLock<TimeStamp>>
}

impl ThingyProcess {
    pub fn new(script_id: String, process: Child) -> Self {
        return Self {
            script_id,
            process,
            timestamp: Arc::new(RwLock::new(get_unix_time()))
        };
    }

    pub fn init_read_loop(&mut self) {
        let stdout = self.process.stdout.take().expect("child did not have a handle to stdout");
        let stderr = self.process.stderr.take().expect("child did not have a handle to stderr");
        let timestamp_lock = self.timestamp.clone();

        spawn(async move {
            let mut stdout_reader = BufReader::new(stdout).lines();
            let mut stderr_reader = BufReader::new(stderr).lines();

            loop {
                tokio::select! {
                    result = stdout_reader.next_line() => {
                        match result {
                            Ok(Some(line)) => {
                                debug!("Stdout: {}", line);
                                *timestamp_lock.write() = get_unix_time();
                            },
                            Err(_) => break,
                            _ => (),
                        }
                    }
                    result = stderr_reader.next_line() => {
                        match result {
                            Ok(Some(line)) => {
                                debug!("Stderr: {}", line);
                                *timestamp_lock.write() = get_unix_time();
                            },
                            Err(_) => break,
                            _ => (),
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