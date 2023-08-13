#hydrolane->deps:diffusers, transformers
from jina import Executor, requests, DocumentArray, Document

from diffusers import DiffusionPipeline

import torch
import base64
import json
import pickle
from PIL import Image, ImageOps
from io import BytesIO

def get_image(document, tag):
    image = Image.open(BytesIO(base64.b64decode(document.tags[tag])))
    image = ImageOps.exif_transpose(image)
    image = image.convert("RGB")
    return image

def get_pipe(hf_auth_token):
    pipe = DiffusionPipeline.from_pretrained(
        get_pretrained_path_safe("kandinsky-community/kandinsky-2-2-prior"), torch_dtype=torch.float16
    )
    def dummy(images, **kwargs):
        return images, [False] * len(images)
    pipe.safety_checker = dummy
    pipe.to("cuda")
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
        try:
            generator = torch.manual_seed(int(document.tags['seed']))
            if global_object['pipe'] is None:
                global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
            pipe = global_object['pipe']
            task = document.tags['task']
            data = {}
            print("task:", task)
            if task == "prompt":
                image_embeds, negative_image_embeds = pipe(document.tags['prompt'], negative_prompt=document.tags['negative_prompt'], guidance_scale=document.tags["guidance_scale"], num_inference_steps=int(document.tags['steps'])).to_tuple()
                data['image_embeds'] = image_embeds
                data['negative_image_embeds'] = negative_image_embeds
            elif task == "interpolate":
                input_context = []
                for i in range(4):
                    key = f"fuse_image_{i + 1}"
                    if key in document.tags:
                        img = get_image(document, key)
                        input_context.append(img)
                if 'prompt' in document.tags:
                    input_context.append(document.tags['prompt'])
                if 'weights' in document.tags:
                    weights = document.tags['weights']
                    weights = weights.split(",")
                    weights = [float(w.strip()) for w in weights]
                    if len(weights) < len(input_context):
                        for i in range(len(input_context) - len(weights)):
                            weights.append(1 / len(input_context))
                else:
                    weights = [1 / len(input_context) for _ in range(len(input_context))]
                print("weights:", weights)
                image_embeds, negative_image_embeds = pipe.interpolate(input_context, weights, negative_prompt=document.tags['negative_prompt'], guidance_scale=document.tags["guidance_scale"], num_inference_steps=int(document.tags['steps'])).to_tuple()
                data['image_embeds'] = image_embeds
                data['negative_image_embeds'] = negative_image_embeds
            data_blob = pickle.dumps(data)
            worker.set_progress(document.id.decode('ascii'), Document(blob=data_blob), 1)
        except:
            print("error")