#hydrolane->deps:diffusers, transformers, Pillow
from jina import Executor, requests, DocumentArray, Document

from diffusers import DiffusionPipeline
import torch
import math
import gc
import pickle
from PIL import Image


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
    stage_1 = DiffusionPipeline.from_pretrained(
        get_pretrained_path_safe("DeepFloyd/IF-I-XL-v1.0"),
        text_encoder=None,
        use_auth_token=hf_auth_token,
        device_map="auto",
        torch_dtype=torch.float16,
        variant="fp16"
    )

    safety_modules = {"feature_extractor": stage_1.feature_extractor, "safety_checker": stage_1.safety_checker, "watermarker": stage_1.watermarker}
    stage_3 = DiffusionPipeline.from_pretrained(get_pretrained_path_safe("stabilityai/stable-diffusion-x4-upscaler"), **safety_modules, torch_dtype=torch.float16, use_auth_token=hf_auth_token)
    stage_3.enable_model_cpu_offload()
    del stage_1
    clean_memory()
    return stage_3

dummy = Image.new(mode="RGB", size=(64, 64))
worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        generator = torch.manual_seed(int(document.tags['seed']))
        hf_token = document.tags['_hf_auth_token']
        if global_object['pipe'] is None:
            global_object['pipe'] = get_stage(hf_token)
        stage = global_object['pipe']
        data = pickle.loads(document.blob)
        image = stage(prompt=data['prompt'], image=data['image'], generator=generator, noise_level=100).images
        worker.set_progress(document.id.decode('ascii'), Document().load_pil_image_to_datauri(image[0]), 1)