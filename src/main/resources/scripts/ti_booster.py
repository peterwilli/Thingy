import base64
import os
import tempfile
from io import BytesIO
from PIL import Image

from jina import Executor, requests, DocumentArray, Document
import subprocess
import sys
import re
import base64


def pip_install(package):
    subprocess.check_call([sys.executable, "-m", "pip", "install", package])


global_object = {
    'installed_leap': False
}

re_steps = re.compile(r"progress:([\d|.]+)")

# Temporary shitty workaround to get unbuffered outputs and a custom print() for processing
# I felt this was less ugly than just using patch
booster_command = r"""
#!/usr/bin/env python3
#Bootstrapped from: https://github.com/huggingface/diffusers/blob/main/examples/textual_inversion/textual_inversion.py
import argparse
import logging
import math
import os
import random
from pathlib import Path
from typing import Optional

import numpy as np
import torch
import torch.nn.functional as F
import torch.utils.checkpoint
from torch.utils.data import Dataset

import datasets
import diffusers
import PIL
import transformers
from accelerate import Accelerator
from accelerate.logging import get_logger
from accelerate.utils import set_seed
from diffusers import AutoencoderKL, DDPMScheduler, StableDiffusionPipeline, UNet2DConditionModel
from diffusers.optimization import get_scheduler
from diffusers.utils import check_min_version
from diffusers.utils.import_utils import is_xformers_available
from huggingface_hub import HfFolder, Repository, whoami

# TODO: remove and import from diffusers.utils when the new version of diffusers is released
from packaging import version
from torchvision import transforms
import torchvision
from tqdm.auto import tqdm
from transformers import CLIPTextModel, CLIPTokenizer
import leap_sd
from PIL import Image, ImageOps

if version.parse(version.parse(PIL.__version__).base_version) >= version.parse("9.1.0"):
    PIL_INTERPOLATION = {
        "linear": PIL.Image.Resampling.BILINEAR,
        "bilinear": PIL.Image.Resampling.BILINEAR,
        "bicubic": PIL.Image.Resampling.BICUBIC,
        "lanczos": PIL.Image.Resampling.LANCZOS,
        "nearest": PIL.Image.Resampling.NEAREST,
    }
else:
    PIL_INTERPOLATION = {
        "linear": PIL.Image.LINEAR,
        "bilinear": PIL.Image.BILINEAR,
        "bicubic": PIL.Image.BICUBIC,
        "lanczos": PIL.Image.LANCZOS,
        "nearest": PIL.Image.NEAREST,
    }
# ------------------------------------------------------------------------------


# Will error if the minimal version of diffusers is not installed. Remove at your own risks.
check_min_version("0.10.0.dev0")


logger = get_logger(__name__)


def save_progress(text_encoder, placeholder_token_id, accelerator, args, save_path):
    logger.info("Saving embeddings")
    learned_embeds = accelerator.unwrap_model(text_encoder).get_input_embeddings().weight[placeholder_token_id]
    learned_embeds_dict = {args.placeholder_token: learned_embeds.detach().cpu()}
    torch.save(learned_embeds_dict, save_path)


def parse_args():
    parser = argparse.ArgumentParser(description="Simple example of a training script.")
    parser.add_argument(
        "--save_steps",
        type=int,
        default=500,
        help="Save learned_embeds.bin every X updates steps.",
    )
    parser.add_argument(
        "--only_save_embeds",
        action="store_true",
        default=False,
        help="Save only the embeddings for the new concept.",
    )
    parser.add_argument(
        "--pretrained_model_name_or_path",
        type=str,
        default=None,
        required=True,
        help="Path to pretrained model or model identifier from huggingface.co/models.",
    )
    parser.add_argument(
        "--revision",
        type=str,
        default=None,
        required=False,
        help="Revision of pretrained model identifier from huggingface.co/models.",
    )
    parser.add_argument(
        "--tokenizer_name",
        type=str,
        default=None,
        help="Pretrained tokenizer name or path if not the same as model_name",
    )
    parser.add_argument(
        "--train_data_dir", type=str, default=None, required=True, help="A folder containing the training data."
    )
    parser.add_argument(
        "--placeholder_token",
        type=str,
        default=None,
        required=True,
        help="A token to use as a placeholder for the concept.",
    )
    parser.add_argument("--learnable_property", type=str, default="object", help="Choose between 'object' and 'style'")
    parser.add_argument("--repeats", type=int, default=100, help="How many times to repeat the training data.")
    parser.add_argument(
        "--output_dir",
        type=str,
        default="text-inversion-model",
        help="The output directory where the model predictions and checkpoints will be written.",
    )
    parser.add_argument("--seed", type=int, default=None, help="A seed for reproducible training.")
    parser.add_argument(
        "--resolution",
        type=int,
        default=512,
        help=(
            "The resolution for input images, all the images in the train/validation dataset will be resized to this"
            " resolution"
        ),
    )
    parser.add_argument(
        "--center_crop", action="store_true", help="Whether to center crop images before resizing to resolution"
    )
    parser.add_argument(
        "--train_batch_size", type=int, default=1, help="Batch size (per device) for the training dataloader."
    )
    parser.add_argument("--num_train_epochs", type=int, default=100)
    parser.add_argument(
        "--max_train_steps",
        type=int,
        default=100,
        help="Total number of training steps to perform.  If provided, overrides num_train_epochs.",
    )
    parser.add_argument(
        "--gradient_accumulation_steps",
        type=int,
        default=4,
        help="Number of updates steps to accumulate before performing a backward/update pass.",
    )
    parser.add_argument(
        "--gradient_checkpointing",
        action="store_true",
        help="Whether or not to use gradient checkpointing to save memory at the expense of slower backward pass.",
    )
    parser.add_argument(
        "--learning_rate",
        type=float,
        default=2e-3,
        help="Initial learning rate (after the potential warmup period) to use.",
    )
    parser.add_argument(
        "--scale_lr",
        action="store_true",
        default=True,
        help="Scale the learning rate by the number of GPUs, gradient accumulation steps, and batch size.",
    )
    parser.add_argument(
        "--lr_scheduler",
        type=str,
        default="cosine",
        help=(
            'The scheduler type to use. Choose between ["linear", "cosine", "cosine_with_restarts", "polynomial",'
            ' "constant", "constant_with_warmup"]'
        ),
    )
    parser.add_argument(
        "--lr_warmup_steps", type=int, default=20, help="Number of steps for the warmup in the lr scheduler."
    )
    parser.add_argument("--adam_beta1", type=float, default=0.9, help="The beta1 parameter for the Adam optimizer.")
    parser.add_argument("--adam_beta2", type=float, default=0.999, help="The beta2 parameter for the Adam optimizer.")
    parser.add_argument("--adam_weight_decay", type=float, default=1e-2, help="Weight decay to use.")
    parser.add_argument("--adam_epsilon", type=float, default=1e-08, help="Epsilon value for the Adam optimizer")
    parser.add_argument("--push_to_hub", action="store_true", help="Whether or not to push the model to the Hub.")
    parser.add_argument("--hub_token", type=str, default=None, help="The token to use to push to the Model Hub.")
    parser.add_argument(
        "--hub_model_id",
        type=str,
        default=None,
        help="The name of the repository to keep in sync with the local `output_dir`.",
    )
    parser.add_argument(
        "--logging_dir",
        type=str,
        default="logs",
        help=(
            "[TensorBoard](https://www.tensorflow.org/tensorboard) log directory. Will default to"
            " *output_dir/runs/**CURRENT_DATETIME_HOSTNAME***."
        ),
    )
    parser.add_argument(
        "--mixed_precision",
        type=str,
        default="no",
        choices=["no", "fp16", "bf16"],
        help=(
            "Whether to use mixed precision. Choose"
            "between fp16 and bf16 (bfloat16). Bf16 requires PyTorch >= 1.10."
            "and an Nvidia Ampere GPU."
        ),
    )
    parser.add_argument(
        "--allow_tf32",
        action="store_true",
        help=(
            "Whether or not to allow TF32 on Ampere GPUs. Can be used to speed up training. For more information, see"
            " https://pytorch.org/docs/stable/notes/cuda.html#tensorfloat-32-tf32-on-ampere-devices"
        ),
    )
    parser.add_argument(
        "--report_to",
        type=str,
        default="tensorboard",
        help=(
            'The integration to report the results and logs to. Supported platforms are `"tensorboard"`'
            ' (default), `"wandb"` and `"comet_ml"`. Use `"all"` to report to all integrations.'
        ),
    )
    parser.add_argument("--local_rank", type=int, default=-1, help="For distributed training: local_rank")
    parser.add_argument(
        "--checkpointing_steps",
        type=int,
        default=500,
        help=(
            "Save a checkpoint of the training state every X updates. These checkpoints are only suitable for resuming"
            " training using `--resume_from_checkpoint`."
        ),
    )
    parser.add_argument(
        "--resume_from_checkpoint",
        type=str,
        default=None,
        help=(
            "Whether training should be resumed from a previous checkpoint. Use a path saved by"
            ' `--checkpointing_steps`, or `"latest"` to automatically select the last available checkpoint.'
        ),
    )
    parser.add_argument(
        "--enable_xformers_memory_efficient_attention", action="store_true", help="Whether or not to use xformers."
    )

    args = parser.parse_args()
    env_local_rank = int(os.environ.get("LOCAL_RANK", -1))
    if env_local_rank != -1 and env_local_rank != args.local_rank:
        args.local_rank = env_local_rank

    if args.train_data_dir is None:
        raise ValueError("You must specify a train data directory.")

    return args


imagenet_templates_small = [
    "a photo of a {}",
    "a rendering of a {}",
    "a cropped photo of the {}",
    "the photo of a {}",
    "a photo of a clean {}",
    "a photo of a dirty {}",
    "a dark photo of the {}",
    "a photo of my {}",
    "a photo of the cool {}",
    "a close-up photo of a {}",
    "a bright photo of the {}",
    "a cropped photo of a {}",
    "a photo of the {}",
    "a good photo of the {}",
    "a photo of one {}",
    "a close-up photo of the {}",
    "a rendition of the {}",
    "a photo of the clean {}",
    "a rendition of a {}",
    "a photo of a nice {}",
    "a good photo of a {}",
    "a photo of the nice {}",
    "a photo of the small {}",
    "a photo of the weird {}",
    "a photo of the large {}",
    "a photo of a cool {}",
    "a photo of a small {}",
]

imagenet_style_templates_small = [
    "a painting in the style of {}",
    "a rendering in the style of {}",
    "a cropped painting in the style of {}",
    "the painting in the style of {}",
    "a clean painting in the style of {}",
    "a dirty painting in the style of {}",
    "a dark painting in the style of {}",
    "a picture in the style of {}",
    "a cool painting in the style of {}",
    "a close-up painting in the style of {}",
    "a bright painting in the style of {}",
    "a cropped painting in the style of {}",
    "a good painting in the style of {}",
    "a close-up painting in the style of {}",
    "a rendition in the style of {}",
    "a nice painting in the style of {}",
    "a small painting in the style of {}",
    "a weird painting in the style of {}",
    "a large painting in the style of {}",
]


class TextualInversionDataset(Dataset):
    def __init__(
        self,
        data_root,
        tokenizer,
        learnable_property="object",  # [object, style]
        size=512,
        repeats=100,
        interpolation="bicubic",
        flip_p=0.5,
        set="train",
        placeholder_token="*",
        center_crop=False,
    ):
        self.data_root = data_root
        self.tokenizer = tokenizer
        self.learnable_property = learnable_property
        self.size = size
        self.placeholder_token = placeholder_token
        self.center_crop = center_crop
        self.flip_p = flip_p

        self.image_paths = [os.path.join(self.data_root, file_path) for file_path in os.listdir(self.data_root)]

        self.num_images = len(self.image_paths)
        self._length = self.num_images

        if set == "train":
            self._length = self.num_images * repeats

        self.interpolation = {
            "linear": PIL_INTERPOLATION["linear"],
            "bilinear": PIL_INTERPOLATION["bilinear"],
            "bicubic": PIL_INTERPOLATION["bicubic"],
            "lanczos": PIL_INTERPOLATION["lanczos"],
        }[interpolation]

        self.templates = imagenet_style_templates_small if learnable_property == "style" else imagenet_templates_small
        self.flip_transform = transforms.RandomHorizontalFlip(p=self.flip_p)

    def __len__(self):
        return self._length

    def __getitem__(self, i):
        example = {}
        image = Image.open(self.image_paths[i % self.num_images])
        image = ImageOps.exif_transpose(image)

        if not image.mode == "RGB":
            image = image.convert("RGB")

        placeholder_string = self.placeholder_token
        text = random.choice(self.templates).format(placeholder_string)

        example["input_ids"] = self.tokenizer(
            text,
            padding="max_length",
            truncation=True,
            max_length=self.tokenizer.model_max_length,
            return_tensors="pt",
        ).input_ids[0]

        # default to score-sde preprocessing
        img = np.array(image).astype(np.uint8)

        if self.center_crop:
            crop = min(img.shape[0], img.shape[1])
            h, w, = (
                img.shape[0],
                img.shape[1],
            )
            img = img[(h - crop) // 2 : (h + crop) // 2, (w - crop) // 2 : (w + crop) // 2]

        image = Image.fromarray(img)
        image = image.resize((self.size, self.size), resample=self.interpolation)

        image = self.flip_transform(image)
        image = np.array(image).astype(np.uint8)
        image = (image / 127.5 - 1.0).astype(np.float32)

        example["pixel_values"] = torch.from_numpy(image).permute(2, 0, 1)
        return example


def get_full_repo_name(model_id: str, organization: Optional[str] = None, token: Optional[str] = None):
    if token is None:
        token = HfFolder.get_token()
    if organization is None:
        username = whoami(token)["name"]
        return f"{username}/{model_id}"
    else:
        return f"{organization}/{model_id}"

@torch.no_grad()
def boost_embed(images_folder):
    # Loading LEAP from checkpoint
    model_path = os.path.abspath(os.path.join(os.path.realpath(leap_sd.__file__), "..", "model.ckpt"))
    leap = leap_sd.LM.load_from_checkpoint(model_path)
    leap.eval()

    def denormalize_embed(embed, mean, std, min_weight, max_weight):
        embed = embed * (abs(min_weight) + max_weight)
        embed = embed - abs(min_weight)
        return embed

    def repeat_array_to_length(arr, length):
        while len(arr) < length:
            arr = arr * 2
        return arr[:length]

    def load_grid(images_path):
        image_names = os.listdir(images_path)
        random.shuffle(image_names)
        image_names = repeat_array_to_length(image_names, 4)
        images = None
        pred_transforms = transforms.Compose(
            [
                transforms.ToTensor(),
                transforms.Normalize((0.5, 0.5, 0.5), (0.5, 0.5, 0.5))
            ]
        )
        for image_name in image_names:
            image = Image.open(os.path.join(images_path, image_name)).convert("RGB")
            image = ImageOps.exif_transpose(image)
            image = image.resize((64, 64))
            image = pred_transforms(np.array(image)).unsqueeze(0)
            if images is None:
                images = image
            else:
                images = torch.cat((images, image), 0)
        grid = torchvision.utils.make_grid(images, nrow = 2, padding = 0)
        return grid

    grid = load_grid(images_folder)
    # Simulate single item batch
    grid = grid.unsqueeze(0)
    embed_model = leap(grid)
    embed_model = embed_model.squeeze()
    embed_model = denormalize_embed(embed_model, mean = 0.527121, std = 0.094522, min_weight = -0.061995748430490494, max_weight = 0.05560213699936867)
    return embed_model

def main():
    args = parse_args()
    logging_dir = os.path.join(args.output_dir, args.logging_dir)

    accelerator = Accelerator(
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        mixed_precision=args.mixed_precision,
        log_with=args.report_to,
        logging_dir=logging_dir,
    )

    # Make one log on every process with the configuration for debugging.
    logging.basicConfig(
        format="%(asctime)s - %(levelname)s - %(name)s - %(message)s",
        datefmt="%m/%d/%Y %H:%M:%S",
        level=logging.INFO,
    )
    logger.info(accelerator.state, main_process_only=False)
    if accelerator.is_local_main_process:
        datasets.utils.logging.set_verbosity_warning()
        transformers.utils.logging.set_verbosity_warning()
        diffusers.utils.logging.set_verbosity_info()
    else:
        datasets.utils.logging.set_verbosity_error()
        transformers.utils.logging.set_verbosity_error()
        diffusers.utils.logging.set_verbosity_error()

    # If passed along, set the training seed now.
    if args.seed is not None:
        set_seed(args.seed)

    # Handle the repository creation
    if accelerator.is_main_process:
        if args.push_to_hub:
            if args.hub_model_id is None:
                repo_name = get_full_repo_name(Path(args.output_dir).name, token=args.hub_token)
            else:
                repo_name = args.hub_model_id
            repo = Repository(args.output_dir, clone_from=repo_name)

            with open(os.path.join(args.output_dir, ".gitignore"), "w+") as gitignore:
                if "step_*" not in gitignore:
                    gitignore.write("step_*\n")
                if "epoch_*" not in gitignore:
                    gitignore.write("epoch_*\n")
        elif args.output_dir is not None:
            os.makedirs(args.output_dir, exist_ok=True)

    # Load tokenizer
    if args.tokenizer_name:
        tokenizer = CLIPTokenizer.from_pretrained(args.tokenizer_name)
    elif args.pretrained_model_name_or_path:
        tokenizer = CLIPTokenizer.from_pretrained(args.pretrained_model_name_or_path, subfolder="tokenizer")

    # Load scheduler and models
    noise_scheduler = DDPMScheduler.from_pretrained(args.pretrained_model_name_or_path, subfolder="scheduler")
    text_encoder = CLIPTextModel.from_pretrained(
        args.pretrained_model_name_or_path, subfolder="text_encoder", revision=args.revision
    )
    vae = AutoencoderKL.from_pretrained(args.pretrained_model_name_or_path, subfolder="vae", revision=args.revision)
    unet = UNet2DConditionModel.from_pretrained(
        args.pretrained_model_name_or_path, subfolder="unet", revision=args.revision
    )

    # Add the placeholder token in tokenizer
    num_added_tokens = tokenizer.add_tokens(args.placeholder_token)
    if num_added_tokens == 0:
        raise ValueError(
            f"The tokenizer already contains the token {args.placeholder_token}. Please pass a different"
            " `placeholder_token` that is not already in the tokenizer."
        )

    placeholder_token_id = tokenizer.convert_tokens_to_ids(args.placeholder_token)

    # Resize the token embeddings as we are adding new special tokens to the tokenizer
    text_encoder.resize_token_embeddings(len(tokenizer))

    # Initialise the newly added placeholder token with the embeddings of the initializer token
    token_embeds = text_encoder.get_input_embeddings().weight.data
    boosted_embed = boost_embed(args.train_data_dir)
    token_embeds[placeholder_token_id] = boosted_embed
    print(f"Successfully boosted embed to {boosted_embed}")

    # Freeze vae and unet
    vae.requires_grad_(False)
    unet.requires_grad_(False)
    # Freeze all parameters except for the token embeddings in text encoder
    text_encoder.text_model.encoder.requires_grad_(False)
    text_encoder.text_model.final_layer_norm.requires_grad_(False)
    text_encoder.text_model.embeddings.position_embedding.requires_grad_(False)

    if args.gradient_checkpointing:
        # Keep unet in train mode if we are using gradient checkpointing to save memory.
        # The dropout cannot be != 0 so it doesn't matter if we are in eval or train mode.
        unet.train()
        text_encoder.gradient_checkpointing_enable()
        unet.enable_gradient_checkpointing()

    if args.enable_xformers_memory_efficient_attention:
        if is_xformers_available():
            unet.enable_xformers_memory_efficient_attention()
        else:
            raise ValueError("xformers is not available. Make sure it is installed correctly")

    # Enable TF32 for faster training on Ampere GPUs,
    # cf https://pytorch.org/docs/stable/notes/cuda.html#tensorfloat-32-tf32-on-ampere-devices
    if args.allow_tf32:
        torch.backends.cuda.matmul.allow_tf32 = True

    if args.scale_lr:
        args.learning_rate = (
            args.learning_rate * args.gradient_accumulation_steps * args.train_batch_size * accelerator.num_processes
        )

    # Initialize the optimizer
    optimizer = torch.optim.AdamW(
        text_encoder.get_input_embeddings().parameters(),  # only optimize the embeddings
        lr=args.learning_rate,
        betas=(args.adam_beta1, args.adam_beta2),
        weight_decay=args.adam_weight_decay,
        eps=args.adam_epsilon,
    )

    # Dataset and DataLoaders creation:
    train_dataset = TextualInversionDataset(
        data_root=args.train_data_dir,
        tokenizer=tokenizer,
        size=args.resolution,
        placeholder_token=args.placeholder_token,
        repeats=args.repeats,
        learnable_property=args.learnable_property,
        center_crop=args.center_crop,
        set="train",
    )
    train_dataloader = torch.utils.data.DataLoader(train_dataset, batch_size=args.train_batch_size, shuffle=True)

    # Scheduler and math around the number of training steps.
    overrode_max_train_steps = False
    num_update_steps_per_epoch = math.ceil(len(train_dataloader) / args.gradient_accumulation_steps)
    if args.max_train_steps is None:
        args.max_train_steps = args.num_train_epochs * num_update_steps_per_epoch
        overrode_max_train_steps = True

    lr_scheduler = get_scheduler(
        args.lr_scheduler,
        optimizer=optimizer,
        num_warmup_steps=args.lr_warmup_steps * args.gradient_accumulation_steps,
        num_training_steps=args.max_train_steps * args.gradient_accumulation_steps,
    )

    # Prepare everything with our `accelerator`.
    text_encoder, optimizer, train_dataloader, lr_scheduler = accelerator.prepare(
        text_encoder, optimizer, train_dataloader, lr_scheduler
    )

    # For mixed precision training we cast the text_encoder and vae weights to half-precision
    # as these models are only used for inference, keeping weights in full precision is not required.
    weight_dtype = torch.float32
    if accelerator.mixed_precision == "fp16":
        weight_dtype = torch.float16
    elif accelerator.mixed_precision == "bf16":
        weight_dtype = torch.bfloat16

    # Move vae and unet to device and cast to weight_dtype
    unet.to(accelerator.device, dtype=weight_dtype)
    vae.to(accelerator.device, dtype=weight_dtype)

    # We need to recalculate our total training steps as the size of the training dataloader may have changed.
    num_update_steps_per_epoch = math.ceil(len(train_dataloader) / args.gradient_accumulation_steps)
    if overrode_max_train_steps:
        args.max_train_steps = args.num_train_epochs * num_update_steps_per_epoch
    # Afterwards we recalculate our number of training epochs
    args.num_train_epochs = math.ceil(args.max_train_steps / num_update_steps_per_epoch)

    # We need to initialize the trackers we use, and also store our configuration.
    # The trackers initializes automatically on the main process.
    if accelerator.is_main_process:
        accelerator.init_trackers("textual_inversion", config=vars(args))

    # Train!
    total_batch_size = args.train_batch_size * accelerator.num_processes * args.gradient_accumulation_steps

    logger.info("***** Running training *****")
    logger.info(f"  Num examples = {len(train_dataset)}")
    logger.info(f"  Num Epochs = {args.num_train_epochs}")
    logger.info(f"  Instantaneous batch size per device = {args.train_batch_size}")
    logger.info(f"  Total train batch size (w. parallel, distributed & accumulation) = {total_batch_size}")
    logger.info(f"  Gradient Accumulation steps = {args.gradient_accumulation_steps}")
    logger.info(f"  Total optimization steps = {args.max_train_steps}")
    global_step = 0
    first_epoch = 0

    # Potentially load in the weights and states from a previous save
    if args.resume_from_checkpoint:
        if args.resume_from_checkpoint != "latest":
            path = os.path.basename(args.resume_from_checkpoint)
        else:
            # Get the most recent checkpoint
            dirs = os.listdir(args.output_dir)
            dirs = [d for d in dirs if d.startswith("checkpoint")]
            dirs = sorted(dirs, key=lambda x: int(x.split("-")[1]))
            path = dirs[-1]
        accelerator.print(f"Resuming from checkpoint {path}")
        accelerator.load_state(os.path.join(args.output_dir, path))
        global_step = int(path.split("-")[1])

        resume_global_step = global_step * args.gradient_accumulation_steps
        first_epoch = resume_global_step // num_update_steps_per_epoch
        resume_step = resume_global_step % num_update_steps_per_epoch

    # keep original embeddings as reference
    orig_embeds_params = accelerator.unwrap_model(text_encoder).get_input_embeddings().weight.data.clone()

    for epoch in range(first_epoch, args.num_train_epochs):
        text_encoder.train()
        for step, batch in enumerate(train_dataloader):
            print(f"progress:{global_step / args.max_train_steps:.4f}")
            # Skip steps until we reach the resumed step
            if args.resume_from_checkpoint and epoch == first_epoch and step < resume_step:
                continue

            with accelerator.accumulate(text_encoder):
                # Convert images to latent space
                latents = vae.encode(batch["pixel_values"].to(dtype=weight_dtype)).latent_dist.sample().detach()
                latents = latents * 0.18215
                
                # Sample noise that we'll add to the latents
                noise = torch.randn_like(latents)
                bsz = latents.shape[0]
                
                # Sample a random timestep for each image
                timesteps = torch.randint(0, noise_scheduler.config.num_train_timesteps, (bsz,), device=latents.device)
                timesteps = timesteps.long()

                # Add noise to the latents according to the noise magnitude at each timestep
                # (this is the forward diffusion process)
                noisy_latents = noise_scheduler.add_noise(latents, noise, timesteps)
                
                # Get the text embedding for conditioning
                encoder_hidden_states = text_encoder(batch["input_ids"])[0].to(dtype=weight_dtype)

                # Predict the noise residual
                model_pred = unet(noisy_latents, timesteps, encoder_hidden_states).sample

                # Get the target for loss depending on the prediction type
                if noise_scheduler.config.prediction_type == "epsilon":
                    target = noise
                elif noise_scheduler.config.prediction_type == "v_prediction":
                    target = noise_scheduler.get_velocity(latents, noise, timesteps)
                else:
                    raise ValueError(f"Unknown prediction type {noise_scheduler.config.prediction_type}")

                loss = F.mse_loss(model_pred.float(), target.float(), reduction="mean")

                accelerator.backward(loss)

                optimizer.step()
                lr_scheduler.step()
                optimizer.zero_grad()

                # Let's make sure we don't update any embedding weights besides the newly added token
                index_no_updates = torch.arange(len(tokenizer)) != placeholder_token_id
                with torch.no_grad():
                    accelerator.unwrap_model(text_encoder).get_input_embeddings().weight[
                        index_no_updates
                    ] = orig_embeds_params[index_no_updates]

            # Checks if the accelerator has performed an optimization step behind the scenes
            if accelerator.sync_gradients:
                global_step += 1
                if global_step % args.save_steps == 0:
                    save_path = os.path.join(args.output_dir, f"learned_embeds-steps-{global_step}.bin")
                    save_progress(text_encoder, placeholder_token_id, accelerator, args, save_path)

                if global_step % args.checkpointing_steps == 0:
                    if accelerator.is_main_process:
                        save_path = os.path.join(args.output_dir, f"checkpoint-{global_step}")
                        accelerator.save_state(save_path)
                        logger.info(f"Saved state to {save_path}")

            logs = {"loss": loss.detach().item(), "lr": lr_scheduler.get_last_lr()[0]}
            accelerator.log(logs, step=global_step)

            if global_step >= args.max_train_steps:
                break

    # Create the pipeline using using the trained modules and save it.
    accelerator.wait_for_everyone()
    if accelerator.is_main_process:
        if args.push_to_hub and args.only_save_embeds:
            logger.warn("Enabling full model saving because --push_to_hub=True was specified.")
            save_full_model = True
        else:
            save_full_model = not args.only_save_embeds
        if save_full_model:
            pipeline = StableDiffusionPipeline.from_pretrained(
                args.pretrained_model_name_or_path,
                text_encoder=accelerator.unwrap_model(text_encoder),
                vae=vae,
                unet=unet,
                tokenizer=tokenizer,
            )
            pipeline.save_pretrained(args.output_dir)
        # Save the newly trained embeddings
        save_path = os.path.join(args.output_dir, "learned_embeds.bin")
        save_progress(text_encoder, placeholder_token_id, accelerator, args, save_path)

        if args.push_to_hub:
            repo.push_to_hub(commit_message="End of training", blocking=False, auto_lfs_prune=True)

    accelerator.end_training()

    pipeline = StableDiffusionPipeline.from_pretrained(
        args.pretrained_model_name_or_path,
        text_encoder=accelerator.unwrap_model(text_encoder),
        vae=vae,
        unet=unet,
        tokenizer=tokenizer,
    )
    preview = pipeline(f"{args.placeholder_token}, realistic photo", width = 512, height = 512, guidance_scale=9, num_inference_steps=50).images[0]
    preview_path = os.path.join(args.output_dir, "image_1.jpg")
    preview.save(preview_path)
    


if __name__ == "__main__":
    main()
"""

