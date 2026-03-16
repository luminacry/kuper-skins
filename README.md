# KLWP Minimal Demo

A minimal Android live wallpaper demo built with the platform `WallpaperService` API and a simple Canvas renderer.

## What it includes

- `MainActivity` that opens the wallpaper preview or wallpaper chooser
- `DemoWallpaperService` that renders:
  - a gradient background
  - a moving orb bound to launcher page offset
  - a live clock label
  - a touch ripple

## Build

This project was verified locally with:

- Android Gradle Plugin `9.0.1`
- Gradle `9.2.1`
- Android SDK platform `36.1`
- Build tools `36.1.0`

Build command:

```powershell
$env:JAVA_HOME='C:\Users\HUA\.jdks\openjdk-25.0.2'
$env:ANDROID_HOME='C:\Users\HUA\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\HUA\AppData\Local\Android\Sdk'
.\gradlew.bat assembleDebug --offline
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Try it

1. Install the debug APK on a device or emulator.
2. Open the app.
3. Tap `Open direct preview`.
4. Apply the wallpaper in the system preview.
5. Swipe between home-screen pages and tap the wallpaper to test input.
