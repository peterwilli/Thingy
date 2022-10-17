use std::sync::Arc;
use tokio::sync::RwLock;
use crate::VM;

pub type VmLock = Arc<RwLock<VM>>;