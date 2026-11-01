#!/usr/bin/env python3
"""Prepares the mascot resources.

* If overlay/.../drawable-nodpi/furina_mascot.jpg exists it is used as is.
* Otherwise a simple generated placeholder is written so the build never depends on a missing image.
Then builds the adaptive-icon foreground (432x432, transparent padding) from the mascot.
"""
import pathlib, sys
from PIL import Image, ImageDraw

root = pathlib.Path(sys.argv[1])
res = root / "app/src/main/res/drawable-nodpi"
res.mkdir(parents=True, exist_ok=True)
mascot = res / "furina_mascot.jpg"

if not mascot.exists():
    img = Image.new("RGB", (160, 256), (255, 255, 255))
    d = ImageDraw.Draw(img)
    d.ellipse((30, 24, 130, 124), fill=(0, 162, 255))          # hydro blue head
    d.polygon([(40, 36), (60, 6), (80, 30), (100, 6), (120, 36)], fill=(255, 215, 0))  # gold crown
    d.rounded_rectangle((38, 124, 122, 236), radius=18, fill=(30, 144, 255))  # dress
    d.rectangle((60, 136, 100, 150), fill=(240, 248, 255))
    img.save(mascot, "JPEG", quality=90)
    print("placeholder mascot generated")

src = Image.open(mascot).convert("RGBA")
canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
height = 264  # 66dp safe zone at xxxhdpi
width = round(src.width * height / src.height)
art = src.resize((width, height), Image.LANCZOS)
canvas.paste(art, ((432 - width) // 2, (432 - height) // 2), art)
canvas.save(res / "ic_launcher_foreground_furina.png")
print("icon written", art.size)
