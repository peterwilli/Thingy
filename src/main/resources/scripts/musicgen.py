#hydrolane->deps:diffusers, emoji, git+https://github.com/huggingface/transformers.git@9a5d468ba0562e2d5edf9da787881fa227132bca, sentencepiece, pydub
from jina import Executor, requests, DocumentArray, Document

import torchaudio
import torch
from io import BytesIO
from pydub import AudioSegment
from audiocraft.models import MusicGen
import re
import numpy as np
import emoji
import base64

global_object = {
    'pipe': None
}

def get_stage():
    model = MusicGen.get_pretrained('melody')
    return model

def replace_emoji(text):
    # Define a regular expression pattern to match :emoji: syntax
    pattern = re.compile(r':([^:]+):')
    # Use the sub() method to replace all occurrences of :emoji: with [text]
    result = pattern.sub(r'[\1]', text)
    return result

worker = ThingyWorker()
while True:
    bucket = worker.get_current_bucket()
    for document in bucket:
        generator = torch.manual_seed(int(document.tags['seed']))
        if global_object['pipe'] is None:
            global_object['pipe'] = get_stage()
        model = global_object['pipe']
        duration = int(document.tags['duration'])
        model.set_generation_params(duration=duration)
        prompt = document.tags['prompt']
        prompt = emoji.demojize(prompt)
        prompt = replace_emoji(prompt)


        audio_values = None
        if 'source_audio' in document.tags:
            bytes = BytesIO(base64.b64decode(document.tags['source_audio']))
            waveform, sample_rate = torchaudio.load(bytes)
            audio_values = model.generate_with_chroma([prompt], waveform[None].expand(1, -1, -1), sample_rate)
        else:
            audio_values = model.generate([prompt])

        # Data needed for conversion
        audio_values = audio_values[0, ...].cpu().numpy()
        sampling_rate = model.sample_rate
        # Convert audio array to 16-bit int representation
        audio_values = (audio_values * np.iinfo(np.int16).max).astype(np.int16)
        audio_segment = AudioSegment(
            audio_values.tobytes(),
            frame_rate=sampling_rate,
            sample_width=audio_values.dtype.itemsize,
            channels=1  # Assuming mono audio
        )
        audio_bytes = audio_segment.export(format='mp3').read()
        base64_audio = base64.b64encode(audio_bytes).decode('utf-8')
        datauri = f'data:audio/mp3;base64,{base64_audio}'
        worker.set_progress(document.id.decode('ascii'), Document(uri=datauri), 1)