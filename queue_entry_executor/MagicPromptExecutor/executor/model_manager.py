import os
import urllib.request
from transformers import AutoTokenizer
from onnxruntime import InferenceSession
import numpy as np
import random
import onnxruntime as ort

class ModelManager:
    def __init__(self, path):
        self.path = path
        self.max_length = 128
        
    def init_model(self):
        if not os.path.exists(self.path):
            print("Downloading model...")
            # TODO: change URL to new upload
            urllib.request.urlretrieve("https://github.com/peterwilli/Endless-AWSW/releases/download/v0.3/model.onnx", model_path)

        self.tokenizer = AutoTokenizer.from_pretrained(os.path.dirname(self.path))
        self.tokenizer.padding_side = "right"
        self.tokenizer.pad_token = self.tokenizer.eos_token
        self.model = ort.InferenceSession(self.path)

    def batch_encode(self, texts) -> dict:
        return self.tokenizer.batch_encode_plus(texts, padding=True)

    def get_model_input(self, prompt):
        encodings_dict = self.batch_encode(prompt)
        input_ids = np.array(encodings_dict['input_ids'], dtype=np.int64)
        attention_mask = np.array(encodings_dict['attention_mask'], dtype=np.int64)
        return input_ids, attention_mask

    def word_chance(self, x, scale):
        c = 1.0
        for i in range(x.shape[0]):
            x[i] = c
            c *= scale
        return x

    def normalize(self, x):
        x = abs(np.min(x)) + x
        return x / x.sum(axis=0, keepdims=1)

    def magic_prompt(self, prompt, variation = 0.3) -> str:
        input_ids, attention_mask = self.get_model_input([prompt])
        eos_token_id = self.tokenizer.eos_token_id
        batch_size = input_ids.shape[0]
        all_token_ids = input_ids
        for step in range(self.max_length):
            inputs = {}
            inputs['attention_mask'] = attention_mask
            inputs['input_ids'] = input_ids
            outputs = self.model.run(None, inputs)                
            next_token_logits = outputs[0][:, -1, :]
            
            word_count = 10
            next_tokens = np.argpartition(-next_token_logits, word_count).flatten()[:word_count]
            chances = next_token_logits.flatten()[next_tokens]
            chances = self.normalize(chances)
            chances_list = []
            for i, c in enumerate(chances):
                chances_list.append({
                    'c': c,
                    'i': next_tokens[i]
                })
            chances_list.sort(key=lambda x: x['c'], reverse=True)
            new_chances = np.zeros(10, dtype = np.float32)
            self.word_chance(new_chances, variation)
            for i in range(new_chances.shape[0]):
                new_chances[i] = new_chances[i] * chances_list[i]['c']
            selection = random.choices(chances_list, weights=new_chances, k=1)[0]['i']
            next_tokens = np.array([selection])
            
            if eos_token_id in next_tokens:
                break
            
            all_token_ids = np.concatenate((all_token_ids, np.expand_dims(next_tokens, -1)), axis=-1)
            # Update input_ids, attention_mask and past
            input_ids = all_token_ids
            attention_mask = np.ones((batch_size, 1), dtype=np.int64)
            
        text_result = self.tokenizer.decode(all_token_ids[0], skip_special_tokens=False)
        if True or len(all_token_ids[0]) == self.max_length:
            # Strip last word as it's likely incomplete
            text_result = text_result.rsplit(' ', 1)[0]
        text_result = text_result.replace("\n", "")
        return text_result