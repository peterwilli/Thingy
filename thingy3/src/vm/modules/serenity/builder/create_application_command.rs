use rune::{ContextError, Module, Any};

pub fn module() -> Result<Module, ContextError> {
    let mut module = Module::with_crate("serenity");
    module.ty::<CreateApplicationCommand>()?;
    module.inst_fn("header", CreateApplicationCommand::name)?;
    return Ok(module);
}

#[derive(Debug, Any)]
pub struct CreateApplicationCommand {
    inner: serenity::builder::CreateApplicationCommand,
}

impl CreateApplicationCommand {
    pub fn name(mut self, name: &str) -> Self {
        self.inner.name(name);
        self
    }

    pub fn description(mut self, description: &str) -> Self {
        self.inner.description(description);
        self
    }
}