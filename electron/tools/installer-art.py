"""The installer's and uninstaller's sidebar (NSIS finish pages, 164x314,
24-bit BMP) in the app's own look, instead of electron-builder's default
drawing (derived from Hallym MIPS v2.5.0 electron/tools/installer-art.py,
D-155: this repository's files, this program's name on two lines):

  - the brand's navy, a little lighter towards the bottom;
  - the Hallym University symbol, as it is (colours, proportions and
    elements unchanged: the repository's original
    assets/hallym/logo/symbol-basic.svg, byte for byte Hallym MIPS's), on a
    white plate with clear space around it, since its blue would be lost on
    navy;
  - the program's name in Pretendard Bold, white, centred; on as many lines
    as it needs to fit between the plate's edges ("Hallym Circuit Studio"
    does not fit on one: "Hallym Circuit" / "Studio").  No Korean.

    python3 tools/installer-art.py

Writes packaging/installerSidebar.bmp and packaging/uninstallerSidebar.bmp
(tools/package-config.ts), the same picture.  Needs Pillow and cairosvg.
tests/unit/installer-art.test.ts reads the BMPs back.
"""

import io
import os

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
REPO = os.path.join(ROOT, '..')
SYMBOL = os.path.join(REPO, 'assets/hallym/logo/symbol-basic.svg')
FONT = os.path.join(REPO, 'assets/fonts/pretendard/Pretendard-Bold.otf')
NAME = 'Hallym Circuit Studio'
W, H = 164, 314
NAVY, NAVY_2 = (0, 32, 91), (6, 48, 120)
SCALE = 4  # drawn at 4x, then reduced: smooth edges
PLATE = (18, 40, W - 18, 112)  # the white plate (at 1x)
LINE = 22  # the name's line height (at 1x)


def lines(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, width: float) -> list[str]:
    """The name broken between words so that every line fits in `width`."""
    out: list[str] = []
    for word in text.split():
        if out and draw.textlength(f'{out[-1]} {word}', font=font) <= width:
            out[-1] = f'{out[-1]} {word}'
        else:
            out.append(word)
    return out


def sidebar() -> Image.Image:
    w, h = W * SCALE, H * SCALE
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        c = tuple(round(a + (b - a) * t) for a, b in zip(NAVY, NAVY_2))
        for x in range(w):
            px[x, y] = c
    draw = ImageDraw.Draw(img)
    # The white plate, and the symbol in it with about 13 % of its height clear on every side.
    plate = tuple(v * SCALE for v in PLATE)
    draw.rounded_rectangle(plate, radius=10 * SCALE, fill=(255, 255, 255))
    sym_w = (plate[2] - plate[0]) - 2 * 16 * SCALE
    png = cairosvg.svg2png(url=SYMBOL, output_width=sym_w)  # its own proportions: the width only is given
    sym = Image.open(io.BytesIO(png)).convert('RGBA')
    img.paste(sym, (plate[0] + 16 * SCALE, (plate[1] + plate[3] - sym.height) // 2), sym)
    font = ImageFont.truetype(FONT, 17 * SCALE)
    y = 128 * SCALE
    for line in lines(draw, NAME, font, plate[2] - plate[0]):
        tw = draw.textlength(line, font=font)
        draw.text(((w - tw) / 2, y), line, font=font, fill=(255, 255, 255))
        y += LINE * SCALE
    # A short teal rule under the name (the app's teal, #00A9A5).
    rule = y + 8 * SCALE
    draw.rounded_rectangle(((w - 28 * SCALE) / 2, rule, (w + 28 * SCALE) / 2, rule + 2 * SCALE), radius=SCALE, fill=(0, 169, 165))
    return img.resize((W, H), Image.LANCZOS)


if __name__ == '__main__':
    art = sidebar()
    for name in ('installerSidebar.bmp', 'uninstallerSidebar.bmp'):
        out = os.path.join(ROOT, 'packaging', name)
        art.save(out, 'BMP')
        print(f'{os.path.relpath(out, ROOT)}  {os.path.getsize(out)} bytes')
