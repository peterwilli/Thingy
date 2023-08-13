#hydrolane->deps:diffusers, transformers
from jina import Executor, requests, DocumentArray, Document

from diffusers import DiffusionPipeline
import torch
import pickle
import math

def get_pipe(hf_auth_token):
    pipe = DiffusionPipeline.from_pretrained(get_pretrained_path_safe("kandinsky-community/kandinsky-2-2-decoder"), torch_dtype=torch.float16)

    def dummy(images, **kwargs):
        return images, [False] * len(images)
    pipe.safety_checker = dummy
    pipe.to("cuda")
    return pipe

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

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        generator = torch.manual_seed(int(document.tags['seed']))
        hf_token = document.tags['_hf_auth_token']
        steps = int(document.tags['steps'])
        base_size = int(document.tags['size'])
        prompt = document.tags["prompt"]
        if global_object['pipe'] is None:
            global_object['pipe'] = get_pipe(hf_token)
        pipe = global_object['pipe']
        data = pickle.loads(document.blob)
        ar = document.tags['ar'].split(":")
        width, height = calculate_size(base_size, int(ar[0]), int(ar[1]))
        width = next_divisible(width, 8)
        height = next_divisible(height, 8)
        if width < base_size:
            width = base_size
        if height < base_size:
            height = base_size
        image = pipe(image_embeds=data['image_embeds'], negative_image_embeds=data['negative_image_embeds'], width=width, height=height, num_inference_steps=int(document.tags['steps'])).images[
            0
        ]
        worker.set_progress(document.id.decode('ascii'), Document().load_pil_image_to_datauri(image), 1)
