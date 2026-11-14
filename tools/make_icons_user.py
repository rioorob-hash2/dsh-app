"""Launcher icons from БРАТИК's own artwork (art/icon-source.jpg)."""

import os

from PIL import Image, ImageDraw

PROJ = r"C:\Users\rioor\Documents\DeepSeek Harness\DshApp"
SRC = os.path.join(PROJ, "art", "icon-source.jpg")
OUT = os.path.join(PROJ, "app", "src", "main", "res")
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

src = Image.open(SRC).convert("RGBA")
side = min(src.size)
left = (src.width - side) // 2
top = (src.height - side) // 2
square = src.crop((left, top, left + side, top + side))

for folder, size in SIZES.items():
    target = os.path.join(OUT, folder)
    os.makedirs(target, exist_ok=True)
    icon = square.resize((size, size), Image.LANCZOS)
    icon.save(os.path.join(target, "ic_launcher.png"))
    round_icon = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    round_icon.paste(icon, (0, 0), mask)
    round_icon.save(os.path.join(target, "ic_launcher_round.png"))

# store a store-sized copy for the report
square.resize((512, 512), Image.LANCZOS).save(os.path.join(PROJ, "art", "icon-512.png"))
print("icons from user artwork:", ", ".join(SIZES))
