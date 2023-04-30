from jina import Executor, requests, DocumentArray, Document

from diffusers import StableDiffusionControlNetPipeline, ControlNetModel, UniPCMultistepScheduler, DPMSolverMultistepScheduler
import torch
import math
import base64
from PIL import Image, ImageOps
from io import BytesIO

def get_pipe(hf_auth_token):
    sd_model_id = "stabilityai/stable-diffusion-2-1"
    controlnet_model_id = "peterwilli/control_instruct_pix2pix_beta_1"
    device = "cuda"

    controlnet = ControlNetModel.from_pretrained(
        controlnet_model_id, torch_dtype=torch.float16
    )
    pipe = StableDiffusionControlNetPipeline.from_pretrained(
        sd_model_id,
        controlnet=controlnet,
        torch_dtype=torch.float16,
    )
    pipe.scheduler = DPMSolverMultistepScheduler.from_config(pipe.scheduler.config)
    pipe = pipe.to(device)
    # pipe.enable_model_cpu_offload()
    return pipe


global_object = {
    'pipe': None
}

def get_image(document, tag):
    image = Image.open(BytesIO(base64.b64decode(document.tags[tag])))
    image = ImageOps.exif_transpose(image)
    image = image.convert("RGB")
    return image

def on_document(document, callback):
    torch.manual_seed(int(document.tags['seed']))
    if global_object['pipe'] is None:
        global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
    pipe = global_object['pipe']
    image = get_image(document, 'image')
    instructions = document.tags['instructions']
    negative_prompt = None
    if 'negative_prompt' in document.tags:
        negative_prompt = document.tags['negative_prompt']
    input_scale = document.tags['input_scale']
    image = pipe(instructions, image=image, controlnet_conditioning_scale=input_scale, negative_prompt=negative_prompt, guidance_scale=document.tags["guidance_scale"], num_inference_steps=int(document.tags['steps'])).images[0]
    callback(Document().load_pil_image_to_datauri(image))