import string
import random
from typing import Dict
import json
import subprocess
import os
import time
from .script import Script
from jina import Executor, requests, DocumentArray, Document

global_object = {
    'current_script': None,
    'current_status': {}
}

def run_doc_entry(docs: DocumentArray, queue_id: str, index: int):
    if index == len(docs):
        return

    if queue_id not in global_object['current_status']:
        global_object['current_status'][queue_id] = []

    def callback(doc):
        if len(global_object['current_status'][queue_id]) == index:
            global_object['current_status'][queue_id].append(None)
        global_object['current_status'][queue_id][-1] = doc
        global_object['current_status'][queue_id][-1].tags.update({
            'document_index': index
        })
        if "progress" in doc.tags:
            if doc.tags["progress"] == 1:
                run_doc_entry(docs, queue_id, index + 1)
        if "error_type" in doc.tags:
            run_doc_entry(docs, queue_id, index + 1)
    global_object['current_script'].run(docs[index], callback)


def generate_queue_id() -> str:
    # choose from all lowercase letter
    letters = string.ascii_letters + string.digits
    return ''.join(random.choice(letters) for _ in range(32))


class ThingyQueueExecutor(Executor):
    @requests(on='/queue_entry_status')
    def queue_entry_status(self, docs: DocumentArray, parameters: Dict, **kwargs):
        if global_object['current_status'] is not None:
            index = int(parameters['index'])
            queue_id = parameters['queue_id']
            if queue_id in global_object['current_status'] and \
                    len(global_object['current_status'][queue_id]) > index and \
                    global_object['current_status'][queue_id][index] is not None:
                doc = global_object['current_status'][queue_id][index]
                if "progress" in doc.tags and doc.tags["progress"] == 1:
                    del global_object['current_status'][queue_id]
                return DocumentArray(doc)

        return DocumentArray()

    @requests(on='/run_queue_entry')
    def run_queue_entry(self, docs: DocumentArray, parameters: Dict, **kwargs):
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
        queue_id = generate_queue_id()
        run_doc_entry(docs, queue_id, 0)
        return DocumentArray(Document(text=queue_id))
