# Ghosty Glyph

Android companion prototype with expressive idle animations, tap reactions, accelerometer tilt and shake responses, and battery-aware moods.

## Build APK
Open **Actions** → **Build Ghosty APK** → **Run workflow**. When the workflow succeeds, download the `ghosty-debug-apk` artifact. It contains `app-debug.apk`.

## Requirements
- Android 8.0+ (API 26)
- Android SDK 35
- JDK 17
- Package: `com.ghosty.glyph`

## Current limitations
Ghosty animates inside its app window. It does not currently control the physical rear Glyph hardware or run continuously in the background. This workflow creates a debug APK for testing, not a Play Store release.
