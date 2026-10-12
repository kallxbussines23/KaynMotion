# KaynMotion — Android Kotlin MVP

Native Android starter for a lightweight, local-first motion tracking editor.

## Features
- Android video picker via Storage Access Framework.
- Video preview and play/pause.
- Draggable manual tracking point.
- Keyframes saved at current video timestamps.
- Linear interpolation between keyframes.
- Project JSON saved in app-private local storage.
- Black/burgundy UI; Android 10+ (API 29).

## Scope
This MVP provides manual 2D point tracking. It is not automatic computer-vision tracking or true 3D camera tracking. It does not yet render tracking overlays into exported video.

## Build
Use Android Studio, JDK 17, Android SDK 35. Sync Gradle and build the app debug variant. GitHub Actions builds the APK on pushes to main; download the workflow artifact when the run succeeds.
