import subprocess
from jina import Document
from threading import Thread
import time

bootstrap = """
import json
from jina import Document
import traceback

while True:
    doc = input()
    try:
        doc = Document.from_base64(doc)
        on_document(doc, lambda doc: print(doc.to_base64()))
    except Exception as e:
        print("parse_error", doc, traceback.format_exc())
"""


class Script:
    def __init__(self, script):
        self.process = None
        self.script = script
        self.debug_mode = True

    def start(self):
        with open("current_script.py", "w") as f:
            f.write(self.script)
            f.write(bootstrap)
        self.process = subprocess.Popen(['python', '-u', 'current_script.py'],
                                        stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT,
                                        bufsize=1,
                                        universal_newlines=True
                                        )
        time.sleep(0.5)

    def stop(self):
        self.process.terminate()

    def _debug(self, msg):
        if self.debug_mode:
            print(msg)

    def _run_worker(self, doc, callback):
        self._debug(doc.tags)
        to_send = f"{doc.to_base64()}\n"
        self.process.stdin.write(to_send)
        while True:
            line = self.process.stdout.readline()
            doc = None
            try:
                doc = Document.from_base64(line)
            except:
                pass
            if doc is None:
                self._debug(line.strip())
            else:
                if "progress" not in doc.tags:
                    # Assume synchronous
                    doc.tags["progress"] = 1
                callback(doc)
                if doc.tags["progress"] == 1:
                    return

    def run(self, doc: Document, callback):
        Thread(target=self._run_worker, args=(doc, callback)).start()
