worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for doc in bucket:
        worker.set_progress(doc.id.decode('ascii'), Document(text = "lol"), 1)