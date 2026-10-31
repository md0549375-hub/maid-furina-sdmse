#!/usr/bin/env python3
"""Registers FurinaHostActivity in the SD Maid SE manifest and renames the launcher label.
Fails loudly when upstream changed so a silent half-patch can never ship."""
import pathlib, sys

root = pathlib.Path(sys.argv[1])
manifest = root / "app/src/main/AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")

if "FurinaHostActivity" in text:
    sys.exit("manifest already patched")

marker = "        <!-- Debug stuff-->"
if text.count(marker) != 1:
    sys.exit("manifest marker not found exactly once; upstream layout changed")

activity = """        <activity
            android:name="eu.darken.sdmse.furina.ui.FurinaHostActivity"
            android:exported="true"
            android:label="@string/furina_app_name"
            android:icon="@mipmap/ic_launcher"
            android:theme="@style/Theme.Furina"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

"""
text = text.replace(marker, activity + marker, 1)

label = 'android:label="@string/app_name"'
if text.count(label) != 1:
    sys.exit("application label not found exactly once; upstream layout changed")
text = text.replace(label, 'android:label="@string/furina_app_name"', 1)

manifest.write_text(text, encoding="utf-8")
print("manifest patched")
