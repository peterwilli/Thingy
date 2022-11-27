import time


def on_document(document, callback):
    for i in range(0, 10):
        callback(Document(text=f"test_{i}", tags = {
            "progress": (i + 1) / 10
        }))
        time.sleep(0.1)