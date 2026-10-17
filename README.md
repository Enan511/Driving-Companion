# Driving Companion

Driving Companion (working app name: **Jarvis Drive**) is a planned native Android driving assistant targeting the Google Pixel 8.

## Project status

Phase 1 implements selected-car Bluetooth sessions, disconnect countdowns, and local resumable session history. The native app and its UI-test APK build from the project-local toolchain. JVM tests cover the session engine, Room persistence, notifications, and manifest protections. Physical Pixel 8/car verification is still required; background reliability is not claimed from compilation alone.

The project starts from scratch; previous APKs and prototype source are excluded.

## MVP scope

1. Automatically start a driving session and speak a time-aware greeting when the selected car's Bluetooth connects.
2. Speak a saved place's custom departure message when navigation is launched through the app into Waze.
3. Speak a custom arrival message on a validated saved-place geofence entry.
4. Compare GPS speed with a sufficiently reliable detected road limit and issue four configurable warning stages.

Supporting functionality includes saved places, real place search, an in-app map picker, settings, and automatic session shutdown after a disconnect grace period.

The interface will have four main screens: Home, Places, Speed, and Settings. There is no normal Start Drive button.

## Technical direction

- Kotlin and Jetpack Compose, with an event-driven architecture and persistent session state.
- Separate Android client and Qwen3-TTS backend directories within this repository when implementation begins.
- An original British assistant voice, cached Qwen audio, dynamic synthesis, and Android TextToSpeech fallback.
- Replaceable place-search and speed-limit providers.
- Deterministic speed-warning rules with confidence checks, freshness checks, hysteresis, and cooldowns.

Unknown road limits remain unknown and suppress road-limit-based warnings. Waze integration initially uses supported navigation deep links; access to its internal destination and speed-limit data is not assumed. Android background activation must be verified under the applicable lifecycle restrictions.

Weather, news, ordering, messaging, calendar integration, general AI chat, OBD-II, and a full Android Auto interface are outside the MVP.

## Development

Use one shared repository across both development PCs. Keep `main` as the default branch and use feature branches for substantial work. The second GitHub account has not been added yet.

This repository is public. Never commit credentials, private configuration, signing keys, generated voice caches, or model weights.

Start with [the handoff status](docs/STATUS.md), [requirements](docs/requirements.md), [architecture and feature order](docs/architecture.md), and [development setup](docs/development.md).

The app currently contains working vehicle/session/history functionality. Greetings, voice commands, places, Waze, GPS monitoring, speed warnings, and Qwen remain future increments; no placeholder tabs or settings represent them as implemented.

## Project-local Windows setup

```powershell
.\scripts\bootstrap.ps1 -AcceptAndroidLicenses
.\scripts\doctor.ps1
.\scripts\gradle.ps1 :check
.\scripts\native.ps1 Devices
```

Tools and machine-specific caches are downloaded into ignored `.tools/` and `.local/` directories. Another PC clones the same branch and repeats setup, then continues from STATUS. Bootstrap currently supports Windows x64. `check` runs the session and Android JVM tests, Android lint, and toolchain verification.

## Build and test Phase 1

```powershell
.\scripts\gradle.ps1 :check :app:assembleDebug :app:assembleDebugAndroidTest
.\scripts\native.ps1 Devices
.\scripts\native.ps1 Install -Serial YOUR_DEVICE_SERIAL
.\scripts\native.ps1 Test -Serial YOUR_DEVICE_SERIAL
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk` (ignored by Git). The app identity is `io.github.enan511.drivingcompanion`, minimum Android 12/API 31, target API 36. See [Phase 1 native verification](docs/testing-phase1.md) before claiming car/background behavior is tested.

The original [environment check](docs/environment.md) records the pre-setup machine state.
