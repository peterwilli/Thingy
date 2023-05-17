#hydrolane->deps:diffusers, transformers
from jina import Executor, requests, DocumentArray, Document

from diffusers import DiffusionPipeline
from transformers import T5EncoderModel
import torch
import math
import gc
import pickle


def calculate_size(base_size, w, h):
    r = max(w, h) / min(w, h)
    s_w = int(math.floor(base_size * r))
    s_h = int(math.floor(s_w / r))
    if w < h:
        return s_h, s_w
    else:
        return s_w, s_h

def next_divisible(n, d):
    divisor = n % d
    return n - divisor


global_object = {
    'pipe': None
}

def clean_memory():
    gc.collect()
    torch.cuda.empty_cache()

def get_stage(hf_auth_token):
    pretrained_path = get_pretrained_path_safe("DeepFloyd/IF-I-XL-v1.0")
    print("pretrained_path:", pretrained_path)
    text_encoder = T5EncoderModel.from_pretrained(
        pretrained_path, subfolder="text_encoder", device_map="auto", torch_dtype=torch.float16, variant="fp16", use_auth_token=hf_auth_token
    )
    pipe = DiffusionPipeline.from_pretrained(
        pretrained_path,
        text_encoder=text_encoder,  # pass the previously instantiated 8bit text encoder
        use_auth_token=hf_auth_token,
        unet=None,
        device_map="auto",
    )
    return pipe

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        generator = torch.manual_seed(int(document.tags['seed']))
        hf_token = document.tags['_hf_auth_token']
        if global_object['pipe'] is None:
            global_object['pipe'] = get_stage(hf_token)
        stage = global_object['pipe']
        prompt = document.tags['prompt']
        negative_prompt = document.tags['negative_prompt']
        prompt_embeds, negative_embeds = stage.encode_prompt(prompt)
        data = {
            'prompt': prompt,
            'prompt_embeds': prompt_embeds,
            'negative_embeds': negative_embeds
        }
        data_blob = pickle.dumps(data)
        worker.set_progress(document.id.decode('ascii'), Document(blob=data_blob), 1)
