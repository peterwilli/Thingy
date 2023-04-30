from bark import SAMPLE_RATE, generate_audio, preload_models
from IPython.display import Audio
import torch
import emoji
import re
from jina import Executor, requests, DocumentArray, Document
import numpy as np
from pydub import AudioSegment
import base64
import io

global_object = {
    'pipe': None
}


def replace_emoji(text):
    # Define a regular expression pattern to match :emoji: syntax
    pattern = re.compile(r':([^:]+):')

    # Use the sub() method to replace all occurrences of :emoji: with [text]
    result = pattern.sub(r'[\1]', text)

    return result


def tts(prompt):
    prompt = emoji.demojize(prompt)
    prompt = replace_emoji(prompt)
    print(f"generate: {prompt}")
    audio_array = generate_audio(prompt)
    return audio_array


def on_document(document, callback):
    generator = torch.manual_seed(int(document.tags['seed']))
    if global_object['pipe'] is None:
        global_object['pipe'] = True
        preload_models()
    audio_array = tts(document.tags['prompt'])
    # convert audio array to 16-bit int representation
    int_audio_arr = (audio_array * np.iinfo(np.int16).max).astype(np.int16)
    audio_segment = AudioSegment(
        int_audio_arr.tobytes(),
        frame_rate=SAMPLE_RATE,
        sample_width=int_audio_arr.dtype.itemsize,
        channels=1,
    )
    audio_bytes = audio_segment.export(format='mp3').read()
    base64_audio = base64.b64encode(audio_bytes).decode('utf-8')
    datauri = f'data:audio/mp3;base64,{base64_audio}'
    result = Document(uri=datauri, tags={'progress': 1.0})
    callback(result)
