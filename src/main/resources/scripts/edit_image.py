from jina import Executor, requests, DocumentArray, Document

from diffusers import StableDiffusionControlNetPipeline, ControlNetModel, UniPCMultistepScheduler
import torch
import math
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
    sd_model_id = "stabilityai/stable-diffusion-2-1"
    controlnet_model_id = "peterwilli/control_instruct_pix2pix"

    controlnet = ControlNetModel.from_pretrained(
        controlnet_model_id, torch_dtype=torch.float16
    )
    pipe = StableDiffusionControlNetPipeline.from_pretrained(
        sd_model_id,
        controlnet=controlnet,
        torch_dtype=torch.float16,
    )
    pipe.scheduler = UniPCMultistepScheduler.from_config(pipe.scheduler.config)
    # pipe.enable_model_cpu_offload()
    return pipe

def next_divisible(n, d):
    divisor = n % d
    return n - divisor

global_object = {
    'pipe': None
}

def get_image(document, tag):
    image = Image.open(BytesIO(base64.b64decode(document.tags[tag])))
    image = ImageOps.exif_transpose(image)
    image = image.convert("RGB")
    return image

def on_document(document, callback):
    generator = torch.manual_seed(int(document.tags['seed']))
    if global_object['pipe'] is None:
        global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
    pipe = global_object['pipe']
    base_size = 512
    image = get_image(document, 'image')
    instructions = document.tags['instructions']
    negative_prompt = document.tags['negative_prompt']
    image = pipe(instructions, image, negative_prompt=negative_prompt, guidance_scale=document.tags["guidance_scale"], num_inference_steps=int(document.tags['steps'])).images[0]
    callback(Document().load_pil_image_to_datauri(image))