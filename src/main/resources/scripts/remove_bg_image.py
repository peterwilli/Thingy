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


worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        input = get_image(document, 'image')
        output = remove(input)
        image_box = output.getbbox()
        output = output.crop(image_box)
        worker.set_progress(document.id.decode('ascii'), Document().load_pil_image_to_datauri(output), 1)