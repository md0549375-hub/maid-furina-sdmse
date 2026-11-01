#!/usr/bin/env bash
# Emulator smoke test: install the APK, open the Furina screen, fail on a crash.
set -euo pipefail
apk="$1"
pkg="eu.darken.sdmse.furina"

adb install -r "$apk"
adb shell pm list packages | grep -q "package:$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/eu.darken.sdmse.furina.ui.FurinaHostActivity"
sleep 10
adb exec-out screencap -p > furina-chat.png
adb logcat -d > logcat.txt || true

if grep -E "FATAL EXCEPTION|Process: $pkg" logcat.txt; then
  echo "App crashed on start" >&2
  exit 1
fi
adb shell pidof "$pkg" > /dev/null
echo "smoke ok"
