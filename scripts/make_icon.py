#!/usr/bin/env python3
"""Builds the adaptive-icon foreground (432x432, transparent padding) from the mascot image."""
import pathlib, sys
from PIL import Image

root = pathlib.Path(sys.argv[1])
res = root / "app/src/main/res/drawable-nodpi"
src = Image.open(res / "furina_mascot.jpg").convert("RGBA")
canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
height = 264  # 66dp safe zone at xxxhdpi
width = round(src.width * height / src.height)
art = src.resize((width, height), Image.LANCZOS)
canvas.paste(art, ((432 - width) // 2, (432 - height) // 2), art)
canvas.save(res / "ic_launcher_foreground_furina.png")
print("icon written", art.size)
