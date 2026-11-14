"""Generate the DSH launcher icon (legacy mipmaps) with PIL."""

import os

from PIL import Image, ImageDraw, ImageFont

OUT = r"C:\Users\rioor\Documents\DeepSeek Harness\DshApp\app\src\main\res"
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
BG_TOP = (16, 26, 38)
BG_BOTTOM = (9, 15, 23)
ACCENT = (56, 208, 196)
TEXT = (238, 246, 255)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\segoeuib.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\seguisb.ttf",
]


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make(size: int) -> Image.Image:
    scale = 4
    side = size * scale
    img = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    # vertical gradient background inside a rounded square
    grad = Image.new("RGBA", (1, side))
    for y in range(side):
        t = y / max(1, side - 1)
        grad.putpixel((0, y), (
            int(BG_TOP[0] + (BG_BOTTOM[0] - BG_TOP[0]) * t),
            int(BG_TOP[1] + (BG_BOTTOM[1] - BG_TOP[1]) * t),
            int(BG_TOP[2] + (BG_BOTTOM[2] - BG_TOP[2]) * t),
            255,
        ))
    grad = grad.resize((side, side))
    mask = Image.new("L", (side, side), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, side - 1, side - 1], radius=int(side * 0.24), fill=255
    )
    img.paste(grad, (0, 0), mask)

    draw = ImageDraw.Draw(img)
    # teal ring
    pad = int(side * 0.13)
    width = max(2, int(side * 0.035))
    draw.rounded_rectangle(
        [pad, pad, side - pad, side - pad],
        radius=int(side * 0.18),
        outline=ACCENT,
        width=width,
    )
    # prompt chevron ">" and underscore, like a terminal
    glyph = font(int(side * 0.46))
    bbox = draw.textbbox((0, 0), ">", font=glyph)
    gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(
        ((side - gw) / 2 - side * 0.03, (side - gh) / 2 - bbox[1] - side * 0.02),
        ">",
        font=glyph,
        fill=ACCENT,
    )
    bar_w = int(side * 0.18)
    bar_h = max(2, int(side * 0.045))
    draw.rounded_rectangle(
        [
            side * 0.52,
            side * 0.62,
            side * 0.52 + bar_w,
            side * 0.62 + bar_h,
        ],
        radius=bar_h // 2,
        fill=TEXT,
    )
    return img.resize((size, size), Image.LANCZOS)


for folder, size in SIZES.items():
    target = os.path.join(OUT, folder)
    os.makedirs(target, exist_ok=True)
    icon = make(size)
    icon.save(os.path.join(target, "ic_launcher.png"))
    # круглый вариант: тот же кадр, но с круглой маской
    round_icon = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    m = Image.new("L", icon.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    round_icon.paste(icon, (0, 0), m)
    round_icon.save(os.path.join(target, "ic_launcher_round.png"))

print("icons written:", ", ".join(f"{k}={v}" for k, v in SIZES.items()))
