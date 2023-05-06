use chrono::Utc;
use crate::types::TimeStamp;

pub fn get_unix_time() -> TimeStamp {
    let now = Utc::now();
    now.timestamp().try_into().unwrap()
}