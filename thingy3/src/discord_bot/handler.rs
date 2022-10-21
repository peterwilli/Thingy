use log::debug;
use serenity::async_trait;
use serenity::model::application::command::Command;
use serenity::model::application::interaction::{Interaction, InteractionResponseType};
use serenity::model::gateway::Ready;
use serenity::model::id::GuildId;
use serenity::prelude::*;
use crate::discord_bot::VMLockData;
use crate::vm::VMCommand;
use crate::vm::VMCommandType::OnCommand;
use rune::{FromValue, Value};
use serenity::builder::CreateApplicationCommand;
use serenity::model::application::interaction::InteractionType::ApplicationCommand;
use tokio::join;

pub struct Handler;

#[async_trait]
impl EventHandler for Handler {
    async fn ready(&self, ctx: Context, ready: Ready) {
        println!("{} is connected!", ready.user.name);
        let data = ctx.data.read().await;
        let vm_locks = data.get::<VMLockData>().unwrap();
        for vm_lock in vm_locks.iter() {
            let vm = vm_lock.write().await;
            let (tx, rx) = tokio::sync::oneshot::channel();
            debug!("first run");
            let command = VMCommand {
                r#type: OnCommand("test".to_string()),
                return_sender: Some(tx)
            };

            let vm_sender = vm.get_sender();
            debug!("kanker");
            let (_, reply) = join!(vm_sender.send(command), rx);
            debug!("reply: {}", String::from_value(reply.unwrap().0).unwrap());
        }
        Command::set_global_application_commands(&ctx.http, |commands| {
            let mut cmd = CreateApplicationCommand::default();
            cmd.name("test").description("desc");
            commands.add_application_command(cmd)
        }).await.unwrap();
    }

    async fn interaction_create(&self, ctx: Context, interaction: Interaction) {
        if let Interaction::ApplicationCommand(command) = interaction {
            println!("Received command interaction: {:#?}", command);

            let content = match command.data.name.as_str() {
                _ => "not implemented :(".to_string(),
            };

            if let Err(why) = command
                .create_interaction_response(&ctx.http, |response| {
                    response
                        .kind(InteractionResponseType::ChannelMessageWithSource)
                        .interaction_response_data(|message| message.content(content))
                })
                .await
            {
                println!("Cannot respond to slash command: {}", why);
            }
        }
    }
}