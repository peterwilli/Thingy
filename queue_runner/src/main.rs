use crate::queue_runner::QueueRunner;
use log::error;
use std::time::Duration;
use tokio::time::sleep;
use url::Url;

pub(crate) mod consts;
pub(crate) mod objects;
mod queue_runner;
mod tests;
pub(crate) mod types;
pub(crate) mod utils;

#[tokio::main]
async fn main() {
    env_logger::init();
    let mut queue_runner = QueueRunner::new(Url::parse("redis://127.0.0.1/").unwrap());

    loop {
        match queue_runner.queue_tick().await {
            Err(e) => {
                error!("queue_tick error: {}", e);
            }
            _ => {}
        }
        sleep(Duration::from_secs(5)).await;
    }
}
