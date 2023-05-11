use crate::consts::T_PROCESS_TIMEOUT;
use crate::objects::thingy_process::ThingyProcess;
use crate::utils::unix_time::get_unix_time;
use anyhow::{Context, Result};
use log::{debug, warn};
use redis::Commands;
use rslock::LockManager;
use std::collections::HashMap;
use std::fs::File;
use std::io::Write;
use std::process::Stdio;
use tokio::process::Command;
use url::Url;

pub struct QueueRunner {
    client: redis::Client,
    con: redis::Connection,
    redis_lock: LockManager,
    current_process: Option<ThingyProcess>,
}

impl QueueRunner {
    pub fn new(redis_url: Url) -> Self {
        let client = redis::Client::open(redis_url.clone()).unwrap();
        let con = client.get_connection().unwrap();
        let redis_lock = LockManager::new(vec![redis_url]);
        let mut result = Self {
            client,
            con,
            redis_lock,
            current_process: None,
        };
        result.init_redis_scripts().unwrap();
        return result;
    }

    fn init_redis_scripts(&mut self) -> Result<()> {
        let remove_script_in_queue =
            String::from_utf8_lossy(include_bytes!("resources/redis_functions.lua")).to_string();
        let _: () = redis::cmd("FUNCTION")
            .arg("LOAD")
            .arg("REPLACE")
            .arg(remove_script_in_queue)
            .query(&mut self.con)?;
        return Ok(());
    }

    fn get_scripts_in_current_queue(&mut self) -> Result<usize> {
        if self.current_process.is_none() {
            return Ok(0);
        }
        let current_script_id = &self.current_process.as_ref().unwrap().script_id;
        let total_commands_still_in_queue: Option<usize> =
            self.con.zscore("scriptsInQueue", current_script_id)?;
        return Ok(total_commands_still_in_queue.unwrap_or(0));
    }

    pub async fn queue_tick(&mut self) -> Result<()> {
        if self.current_process.is_some() {
            let proc = self.current_process.as_mut().unwrap();
            let proc_is_timed_out = get_unix_time() - *proc.timestamp.read() > T_PROCESS_TIMEOUT;
            if proc_is_timed_out {
                warn!("Script {} has timed out", proc.script_id);
                proc.kill().await;
                let doc_ids_doing: Vec<String> =
                    self.con
                        .lrange(format!("queue:doing:{}", proc.script_id), 0, -1)?;
                if doc_ids_doing.is_empty() {
                    let doc_ids_todo: Vec<String> =
                        self.con
                            .lrange(format!("queue:todo:{}", proc.script_id), 0, -1)?;
                    if !doc_ids_todo.is_empty() {
                        let mut pipe = redis::pipe();
                        pipe = pipe
                            .cmd("FCALL")
                            .arg("remove_n_script_in_queue")
                            .arg(1)
                            .arg("scriptsInQueue")
                            .arg(&proc.script_id)
                            .arg(doc_ids_todo.len())
                            .ignore()
                            .to_owned();
                        for doc_id in doc_ids_todo.iter() {
                            pipe = pipe
                                .lrem(format!("queue:todo:{}", proc.script_id), 1, doc_id)
                                .ignore()
                                .to_owned();
                            pipe = pipe
                                .hset(format!("entry:{}", doc_id), "error", "script_timed_out")
                                .ignore()
                                .to_owned();
                        }
                        let _: () = pipe.query(&mut self.con)?;
                    }
                } else {
                    let mut pipe = redis::pipe();
                    pipe = pipe
                        .cmd("FCALL")
                        .arg("remove_n_script_in_queue")
                        .arg(1)
                        .arg("scriptsInQueue")
                        .arg(&proc.script_id)
                        .arg(doc_ids_doing.len())
                        .ignore()
                        .to_owned();
                    for doc_id in doc_ids_doing.iter() {
                        pipe = pipe
                            .hset(format!("entry:{}", doc_id), "error", "command_timed_out")
                            .ignore()
                            .to_owned();
                        pipe = pipe
                            .lrem(format!("queue:doing:{}", proc.script_id), 1, doc_id)
                            .ignore()
                            .to_owned();
                    }
                    let _: () = pipe.query(&mut self.con)?;
                }
                self.current_process = None;
            }
        }
        let scripts_in_current_queue = self.get_scripts_in_current_queue()?;
        let script_ids: Vec<String> = if scripts_in_current_queue > 0 {
            vec![self.current_process.as_ref().unwrap().script_id.clone()]
        } else {
            redis::cmd("ZRANGE")
                .arg("scriptsInQueue")
                .arg(0)
                .arg(0)
                .arg("REV")
                .query(&mut self.con)?
        };
        if script_ids.is_empty() {
            return Ok(());
        }
        let script_id = script_ids.into_iter().nth(0).unwrap();
        let script: String = self.con.hget("scripts", &script_id)?;
        if self.current_process.is_some() {
            let proc = self.current_process.as_mut().unwrap();
            if proc.script_id != script_id {
                proc.kill().await;
                self.current_process = None;
            }
        }
        if self.current_process.is_none() {
            let tmp_dir = std::env::temp_dir();
            let file_name = format!("thingy_script_{}.py", script_id);
            let file_path = tmp_dir.join(&file_name);
            debug!("starting new process {}...", file_path.display());
            let script = format!(
                "{}\n{}",
                String::from_utf8_lossy(include_bytes!("resources/bootstrap.py"))
                    .replace("{%PREFIX%}", &script_id),
                script
            );
            // TODO: check if file is the same
            let mut file = File::create(&file_path)
                .with_context(|| format!("Failed to create {:?}", file_path))?;
            file.write_all(script.as_bytes())
                .with_context(|| format!("Failed to write to {:?}", file_path))?;
            let child = Command::new("python3")
                .arg("-u")
                .arg(file_path)
                .arg(&script_id)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn()?;
            let mut process = ThingyProcess::new(script_id, child);
            process.init_read_loop();
            self.current_process = Some(process);
        }
        return Ok(());
    }
}
