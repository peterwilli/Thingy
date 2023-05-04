#[cfg(test)]
mod tests {
    use crate::queue_runner::QueueRunner;
    use hex_literal::hex;
    use rand::{Rng, SeedableRng};
    use rand_chacha::ChaCha8Rng;
    use redis::Commands;
    use rslock::LockManager;
    use std::collections::HashMap;
    use std::time::Duration;
    use test_log::test;
    use tokio::time::sleep;
    use url::Url;

    #[test(tokio::test)]
    async fn test_queue_order() {
        let client = redis::Client::open("redis://127.0.0.1/").unwrap();
        let mut con = client.get_connection().unwrap();
        let mut rng = ChaCha8Rng::seed_from_u64(80085);

        for i in 0..10 {
            let doc_id = format!("doc_{}", i);
            let hash_key = format!("entry:{}", doc_id);
            let script_id = rng.gen_range(0..5).to_string();

            // Set demo script
            let _: () = con
                .hset(
                    "scripts",
                    &script_id,
                    String::from_utf8_lossy(include_bytes!("resources/test_script.py")).to_string(),
                )
                .unwrap();

            let _: () = con.hset(&hash_key, "progress", 0.1).unwrap();
            let _: () = con.hset(&hash_key, "doc", hex!("80049584010000000000008c11646f6361727261792e646f63756d656e74948c08446f63756d656e749493942981947d948c055f64617461948c16646f6361727261792e646f63756d656e742e64617461948c0c446f63756d656e74446174619493942981947d94288c0e5f7265666572656e63655f646f639468038c026964948c203436363933633030643765633139313633383036323063613831306537356666948c09706172656e745f6964944e8c0b6772616e756c6172697479944e8c0961646a6163656e6379944e8c04626c6f62944e8c0674656e736f72944e8c096d696d655f74797065944e8c0474657874948c066b616e6b6572948c07636f6e74656e74944e8c06776569676874944e8c03757269944e8c0474616773944e8c095f6d65746164617461944e8c066f6666736574944e8c086c6f636174696f6e944e8c09656d62656464696e67944e8c086d6f64616c697479944e8c0b6576616c756174696f6e73944e8c0673636f726573944e8c066368756e6b73944e8c076d617463686573944e756273622e").to_vec()).unwrap();
            let _: () = con.hset(&hash_key, "scriptID", &script_id).unwrap();
            let _: () = con
                .lpush(format!("queue:todo:{}", script_id), doc_id)
                .unwrap();
            let _: () = redis::cmd("ZADD")
                .arg("scriptsInQueue")
                .arg("INCR")
                .arg(1)
                .arg(script_id)
                .query(&mut con)
                .unwrap();
        }
        let mut queue_runner = QueueRunner::new(Url::parse("redis://127.0.0.1/").unwrap());
        loop {
            queue_runner.queue_tick().await.unwrap();
            sleep(Duration::from_secs(1)).await;
        }
    }
}
