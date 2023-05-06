use std::time::Duration;
use log::error;
use tokio::time::sleep;
use url::Url;
use crate::queue_runner::QueueRunner;

mod tests;
mod queue_runner;
pub(crate) mod objects;
pub(crate) mod utils;
pub(crate) mod types;
pub(crate) mod consts;

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
        sleep(Duration::from_secs(1)).await;
    }
}
