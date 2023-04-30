import re
import subprocess
import sys
import time
from jina import Document
from threading import Thread
from blake3 import blake3

def pip_install(package):
    subprocess.call([sys.executable, "-u", "-m", "pip", "-v", "install", package])


re_deps = re.compile(r"#thingy->deps:(.+)")


class Script:
    def __init__(self, script):
        self.process = None
        self.script = script
        self.debug_mode = True
        self.last_output = 0
        self.prefix = blake3(str.encode(script)).hexdigest(length=8)

    def install_deps(self):
        deps = re_deps.findall(self.script)
        if deps is not None and len(deps) > 0:
            deps = deps[0].split(",")
            for dep in deps:
                self._debug(f"Installing {dep} from script dependencies")
                pip_install(dep)
        self._debug("Dependencies done")

    def _debug(self, msg):
        if self.debug_mode:
            print(msg)

    def start(self):
        if self.process is None:
            bootstrap = f"prefix=\"{self.prefix}\"\n"
            with open("current_script.py", "w") as f:
                f.write(bootstrap)
                f.write(self.script)
            self.process = subprocess.Popen([sys.executable, '-u', 'current_script.py'])
    def stop(self):
        if self.process is not None:
            self.process.terminate()
            self.process = None