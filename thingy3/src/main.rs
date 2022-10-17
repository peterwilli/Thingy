use tokio::task;
use crate::discord_bot::DiscordBot;
use crate::vm::VM;

mod discord_bot;
mod vm;

#[tokio::main]
async fn main() {
    let bot = DiscordBot::new();
    let mut vm = VM::default();
    vm.start().await.unwrap();
    bot.start("MTAxOTE3MjYxMjAyNDMyMDA3Mg.GZNCeA.jGwtMGTjbnLxj-EvB-z8ybnZ3F-0FlQCHnIpYk").await;
}