preview_image_watermark = "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"
preview_image_watermark = base64.b64decode(preview_image_watermark)

def on_document(document, callback):
    if not global_object['installed_leap']:
        pip_install("git+https://github.com/peterwilli/sd-leap-booster.git")
        global_object['installed_leap'] = True
        with open("ti_booster_cmd.py", "w") as f:
            f.write(booster_command)
    word = f"<{document.tags['word']}>"
    steps = int(document.tags['steps'])
    with tempfile.TemporaryDirectory() as temp_dir:
        images_path = os.path.join(temp_dir, "images")
        os.makedirs(images_path, exist_ok=True)
        output_path = os.path.join(temp_dir, "output")
        os.makedirs(output_path, exist_ok=True)

        for i in range(3):
            image_name = f'image_{i + 1}'
            if image_name in document.tags:
                image = Image.open(BytesIO(base64.b64decode(document.tags[image_name])))
                image = image.convert("RGB")
                image.save(os.path.join(images_path, f"{image_name}.jpg"))

        process = subprocess.Popen(['python', '-u', 'ti_booster_cmd.py',
                                    "--pretrained_model_name_or_path=stabilityai/stable-diffusion-2-1-base",
                                    f"--placeholder_token={word}",
                                    f"--train_data_dir={images_path}",
                                    f"--output_dir={output_path}",
                                    f"--max_train_steps={steps}",
                                    f"--only_save_embeds"],
                                   stdout=subprocess.PIPE)

        # Iterate over the output lines
        while True:
            output_bytes = process.stdout.readline()
            # If line is an empty bytes object, it means the process has finished
            if not output_bytes:
                print("Booster Command stopped!")
                break
            line = output_bytes.decode('utf-8').strip()
            print("Line:", line)
            if line.startswith("progress:"):
                match = re_steps.findall(line)
                if match is not None:
                    percentage = float(match[0])
                    callback_obj = {'progress': percentage}
                    callback(Document(tags=callback_obj))

        preview_image_path = os.path.join(output_path, "image_1.jpg")
        preview_image = Image.open(preview_image_path)
        preview_watermark = Image.open(BytesIO(preview_image_watermark))
        preview_image.paste(preview_watermark, (0, 0), preview_watermark)
        with open(os.path.join(output_path, "learned_embeds.bin"), 'rb') as embed_filed:
            file_content = embed_filed.read()
            encoded_content = base64.b64encode(file_content).decode('ascii')
            callback_obj = {'progress': 1.0, 'trained_model': encoded_content}
            callback(Document(tags=callback_obj).load_pil_image_to_datauri(preview_image))
