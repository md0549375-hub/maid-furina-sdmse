# Maid Furina (SD Maid SE overlay)

Private overlay that turns upstream [SD Maid SE](https://github.com/d4rken-org/sdmaid-se) (GPL-3.0) into
"Maid Furina": Furina theme/persona, Gemini chat with function calling, Keystore-encrypted API key, WorkManager schedule.

* `overlay/` - files copied over an upstream checkout (Kotlin under `eu.darken.sdmse.furina`, resources `furina_*`).
* `scripts/patch.py` - registers the activity in the manifest and renames the launcher label.
* `scripts/make_icon.py` - builds the adaptive-icon foreground from `furina_mascot.jpg`.
* `.github/workflows/build-apk.yml` - clones upstream, applies the overlay, builds `app:assembleFossDebug`, publishes a release.

The mascot is a 160x256 "sample" preview: replace `overlay/app/src/main/res/drawable-nodpi/furina_mascot.jpg`
with a licensed, larger original. Keep this repository private unless you own the artwork rights.
