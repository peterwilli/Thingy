mod handler;

use std::sync::Arc;
use serenity::Client;
use serenity::prelude::GatewayIntents;
use tokio::sync::RwLock;
use crate::discord_bot::handler::Handler;
use crate::VM;
use crate::vm::types::VmLock;

struct VMLockData;

impl serenity::prelude::TypeMapKey for VMLockData {
    type Value = Vec<VmLock>;
}

#[derive(Default)]
pub struct DiscordBot {
    vms: Vec<VmLock>
}

impl DiscordBot {
    pub fn new() -> Self {
        return Self {
            ..Default::default()
        };
    }

    pub async fn start(&self, token: impl AsRef<str>) {
        // Build our client.
        let mut client = Client::builder(token, GatewayIntents::empty())
            .event_handler(Handler)
            .await
            .expect("Error creating client");
        let mut data = client.data.write().await;
        let mut test_vm = VM::new();
        test_vm.start().await.unwrap();
        data.insert::<VMLockData>(vec![Arc::new(RwLock::new(test_vm))]);
        drop(data);

        // Finally, start a single shard, and start listening to events.
        //
        // Shards will automatically attempt to reconnect, and will perform
        // exponential backoff until it reconnects.
        if let Err(why) = client.start().await {
            println!("Client error: {:?}", why);
        }
    }
}