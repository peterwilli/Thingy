from jina import Executor, requests, DocumentArray, Document
from rembg import remove
from io import BytesIO
import base64
from PIL import Image, ImageOps

def get_image(document, tag):
    image = Image.open(BytesIO(base64.b64decode(document.tags[tag])))
    image = ImageOps.exif_transpose(image)
    image = image.convert("RGB")
    return image

def on_document(document, callback):
    input = get_image(document, 'image')
    output = remove(input)
    callback(Document().load_pil_image_to_datauri(output))