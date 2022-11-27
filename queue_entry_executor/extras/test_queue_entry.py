import os
import time
from jina import Flow, Executor, Client, Document, DocumentArray, requests

c = Client(host='grpc://localhost:51001')
file_path = os.path.abspath(os.path.dirname(__file__))
with open(os.path.join(file_path, "test_script.py"), "r") as f:
    script = f.read()
script_params = {
    "prompt": "Laughing mother in Greece"
}
print(c.post('/run_queue_entry', Document(tags={ "script_params": script_params }), parameters = {
    "script": script
})[0].text)
print("Testing status")

while True:
    result = c.post('/queue_entry_status', parameters = {
        "index": 0
    })
    if len(result) > 0:
        doc = result[0]
        progress = doc.tags["progress"]
        print(f"progress: {progress}")
        if progress == 1:
            print("done!")
            break
    time.sleep(1)