# Driving Companion

Driving Companion (working app name: **Jarvis Drive**) is a planned native Android driving assistant targeting the Google Pixel 8.

## Project status

Repository setup only. No Android application, backend, or app features have been implemented. No APK has been built, installed, or tested.

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

See [the initial environment check](docs/environment.md) for current toolchain findings. Android tooling and provider choices must be verified before application scaffolding or implementation starts.
