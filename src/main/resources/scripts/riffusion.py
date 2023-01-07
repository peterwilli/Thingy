from jina import Executor, requests, DocumentArray, Document

from diffusers import DiffusionPipeline, DPMSolverMultistepScheduler
import torch
import math

def get_pipe(hf_auth_token):
    repo_id = "riffusion/riffusion-model-v1"
    device = "cuda"
    RiffusionPipeline.load_checkpoint(
        checkpoint=checkpoint,
        use_traced_unet=True,
        device=device,
    )
    return pipe

def next_divisible(n, d):
    divisor = n % d
    return n - divisor

global_object = {
    'pipe': None
}

def on_document(document, callback):
    generator = torch.manual_seed(int(document.tags['seed']))
    if global_object['pipe'] is None:
        global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
    pipe = global_object['pipe']
    base_size = document.tags['size']
    prompt = document.tags['prompt']
    ar = document.tags['ar'].split(":")
    width, height = calculate_size(base_size, int(ar[0]), int(ar[1]))
    width = next_divisible(width, 8)
    height = next_divisible(height, 8)
    print(f"width: {width} height: {height}")
    image = pipe(prompt, width = width, height = height, guidance_scale=document.tags["guidance_scale"], num_inference_steps=int(document.tags['steps'])).images[0]
    callback(Document().load_pil_image_to_datauri(image))