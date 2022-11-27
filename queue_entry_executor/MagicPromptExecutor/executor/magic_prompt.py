from jina import Executor, requests
from docarray import DocumentArray, Document
import os
import json
from typing import Dict
from .model_manager import ModelManager

script_path = os.path.dirname(os.path.realpath(__file__))
model_path = os.path.join(script_path, "model", "model.onnx")
model_manager = ModelManager(model_path)
model_manager.init_model()

class MagicPromptExecutor(Executor):
    """generate prompt texts for imaging AIs"""
    @requests(on='/magic_prompt/stable_diffusion')
    def generate_prompt(self, docs: DocumentArray, parameters: Dict, **kwargs):
        prompt = docs[0].text
        amount = int(parameters['amount'])
        variation = parameters['variation']
        return DocumentArray([
            Document(text = model_manager.magic_prompt(prompt, variation)) for _ in range(0, amount)
        ])