from jina import Executor, requests, DocumentArray, Document

import torch
import math
from PIL import Image
from io import BytesIO
from diffusers import StableDiffusionUpscalePipeline
from io import BytesIO
import numpy as np
import base64

def get_pipe(hf_auth_token):
    # load model and scheduler
    model_id = "stabilityai/stable-diffusion-x4-upscaler"
    pipe = StableDiffusionUpscalePipeline.from_pretrained(model_id, revision="fp16", torch_dtype=torch.float16)
    pipe = pipe.to("cuda")
    pipe.enable_attention_slicing()
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

def add_overlap_rect(rect: [int], overlap: int, image_size: [int]):
    rect = list(rect)
    if rect[0] > 0:
        rect[0] -= overlap
    if rect[1] > 0:
        rect[1] -= overlap
    if rect[2] < image_size[0]:
        rect[2] += overlap
    if rect[3] < image_size[1]:
        rect[3] += overlap
    return rect

def squeeze_tile(tile, original_image, tile_size):
    result = Image.new('RGB', (tile_size * 2, tile_size))
    result.paste(original_image.resize((tile_size, tile_size), Image.BICUBIC), (0, 0))
    result.paste(tile.resize((tile_size, tile_size), Image.BICUBIC), (tile_size, 0))
    return result

def unsqueeze_tile(tile):
    half_crop_rect = (int(tile.size[0] / 2), 0, tile.size[0], tile.size[1])
    tile = tile.crop(half_crop_rect)
    return tile

def process_tile(pipe, prompt, x, y, tile_size, tile_border, image, final_image):
    generator = torch.manual_seed(1)
    crop_rect = (min(image.size[0] - tile_size, x * tile_size), min(image.size[1] - tile_size, y * tile_size), min(image.size[0], (x + 1) * tile_size), min(image.size[1], (y + 1) * tile_size))
    crop_rect_with_overlap = add_overlap_rect(crop_rect, tile_border, image.size)
    tile = image.crop(crop_rect_with_overlap)
    to_input = squeeze_tile(tile, image, tile_size)
    upscaled_tile = pipe(prompt=prompt, image=to_input).images[0]
    upscaled_tile.save(f"tile_{x}_{y}.png")
    upscaled_tile = unsqueeze_tile(upscaled_tile)
    upscaled_tile = upscaled_tile.resize((tile.size[0] * 4, tile.size[1] * 4), Image.BICUBIC)
    # upscaled_tile  = tile.resize((tile.size[0] * 4, tile.size[1] * 4), Image.BICUBIC)
    remove_borders = []
    if x == 0:
        remove_borders.append("l")
    elif crop_rect[2] == image.size[0]:
        remove_borders.append("r")
    if y == 0:
        remove_borders.append("t")
    elif crop_rect[3] == image.size[1]:
        remove_borders.append("b")
    transparency_mask = Image.fromarray(make_transparency_mask((upscaled_tile.size[0], upscaled_tile.size[1]), tile_border, remove_borders=remove_borders), mode="L")
    print("transparency_mask", transparency_mask.size)
    print("upscaled_tile", upscaled_tile.size)
    print("remove_borders", remove_borders)
    final_image.paste(upscaled_tile, (crop_rect_with_overlap[0] * 4, crop_rect_with_overlap[1] * 4), transparency_mask)

def on_document(document, callback):
    if global_object['pipe'] is None:
        global_object['pipe'] = get_pipe(document.tags['_hf_auth_token'])
    pipe = global_object['pipe']
    prompt = document.tags['prompt']
    image = Image.open(BytesIO(base64.b64decode(document.tags['image'])))

    final_image = Image.new('RGB', (image.size[0] * 4, image.size[1] * 4))
    tile_size = 128
    tile_border = 32
    tcx = image.size[0] // tile_size
    tcy = image.size[1] // tile_size
    total_tile_count = tcx * tcy
    current_count = 0
    for y in range(tcy):
        for x in range(tcx):
            process_tile(pipe, prompt, x, y, tile_size, tile_border, image, final_image)
            current_count += 1
            callback(Document(tags={'progress': current_count / total_tile_count}).load_pil_image_to_datauri(final_image))