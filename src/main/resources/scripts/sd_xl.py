#hydrolane->deps:diffusers, transformers, Pillow, invisible_watermark, safetensors
from jina import Executor, requests, DocumentArray, Document

from diffusers import DPMSolverMultistepScheduler
from diffusers import DiffusionPipeline
from transformers import CLIPFeatureExtractor, CLIPTextModel, CLIPTokenizer
import torch
import math
import tempfile
import os
import base64

def calculate_size(base_size, w, h):
    r = max(w, h) / min(w, h)
    s_w = int(math.floor(base_size * r))
    s_h = int(math.floor(s_w / r))
    if w < h:
        return s_h, s_w
    else:
        return s_w, s_h

def get_pipe(hf_auth_token):
    repo_id = "stabilityai/stable-diffusion-xl-base-0.9"
    pipe = DiffusionPipeline.from_pretrained(repo_id, torch_dtype=torch.float16, use_safetensors=True, variant="fp16", use_auth_token=hf_auth_token)
    pipe.to("cuda")
    pipe.unet = torch.compile(pipe.unet, mode="reduce-overhead", fullgraph=True)
    return pipe

def next_divisible(n, d):
    divisor = n % d
    return n - divisor

global_object = {
    'pipe': None
}

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        generator = torch.manual_seed(int(document.tags['seed']))
        if global_object['pipe'] is None:
            global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
        pipe = global_object['pipe']
        base_size = document.tags['size']
        prompt = document.tags['prompt']
        embeds = document.tags['embeds']
        ar = document.tags['ar'].split(":")
        width, height = calculate_size(base_size, int(ar[0]), int(ar[1]))
        width = next_divisible(width, 8)
        height = next_divisible(height, 8)
        print(f"width: {width} height: {height}")
        image = pipe(prompt, num_inference_steps=int(document.tags['steps'])).images[0]
        worker.set_progress(document.id.decode('ascii'), Document().load_pil_image_to_datauri(image), 1)