from jina import Executor, requests, DocumentArray, Document

from diffusers import DPMSolverMultistepScheduler
from diffusers import StableDiffusionPipeline
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
    repo_id = "stabilityai/stable-diffusion-2-1-base"
    device = "cuda"
    tokenizer = CLIPTokenizer.from_pretrained(
        repo_id,
        subfolder="tokenizer",
    )
    text_encoder = CLIPTextModel.from_pretrained(
        repo_id, subfolder="text_encoder", torch_dtype=torch.float16
    )
    pipe = StableDiffusionPipeline.from_pretrained(repo_id, torch_dtype=torch.float16, revision="fp16", use_auth_token=hf_auth_token, text_encoder=text_encoder, tokenizer=tokenizer)
    pipe.scheduler = DPMSolverMultistepScheduler.from_config(pipe.scheduler.config)
    pipe = pipe.to(device)
    return pipe

def next_divisible(n, d):
    divisor = n % d
    return n - divisor

global_object = {
    'pipe': None
}

def load_learned_embed_in_clip(learned_embeds_path, text_encoder, tokenizer):
    loaded_learned_embeds = torch.load(learned_embeds_path, map_location="cpu")

    # separate token and the embeds
    token = list(loaded_learned_embeds.keys())[0]
    embeds = loaded_learned_embeds[token]

    # cast to dtype of text_encoder
    dtype = text_encoder.get_input_embeddings().weight.dtype
    embeds = embeds.to(dtype)

    # add the token in tokenizer
    num_added_tokens = tokenizer.add_tokens(token)
    if num_added_tokens == 0:
        print(f"Warning: Token {token} already exists! Will replace this token in-memory!")

    # resize the token embeddings
    text_encoder.resize_token_embeddings(len(tokenizer))

    # get the id for the token and assign the embeds
    token_id = tokenizer.convert_tokens_to_ids(token)
    text_encoder.get_input_embeddings().weight.data[token_id] = embeds

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
        negative_prompt = document.tags['negative_prompt']
        ar = document.tags['ar'].split(":")
        width, height = calculate_size(base_size, int(ar[0]), int(ar[1]))
        width = next_divisible(width, 8)
        height = next_divisible(height, 8)
        print(f"width: {width} height: {height}")

        with tempfile.TemporaryDirectory() as temp_dir:
            for idx, embed_base64 in enumerate(embeds):
                embed_path = os.path.join(temp_dir, f"embed_{idx + 1}.bin")
                with open(embed_path, 'wb') as f:
                    f.write(base64.b64decode(embed_base64))
                load_learned_embed_in_clip(embed_path, pipe.text_encoder, pipe.tokenizer)

        image = pipe(prompt, negative_prompt=negative_prompt, width = width, height = height, guidance_scale=document.tags["guidance_scale"], num_inference_steps=int(document.tags['steps'])).images[0]
        worker.set_progress(document.id.decode('ascii'), Document().load_pil_image_to_datauri(image), 1)