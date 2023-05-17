#hydrolane->deps:diffusers, transformers
from jina import Executor, requests, DocumentArray, Document

from diffusers import IFPipeline
from diffusers.utils import pt_to_pil
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
    pipe = IFPipeline.from_pretrained(
        get_pretrained_path_safe("DeepFloyd/IF-I-XL-v1.0"), text_encoder=None, variant="fp16", torch_dtype=torch.float16, device_map="auto", use_auth_token=hf_auth_token
    )
    return pipe

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        generator = torch.manual_seed(int(document.tags['seed']))
        hf_token = document.tags['_hf_auth_token']
        steps = int(document.tags['steps'])
        if global_object['pipe'] is None:
            global_object['pipe'] = get_stage(hf_token)
        stage = global_object['pipe']
        data = pickle.loads(document.blob)
        ar = document.tags['ar'].split(":")
        width, height = calculate_size(64, int(ar[0]), int(ar[1]))
        image = stage(
            prompt_embeds=data['prompt_embeds'],
            negative_prompt_embeds=data['negative_embeds'],
            output_type="pt",
            generator=generator,
            num_inference_steps=steps
        ).images
        data['image'] = image
        worker.set_progress(document.id.decode('ascii'), Document(blob=pickle.dumps(data)).load_pil_image_to_datauri(pt_to_pil(image)[0]), 1)