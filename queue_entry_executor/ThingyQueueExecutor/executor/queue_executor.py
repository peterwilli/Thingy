from typing import Dict
import redis
from .script import Script
from jina import Executor, requests, DocumentArray, Document

global_object = {
    'current_script': None
}

class ThingyQueueExecutor(Executor):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.redis = redis.Redis(host='localhost', port=6379, decode_responses=True)

    @requests(on='/queue_entry_status')
    def queue_entry_status(self, docs: DocumentArray, parameters: Dict, **kwargs):
        queue_id = parameters['queue_id']
        doc = None
        with self.redis.pipeline() as pipe:
            pipe.multi()
            doc_bytes = self.redis.hget(f'progress_{queue_id}')
            if doc_bytes is not None:
                doc = Document.from_bytes(doc_bytes)
                progress = doc.tags['progress']
                if progress == 1.0:
                    pipe.hdel(f'progress_{queue_id}')
            pipe.execute()
        if doc is not None:
            return DocumentArray(doc)
        return DocumentArray()

    @requests(on='/run_queue_entries')
    def run_queue_entries(self, docs: DocumentArray, parameters: Dict, **kwargs):
        new_script = parameters['script']
        should_spawn_new_script = False
        if global_object['current_script'] is None:
            should_spawn_new_script = True
        elif global_object['current_script'].script != new_script:
            global_object['current_script'].stop()
            should_spawn_new_script = True
        if should_spawn_new_script:
            global_object['current_script'] = Script(new_script)
            global_object['current_script'].install_deps()
        global_object['current_script'].start()
        prefix = global_object['current_script'].prefix
        bucket_name = f'{prefix}_bucket'
        with self.redis.pipeline() as pipe:
            pipe.multi()
            for doc in docs:
                pipe.rpush(bucket_name, doc.to_bytes())
            pipe.execute()
        return DocumentArray([Document(text=doc.id) for doc in docs])
