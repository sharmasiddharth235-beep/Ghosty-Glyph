# Ghosty Glyph

A playful Android companion for the Nothing Phone (4a) Pro with expressive on-screen animations and an experimental integration with the rear 13×13 Glyph Matrix.

## Features
- Fluid idle animation, tap reactions, shake and tilt responses.
- Mood changes for charging and low battery.
- Uses Nothing's official Glyph Matrix SDK to draw a tiny mood face on the rear matrix while Ghosty is open in the foreground.
- Gracefully does nothing on unsupported devices.

## Build a debug APK
The GitHub Actions workflow downloads Nothing's official glyph-matrix-sdk-2.0.aar, builds the app, and uploads an artifact named `ghosty-debug-apk`.

1. Open **Actions** → **Build Ghosty APK**.
2. Choose **Run workflow** if a run is not already in progress.
3. Open the completed run and download `ghosty-debug-apk`.
4. Extract `app-debug.apk` and install it on the phone.

## Requirements
- Nothing Phone (4a) Pro for physical Glyph Matrix output.
- Android 14+ for the Nothing Glyph SDK; app-matrix output requires a sufficiently recent Nothing OS build.
- Android SDK 35 and JDK 17 for the build.
- Package: `com.ghosty.glyph`.

## Important limitations
- Rear Glyph updates are app-matrix updates while Ghosty is foregrounded. They stop when the app goes into the background.
- This is not yet an always-on/AOD Glyph Toy service. A user must select and enable a Glyph Toy separately for an always-on experience.
- The app has not yet been tested on a physical Phone (4a) Pro. Confirm the build and hardware behaviour before treating it as a finished release.
- The workflow creates a debug APK for testing, not a Play Store release.