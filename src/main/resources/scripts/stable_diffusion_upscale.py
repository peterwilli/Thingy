#hydrolane->deps:diffusers, transformers, Pillow
from jina import Executor, requests, DocumentArray, Document

import torch
import math
from PIL import Image
from diffusers import StableDiffusionUpscalePipeline
from io import BytesIO
import numpy as np
import base64

def get_pipe(hf_auth_token):
    # load model and scheduler
    model_id = "stabilityai/stable-diffusion-x4-upscaler"
    pipe = StableDiffusionUpscalePipeline.from_pretrained(model_id, revision="fp16", torch_dtype=torch.float16)
    pipe = pipe.to("cuda")
    # pipe.enable_attention_slicing()
    return pipe

global_object = {
    'pipe': None
}

def make_transparency_mask(size, overlap_pixels, remove_borders = []):
    size_x = size[0] - overlap_pixels * 2
    size_y = size[1] - overlap_pixels * 2
    for letter in ['l', 'r']:
        if letter in remove_borders:
            size_x += overlap_pixels
    for letter in ['t', 'b']:
        if letter in remove_borders:
            size_y += overlap_pixels
    mask = np.ones((size_y, size_x), dtype=np.uint8) * 255
    mask = np.pad(mask, mode = 'linear_ramp', pad_width=overlap_pixels, end_values = 0)

    if 'l' in remove_borders:
        mask = mask[:, overlap_pixels:mask.shape[1]]
    if 'r' in remove_borders:
        mask = mask[:, 0:mask.shape[1] - overlap_pixels]
    if 't' in remove_borders:
        mask = mask[overlap_pixels:mask.shape[0], :]
    if 'b' in remove_borders:
        mask = mask[0:mask.shape[0] - overlap_pixels, :]
    return mask

def clamp(n, smallest, largest):
    return max(smallest, min(n, largest))

def clamp_rect(rect: [int], min: [int], max: [int]):
    return (
        clamp(rect[0], min[0], max[0]),
        clamp(rect[1], min[1], max[1]),
        clamp(rect[2], min[0], max[0]),
        clamp(rect[3], min[1], max[1])
    )

def add_overlap_rect(rect: [int], overlap: int, image_size: [int]):
    rect = list(rect)
    rect[0] -= overlap
    rect[1] -= overlap
    rect[2] += overlap
    rect[3] += overlap
    rect = clamp_rect(rect, [0, 0], [image_size[0], image_size[1]])
    return rect

def squeeze_tile(tile, original_image, original_slice, slice_x):
    result = Image.new('RGB', (tile.size[0] + original_slice, tile.size[1]))
    result.paste(original_image.resize((tile.size[0], tile.size[1]), Image.BICUBIC).crop((slice_x, 0, slice_x + original_slice, tile.size[1])), (0, 0))
    result.paste(tile, (original_slice, 0))
    return result

def unsqueeze_tile(tile, original_image_slice):
    crop_rect = (original_image_slice * 4, 0, tile.size[0], tile.size[1])
    tile = tile.crop(crop_rect)
    return tile

def next_divisible(n, d):
    divisor = n % d
    return n - divisor

def process_tile(pipe, seed, prompt, noise_level, guidance_scale, original_image_slice, x, y, tile_size, tile_border, image, final_image):
    torch.manual_seed(seed)
    crop_rect = (min(image.size[0] - tile_size, x * tile_size),
                 min(image.size[1] - tile_size, y * tile_size),
                 min(image.size[0], (x + 1) * tile_size),
                 min(image.size[1], (y + 1) * tile_size))
    crop_rect_with_overlap = add_overlap_rect(crop_rect, tile_border, image.size)
    tile = image.crop(crop_rect_with_overlap)
    translated_slice_x = ((crop_rect[0] + ((crop_rect[2] - crop_rect[0]) / 2)) / image.size[0]) * tile.size[0]
    translated_slice_x = translated_slice_x - (original_image_slice / 2)
    translated_slice_x = max(0, translated_slice_x)
    # translated_slice_x = random.randint(0, image.size[0] - original_image_slice)
    to_input = squeeze_tile(tile, image, original_image_slice, translated_slice_x)
    orig_input_size = to_input.size
    to_input = to_input.resize((tile_size, tile_size), Image.BICUBIC)
    upscaled_tile = pipe(prompt=prompt, image=to_input, guidance_scale=guidance_scale, noise_level=noise_level).images[0]
    upscaled_tile = upscaled_tile.resize((orig_input_size[0] * 4, orig_input_size[1] * 4), Image.BICUBIC)
    #upscaled_tile  = tile.resize((tile.size[0] * 4, tile.size[1] * 4), Image.BICUBIC)
    upscaled_tile = unsqueeze_tile(upscaled_tile, original_image_slice)
    upscaled_tile = upscaled_tile.resize((tile.size[0] * 4, tile.size[1] * 4), Image.BICUBIC)
    remove_borders = []
    if x == 0:
        remove_borders.append("l")
    elif crop_rect[2] == image.size[0]:
        remove_borders.append("r")
    if y == 0:
        remove_borders.append("t")
    elif crop_rect[3] == image.size[1]:
        remove_borders.append("b")
    transparency_mask = Image.fromarray(make_transparency_mask((upscaled_tile.size[0], upscaled_tile.size[1]), tile_border * 4, remove_borders=remove_borders), mode="L")
    final_image.paste(upscaled_tile, (crop_rect_with_overlap[0] * 4, crop_rect_with_overlap[1] * 4), transparency_mask)

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        if global_object['pipe'] is None:
            global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
        pipe = global_object['pipe']
        prompt = document.tags['prompt']
        noise_level = document.tags['noise_level']
        seed = int(document.tags['seed'])
        guidance_scale = document.tags['guidance_scale']
        original_image_slice = int(document.tags['original_image_slice'])
        tile_border = int(document.tags['tile_border'])
        image = Image.open(BytesIO(base64.b64decode(document.tags['image'])))

        final_image = Image.new('RGB', (image.size[0] * 4, image.size[1] * 4))
        tile_size = 128
        tcx = math.ceil(image.size[0] / tile_size)
        tcy = math.ceil(image.size[1] / tile_size)
        total_tile_count = tcx * tcy
        current_count = 0
        for y in range(tcy):
            for x in range(tcx):
                process_tile(pipe, seed, prompt, noise_level, guidance_scale, original_image_slice, x, y, tile_size, tile_border, image, final_image)
                current_count += 1
                worker.set_progress(document.id.decode('ascii'), Document().load_pil_image_to_datauri(final_image), current_count / total_tile_count)
                if worker.is_canceled(document.id.decode('ascii')):
                    break
            if worker.is_canceled(document.id.decode('ascii')):
                break