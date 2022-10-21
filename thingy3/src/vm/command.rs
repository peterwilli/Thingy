use rune::Value;
use tokio::sync::oneshot::Sender;

#[derive(Debug)]
pub struct WValue(pub Value);
unsafe impl Send for WValue {}

#[derive(Debug)]
pub enum VMCommandType {
    // TODO: add real args
    OnCommand(String)
}

#[derive(Debug)]
pub struct VMCommand {
    pub r#type: VMCommandType,
    pub return_sender: Option<Sender<WValue>>
}