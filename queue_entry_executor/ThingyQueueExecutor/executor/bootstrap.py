from jina import Document
import redis

prefix = "%{PREFIX}%"

class ThingyWorker:
    def __init__(self):
        self.redis = redis.Redis(host='localhost', port=6379, decode_responses=False)
        self.bucket_name = f'{prefix}_bucket'

    def set_progress(self, document):
        self.redis.hset(f'progress_{document.id}', Document.to_bytes(document))

    def get_current_bucket(self):
        bytes_list = None
        with self.redis.pipeline() as pipe:
            pipe.multi()
            pipe.lrange(self.bucket_name, 0, -1)
            pipe.ltrim(self.bucket_name, -1, 0)
            result = pipe.execute()
            bytes_list = result[0]
        return [Document.from_bytes(d_bytes) for d_bytes in bytes_list]


worker = ThingyWorker()