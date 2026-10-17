# Architecture and functional increments

## Current boundary

Preparation provides a real build toolchain, wrapper, dependency pins, verification tasks, and development commands. There is no app module, launcher screen, placeholder feature implementation, or APK. The first APK will accompany the first real feature.

## Initial code structure

Start with only two modules when Bluetooth/session work begins:

```text
android/
  app/                    Native Android adapters and minimum useful vehicle/session UI
    vehicle/              Selected-car storage, association, Bluetooth events
    drive/                Foreground service and lifecycle reconciliation
    diagnostics/          Test-only actions and observable session events
  core/drive/             Pure Kotlin session state machine and deterministic timing rules
```

`core/drive` must not import Android APIs. Test connection identity, duplicate events, disconnect grace, reconnect cancellation, and state restoration with a controllable clock. Device association and process recovery require Android integration/device tests in addition to those unit tests.

Keep future places, location, speed, voice, navigation, UI, database, and settings in clear packages. Extract additional Gradle modules only when ownership, reuse, platform isolation, or build performance warrants it. Do not create empty modules for the entire roadmap now.

## Event flow

```mermaid
flowchart TD
  A[Bluetooth / location / provider adapters] --> B[Validated events]
  B --> C[Session state and deterministic rules]
  C --> D[Announcement scheduler]
  D --> E[Qwen cache / remote Qwen / local TTS]
  E --> F[Android audio routing]
```

Persist selected-car identity and enough state for lifecycle reconciliation. A restored state is not proof that the car is still connected: reconcile with supported platform observations before resuming resources or speech.

Suggested session states: NOT_CONFIGURED, CAR_DISCONNECTED, CAR_CONNECTING, DRIVE_STARTING, DRIVE_ACTIVE, TEMPORARILY_DISCONNECTED, DRIVE_ENDING. Avoid duplicate services, greetings, location subscriptions, and session-end timers.

Separate the active driving period from its persistent history grouping. A short grace reconnect stays in the same period. After grace expiry, resuming a history record adds a new connected period and preserves the disconnected gap. History-selection state does not itself authorize Bluetooth/service startup. See [the detailed session design](bluetooth-session-design.md).

## Implementation order

| Increment | Real result | Required dependencies |
| --- | --- | --- |
| F1: car/session core | Real car selection, automatic activation, grace/reconnect/end, visibility-aware disconnect countdown, local history, and app/notification Resume/New choices | Project toolchain; Android Bluetooth/companion/notification permissions; storage; Pixel 8/car |
| F2: audible welcome and optional session commands | Local-time greeting, priority queue, local TTS; optional bounded voice Resume/New with explicit fallback | F1; audio focus/routing; separately verified speech recognizer and microphone eligibility |
| F3: saved destinations | Real search, map choice, persisted place/radius/messages, Waze departure | Storage; selected map/search services; F2 for speech |
| F4: arrivals | Validated arrival announcement with anti-repeat state | F1 session gating; F2 speech; F3 coordinates; location/geofences |
| F5: speed awareness | Quality-controlled GPS speed, matched road limit, deterministic escalating warnings | F1; F2; location; licensed/accessible road provider |
| F6: Qwen voice | Original voice, generated audio, cache, secure backend, local fallback | F2 speech contract; suitable backend hardware; endpoint |
| F7: release hardening | Measured background/audio/battery/UI behaviour and acceptance evidence | All implemented increments |

Investigate provider access/coverage and Qwen hardware early. They should not block implementing F1 or local speech. Qwen backend work can proceed separately after the voice API contract is agreed. Full UI navigation grows as its real features become available; no four-tab blank dashboard first.

## Planned integration contracts

- `PlaceSearchProvider`: normalized search results with usable coordinates and explicit errors; independent of map renderer.
- `SpeedLimitProvider` -> `SpeedLimitResolver` -> `SpeedWarningEngine`: limit/source/segment/confidence/freshness, explicit unknown state, deterministic warning decisions.
- `AnnouncementManager`: bounded queue, message identity, expiration, priority, cancellation, and audio-focus handling; safety announcements cannot depend on remote synthesis.
- `QwenTtsClient`: bounded request timeouts, authenticated endpoint, model/profile/style identifiers, audio format, safe retry semantics. Agree the contract before installing a backend stack.

## Initial build choices

JDK 21, Gradle 8.13, AGP 8.13.2, Kotlin/Compose plugin 2.2.21, Android platform 36, build tools 35.0.0. These are a pinned compatible baseline, not a claim to be the newest versions. Target/min SDK and application identity become explicit app configuration with F1; verify the phone OS and API paths then.

Sources checked during preparation: [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html), [Android SDK manager](https://developer.android.com/tools/sdkmanager).
