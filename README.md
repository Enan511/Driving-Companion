# Driving Companion

Driving Companion (working app name: **Jarvis Drive**) is a planned native Android driving assistant targeting the Google Pixel 8.

## Project status

The project-local Android development toolchain is prepared and verified. No Android application, backend, or app features have been implemented. No APK has been built, installed, or tested.

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

The first implementation is selected-car Bluetooth activation and driving-session management. The first installable app must contain that functional increment; there is no separate blank-app milestone.

## Project-local Windows setup

```powershell
.\scripts\bootstrap.ps1 -AcceptAndroidLicenses
.\scripts\doctor.ps1
.\scripts\gradle.ps1 check
.\scripts\native.ps1 Devices
```

Tools and machine-specific caches are downloaded into ignored `.tools/` and `.local/` directories. Another PC clones the same branch and repeats setup, then continues from STATUS. Bootstrap currently supports Windows x64. `check` verifies build prerequisites; it is not an application build or feature test.

The original [environment check](docs/environment.md) records the pre-setup machine state.
