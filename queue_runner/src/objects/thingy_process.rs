use std::process::Child;
use log::error;

pub struct ThingyProcess {
    pub process: Child,
    pub script_id: String
}

impl ThingyProcess {
    pub fn new(script_id: String, process: Child) -> Self {
        return Self {
            script_id, process
        };
    }

    pub fn kill(&mut self) {
        match self.process.kill() {
            Err(e) => {
                error!("kill error: {} (ignored)", e);
            }
            _ => {}
        };
    }
}