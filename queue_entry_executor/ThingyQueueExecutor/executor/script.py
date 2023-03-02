import re
import subprocess
import sys
import time
from jina import Document
from threading import Thread

bootstrap = """
import json
from jina import Document
import traceback

while True:
    print("thingy:ready")
    doc = input()
    try:
        doc = Document.from_base64(doc)
        on_document(doc, lambda doc: print(doc.to_base64()))
    except Exception as e:
        print("parse_error", doc, traceback.format_exc())
"""


def pip_install(package):
    subprocess.call([sys.executable, "-u", "-m", "pip", "-v", "install", package])


re_deps = re.compile(r"#thingy->deps:(.+)")


class Script:
    def __init__(self, script):
        self.process = None
        self.script = script
        self.debug_mode = True
        self.last_output = 0
        self.is_running_command = False

    def install_deps(self):
        deps = re_deps.findall(self.script)
        if deps is not None and len(deps) > 0:
            deps = deps[0].split(",")
            for dep in deps:
                self._debug(f"Installing {dep} from script dependencies")
                pip_install(dep)
        self._debug("Dependencies done")

    def start(self):
        if self.process is None:
            with open("current_script.py", "w") as f:
                f.write(self.script)
                f.write(bootstrap)
            self.process = subprocess.Popen([sys.executable, '-u', 'current_script.py'],
                                            stdin=subprocess.PIPE,
                                            stdout=subprocess.PIPE,
                                            stderr=subprocess.STDOUT,
                                            bufsize=1,
                                            universal_newlines=True
                                            )

    def stop(self):
        if self.process is not None:
            self.process.terminate()
            self.process = None
        self.is_running_command = False

    def _debug(self, msg):
        if self.debug_mode:
            print(msg)

    def _run_worker(self, doc, callback):
        while self.is_running_command:
            print("waiting for ready")
            line = self.process.stdout.readline()
            self.last_output = int(time.time())
            self._debug("waiting for ready line: " + line)
            if line.strip() == "thingy:ready":
                print("Ready given, continuing!")
                time.sleep(0.5)
                break

        to_send = f"{doc.to_base64()}\n"
        self.process.stdin.write(to_send)
        for line in self.process.stdout:
            self.last_output = int(time.time())
            self._debug(f"process.stdout->line: {line}")
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
                    self._debug("Process closed (100%)!")
                    self.is_running_command = False
                    return
        self._debug("Process closed!")
        self.stop()

    def _timer_worker(self, callback):
        while self.is_running_command:
            if (int(time.time()) - self.last_output) > 60:
                self._debug("Killing process because of timeout")
                callback(Document(text="error", tags={"error_type": "timeout"}))
                self.stop()
            time.sleep(1)

    def run(self, doc: Document, callback):
        if self.is_running_command:
            raise Exception("Already running command!")
        self.is_running_command = True
        self.last_output = int(time.time())
        Thread(target=self._timer_worker, args=(callback,)).start()
        Thread(target=self._run_worker, args=(doc, callback)).start()
