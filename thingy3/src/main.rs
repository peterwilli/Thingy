use std::time::Duration;
use log::debug;
use tokio::task;
use tokio::task::spawn_local;
use tokio::time::sleep;
use crate::discord_bot::DiscordBot;
use crate::vm::{VM, VMCommandType, WValue};

mod discord_bot;
mod vm;

#[tokio::main]
async fn main() {
    env_logger::init();
    // let bot = DiscordBot::new();
    // bot.start("").await;
    // TODO: Play with demo code
    // spawn_local(async move {
    //     loop {
    //         debug!("Loop 1");
    //         sleep(Duration::from_millis(250)).await;
    //     }
    // });
    // spawn_local(async move {
    //     loop {
    //         debug!("Loop 2");
    //         sleep(Duration::from_millis(250)).await;
    //     }
    // });
    // loop {
    //     sleep(Duration::from_millis(250)).await;
    // }
}
