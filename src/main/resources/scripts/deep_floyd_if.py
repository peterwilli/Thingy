from jina import Document
import redis


class ThingyWorker:
    def __init__(self):
        self.redis = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.bucket_name = f'{prefix}_bucket'

    def set_progress(self, document):
        self.redis.hset(f'progress_{document.id}', Document.to_bytes(document))

    def get_current_bucket(self):
        bytes_list = None
        with self.redis.pipeline() as pipe:
            pipe.multi()
            pipe.lrange(self.bucket_name, 0, -1)
            pipe.ltrim(self.bucket_name, -1, 0)
            result = pipe.execute()
            bytes_list = result[0]
        return [Document.from_bytes(d_bytes) for d_bytes in bytes_list]


import time

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    print(bucket)
    time.sleep(1)

from jina import Executor, requests, DocumentArray, Document

from diffusers import DiffusionPipeline
from diffusers.utils import pt_to_pil
import torch
import math
import gc
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


def get_stage(hf_auth_token, stage):
    if stage == 1:
        stage_1 = DiffusionPipeline.from_pretrained("DeepFloyd/IF-I-XL-v1.0", variant="fp16", torch_dtype=torch.float16)
        stage_1.enable_model_cpu_offload()

        def stage_1_fn(prompt, negative_prompt, num_inference_steps, generator):
            prompt_embeds, negative_embeds = stage_1.encode_prompt(prompt, negative_prompt=negative_prompt)
            image = stage_1(prompt_embeds=prompt_embeds, negative_prompt_embeds=negative_embeds, generator=generator,
                            num_inference_steps=num_inference_steps, output_type="pt").images
            return image, prompt_embeds, negative_embeds

        return stage_1_fn

    if stage == 2:
        stage_2 = DiffusionPipeline.from_pretrained(
            "DeepFloyd/IF-II-L-v1.0", text_encoder=None, variant="fp16", torch_dtype=torch.float16
        )
        stage_2.enable_model_cpu_offload()

        def stage_2_fn(image, prompt_embeds, negative_embeds, generator):
            image = stage_2(
                image=image, prompt_embeds=prompt_embeds, negative_prompt_embeds=negative_embeds, generator=generator,
                output_type="pt"
            ).images
            return image

        return stage_2_fn

    if stage == 3:
        def stage_3_fn(image, prompt, noise_level, generator):
            stage_1 = DiffusionPipeline.from_pretrained("DeepFloyd/IF-I-XL-v1.0", variant="fp16",
                                                        torch_dtype=torch.float16)
            stage_1.enable_model_cpu_offload()
            safety_modules = {
                "feature_extractor": stage_1.feature_extractor,
                "safety_checker": stage_1.safety_checker,
                "watermarker": stage_1.watermarker,
            }
            clean_memory()
            stage_3 = DiffusionPipeline.from_pretrained(
                "stabilityai/stable-diffusion-x4-upscaler", **safety_modules, torch_dtype=torch.float16
            )
            stage_3.enable_model_cpu_offload()
            image = stage_3(prompt=prompt, image=image, generator=generator, noise_level=noise_level).images[0]
            return image

        return stage_3_fn


def next_divisible(n, d):
    divisor = n % d
    return n - divisor


global_object = {
    'pipe': None
}


def clean_memory():
    gc.collect()
    torch.cuda.empty_cache()


def on_document(document, callback):
    generator = torch.manual_seed(int(document.tags['seed']))
    hf_token = document.tags['_hf_auth_token']
    base_size = 512
    prompt = document.tags['prompt']
    noise_level = document.tags['noise_level']
    negative_prompt = document.tags['negative_prompt']
    ar = document.tags['ar'].split(":")
    width, height = calculate_size(base_size, int(ar[0]), int(ar[1]))
    width = next_divisible(width, 8)
    height = next_divisible(height, 8)
    print(f"width: {width} height: {height}")
    stage_1 = get_stage(hf_token, 1)
    image, prompt_embeds, negative_embeds = stage_1(prompt, negative_prompt, int(document.tags['steps']), generator)
    del stage_1
    clean_memory()
    preview_image = pt_to_pil(image)[0]
    callback(Document(tags={'progress': 1 / 3}).load_pil_image_to_datauri(preview_image))
    stage_2 = get_stage(hf_token, 2)
    image = stage_2(image, prompt_embeds, negative_embeds, generator)
    del stage_2
    clean_memory()
    preview_image = pt_to_pil(image)[0]
    callback(Document(tags={'progress': 2 / 3}).load_pil_image_to_datauri(preview_image))
    stage_3 = get_stage(hf_token, 3)
    image = stage_3(image, prompt, noise_level, generator)
    callback(Document(tags={'progress': 1}).load_pil_image_to_datauri(image))
    del stage_3
    clean_memory()
