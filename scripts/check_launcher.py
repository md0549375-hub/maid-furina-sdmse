#!/usr/bin/env python3
"""Fails unless the merged manifest has exactly one real launcher activity (action MAIN + category LAUNCHER).
Library test activities that only declare the LAUNCHER category (no MAIN action) never show up as icons."""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()
found = []
for comp in list(root.iter("activity")) + list(root.iter("activity-alias")):
    for f in comp.findall("intent-filter"):
        actions = {a.get(A + "name") for a in f.findall("action")}
        cats = {c.get(A + "name") for c in f.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
            found.append(comp.get(A + "name"))
print("launcher activities:", found)
if len(found) != 1:
    print("::error::expected exactly one launcher activity, found %d" % len(found))
    sys.exit(1)
