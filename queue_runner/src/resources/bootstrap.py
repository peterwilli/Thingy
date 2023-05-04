from jina import Document
import redis
import time
from pottery import Redlock

prefix = "{%PREFIX%}"


class ThingyWorker:
    def __init__(self):
        self.redis = redis.Redis(host='localhost', port=6379, decode_responses=False)
        self.bucket_name = f'queue:todo:{prefix}'

    def set_progress(self, document_id: str, updated_document: Document, progress: float):
        hash_key = f"entry:{document_id}"
        self.redis.hset(hash_key, mapping={
            'updated_doc': updated_document.to_bytes(),
            'progress': progress
        })
        if progress == 1:
            lock = Redlock(key=f"script_check_{prefix}", masters={self.redis}, auto_release_time=.5)
            with lock:
                incr_result = self.redis.zincrby("scriptsInQueue", -1, prefix)
                if incr_result <= 0:
                    self.redis.zrem("scriptsInQueue", prefix)

    def get_current_bucket(self):
        time.sleep(1)
        result = []
        with self.redis.pipeline() as pipe:
            pipe.multi()
            pipe.lrange(self.bucket_name, 0, 0)
            pipe.ltrim(self.bucket_name, 1, -1)
            pipe_result = pipe.execute()
            doc_ids = pipe_result[0]
            pipe.execute()

        for doc_id in doc_ids:
            hash_key = f"entry:{doc_id.decode('ascii')}"
            doc_bytes = self.redis.hget(hash_key, 'doc')
            doc = Document.from_bytes(doc_bytes)
            doc.id = doc_id  # In case the ID's differ (this can happen, and is fine)
            result.append(doc)
        return result
