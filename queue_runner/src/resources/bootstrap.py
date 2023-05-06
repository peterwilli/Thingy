from jina import Document
import redis
import time

prefix = "{%PREFIX%}"


class ThingyWorker:
    def __init__(self):
        self.redis = redis.Redis(host='localhost', port=6379, decode_responses=False)
        self.bucket_name = f'queue:todo:{prefix}'
        self.doing_bucket_name = f'queue:doing:{prefix}'

    def set_progress(self, document_id: str, updated_document: Document, progress: float):
        hash_key = f"entry:{document_id}"
        self.redis.hset(hash_key, mapping={
            'updatedDoc': updated_document.to_bytes(protocol='protobuf'),
            'progress': progress
        })
        if progress == 1:
            pipe = self.redis.pipeline()
            pipe.fcall('remove_n_script_in_queue', 1, 'scriptsInQueue', prefix, 1)
            pipe.lrem(self.doing_bucket_name, 1, document_id)
            pipe.execute()

    def get_current_bucket(self):
        # print("get_current_bucket")
        time.sleep(1)
        result = []
        doc_id = self.redis.fcall('get_and_move_doc', 2, self.bucket_name, self.doing_bucket_name)
        if doc_id is not None:
            hash_key = f"entry:{doc_id.decode('ascii')}"
            doc_bytes = self.redis.hget(hash_key, 'doc')
            doc = Document.from_bytes(doc_bytes, protocol='protobuf')
            doc.id = doc_id  # In case the ID's differ (this can happen, and is fine)
            result.append(doc)
        return result
