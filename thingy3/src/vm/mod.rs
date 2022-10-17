mod command;
pub mod types;

use std::sync::Arc;
use std::time::Duration;
use rune::{Context, Diagnostics, FromValue, Source, Sources, Vm as RuneVM};
use rune::termcolor::{ColorChoice, StandardStream};
use tokio::{spawn, task};
use tokio::sync::{mpsc, RwLock};
use tokio::sync::mpsc::Sender;
use tokio::task::spawn_local;
use tokio::time::sleep;
use crate::vm::command::{VMCommand, VMCommandType};

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
        local.run_until(async move {
            spawn_local(async move {
                let mut vm = Self::create_rune_vm().unwrap();
                loop {
                    let msg = command_rx.recv().await;
                    if msg.is_none() {
                        break;
                    }
                    let command = msg.unwrap();
                    let script_result = match command.r#type {
                        VMCommandType::OnCommand(s) => {
                            vm.call(&["on_command"], (s, )).unwrap()
                        }
                        _ => {
                            panic!("Unknown command!");
                        }
                    };
                    if command.return_sender.is_some() {
                        command.return_sender.unwrap().send(script_result).unwrap();
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
        pub fn add(a, b) {
            a + b
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