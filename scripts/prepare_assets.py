#!/usr/bin/env python3
"""Wires the mascot drawable. A supplied overlay/.../drawable-nodpi/furina_mascot.jpg is used as is;
without one, furina_mascot is aliased to the original emblem vector so the build never lacks an image."""
import pathlib, sys

root = pathlib.Path(sys.argv[1])
res = root / "app/src/main/res"
mascot = res / "drawable-nodpi/furina_mascot.jpg"

if mascot.exists():
    print("using supplied mascot image")
else:
    (res / "values/furina_mascot_alias.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <item name="furina_mascot" type="drawable">@drawable/ic_furina_emblem</item>\n'
        '</resources>\n',
        encoding="utf-8",
    )
    print("no mascot image supplied: furina_mascot aliased to the emblem")
