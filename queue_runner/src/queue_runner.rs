use crate::objects::thingy_process::ThingyProcess;
use anyhow::{Context, Result};
use log::debug;
use redis::Commands;
use rslock::LockManager;
use std::fs::File;
use std::io::Write;
use std::process::{Command, Stdio};
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
        return Self {
            client,
            con,
            redis_lock,
            current_process: None,
        };
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
                proc.kill();
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
                .stdout(Stdio::inherit())
                .spawn()?;
            let process = ThingyProcess::new(script_id, child);
            self.current_process = Some(process);
        }
        return Ok(());
    }
}
