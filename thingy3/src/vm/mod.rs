use std::sync::Arc;
use std::time::Duration;
use log::debug;

use rune::{Context, Diagnostics, Source, Sources, Vm as RuneVM};
use rune::termcolor::{ColorChoice, StandardStream};
use tokio::task;
use tokio::sync::mpsc;
use tokio::sync::mpsc::Sender;
use tokio::task::spawn_local;
use tokio::time::sleep;

pub use crate::vm::command::{VMCommand, VMCommandType, WValue};

mod command;
mod modules;
pub mod types;

#[derive(Default)]
pub struct VM {
    sender: Option<Sender<VMCommand>>
}

impl VM {
    pub fn new() -> Self {
        return Self {
            ..Default::default()
        };
    }

    pub async fn start(&mut self) -> rune::Result<()> {
        let (command_tx, mut command_rx) = mpsc::channel(16);
        self.sender = Some(command_tx);
        let local = task::LocalSet::new();
        debug!("VM start");
        local.run_until(async move {
            debug!("VM start run_until");
            // TODO: How to fix spawn local to run in background?
            spawn_local(async move {
                debug!("VM start spawn_local");
                let mut vm = Self::create_rune_vm().unwrap();
                loop {
                    debug!("VM start loop");
                    let msg = command_rx.recv().await;
                    if msg.is_none() {
                        break;
                    }
                    let command = msg.unwrap();
                    let script_result = match command.r#type {
                        VMCommandType::OnCommand(s) => {
                            debug!("calling OnCommand");
                            vm.call(&["on_command"], (s, )).unwrap()
                        }
                        _ => {
                            panic!("Unknown command!");
                        }
                    };
                    if command.return_sender.is_some() {
                        command.return_sender.unwrap().send(WValue(script_result)).unwrap();
                    }
                    sleep(Duration::from_millis(250)).await;
                }
            });
        }).await;
        return Ok(());
    }

    fn create_rune_vm() -> rune::Result<RuneVM> {
        let context = Context::with_default_modules()?;
        let runtime = Arc::new(context.runtime());

        let mut sources = Sources::new();
        sources.insert(Source::new(
            "script",
            r#"
        pub fn on_command(a) {
            "test"
        }
        "#,
        ));

        let mut diagnostics = Diagnostics::new();

        let result = rune::prepare(&mut sources)
            .with_context(&context)
            .with_diagnostics(&mut diagnostics)
            .build();

        if !diagnostics.is_empty() {
            let mut writer = StandardStream::stderr(ColorChoice::Always);
            diagnostics.emit(&mut writer, &sources)?;
        }

        let unit = result?;
        return Ok(RuneVM::new(runtime, Arc::new(unit)));
    }

    pub fn get_sender(&self) -> Sender<VMCommand> {
        return self.sender.as_ref().expect("Make sure you call start() before get_sender()!").clone();
    }
}