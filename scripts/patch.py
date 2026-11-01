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

# ---- Gradle: side-by-side install, consistent package name, stable signing for the debug build type ----
gradle = root / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
if "applicationIdSuffix" in g:
    sys.exit("gradle already patched")

ns = "        namespace = projectConfig.packageName\n"
if g.count(ns) != 1:
    sys.exit("namespace line not found exactly once; upstream layout changed")
g = g.replace(ns, ns + '        applicationIdSuffix = ".furina"\n', 1)

pk = 'buildConfigField("String", "PACKAGENAME", "\\"${projectConfig.packageName}\\"")'
if g.count(pk) != 1:
    sys.exit("PACKAGENAME field not found exactly once; upstream layout changed")
g = g.replace(pk, 'buildConfigField("String", "PACKAGENAME", "\\"${projectConfig.packageName}.furina\\"")', 1)

bt = "    buildTypes {\n        all {"
if g.count(bt) != 1:
    sys.exit("buildTypes block not found exactly once; upstream layout changed")
g = g.replace(
    bt,
    '    buildTypes {\n        getByName("debug") {\n            signingConfig = signingConfigs.getByName("releaseFoss")\n        }\n        all {',
    1,
)
gradle.write_text(g, encoding="utf-8")
print("gradle patched")
