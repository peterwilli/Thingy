use rune::Value;
use tokio::sync::oneshot::Sender;

pub enum VMCommandType {
    // TODO: add real args
    OnCommand(String)
}

pub struct VMCommand {
    pub r#type: VMCommandType,
    pub return_sender: Option<Sender<Value>>
}