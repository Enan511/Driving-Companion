# Handoff status

Updated: 17 October 2026.

## Active checkpoint

- Active branch: `feature/bluetooth-sessions`.
- Implemented code checkpoint: `5eaa66c`; [draft PR #1](https://github.com/Enan511/Driving-Companion/pull/1). Later handoff-only documentation commits do not change the verified app source.
- Scope: F1 selected-car Bluetooth sessions, native disconnect countdowns, and persistent resumable history.
- Real `app` and pure Kotlin `core/drive` modules exist; there is no dummy dashboard or Start Drive button.
- App and native UI-test APKs build. Local verification passed: 22 core tests, 9 JVM Android tests, and zero Android lint issues. Pixel 8/car acceptance remains pending.
- Voice commands, greetings, GPS/location, places, Waze, speed warnings, and backend implementation remain later phases.
- Automatic new-history creation while a Resume choice is pending implements the proposed non-blocking default. A consumed/expired choice cannot reassign another driving period.

## Requirements progress

| Area | State |
| --- | --- |
| Public GitHub repository | Created and connected |
| Requirements and functional implementation order | Documented; F1 implemented in source |
| Project-local JDK / Gradle | Downloaded and checksum-verified |
| Android SDK and native development commands | Installed; doctor verified in PowerShell 7 and Windows PowerShell 5.1 |
| Gradle / Android and Kotlin plugin resolution | `check` passed with project JDK/SDK |
| GitHub workflow | Fresh Windows app tests/lint/both APK builds passed for `5eaa66c`; debug APK and reports uploaded |
| CAR-01 through CAR-07 and local session history | Implemented; JVM verification and APK builds; Pixel 8/car acceptance pending |
| Optional voice Resume/New commands | Specified; speech-recognition/background eligibility not verified |
| Greetings, places, Waze, arrivals, speed, Qwen | Not started |
| Pixel 8 / car verification | Not performed |

## Verified commands and review

- `scripts/bootstrap.ps1 -AcceptAndroidLicenses`: installed project-local tools and SDK packages. Repeated successfully under Windows PowerShell 5.1 after fixing its native-stderr warning handling.
- `scripts/doctor.ps1`: passed under PowerShell 7.6 and Windows PowerShell 5.1; checks required files, script syntax, exact wrapper properties, and published wrapper-JAR SHA-256.
- `scripts/gradle.ps1 check --no-daemon`: passed with JDK 21 and Android platform 36/build tools 35.0.0. This is prerequisite verification, not application compilation or feature tests.
- `scripts/native.ps1 Devices`: ADB ran; no connected phone or emulator was listed.
- Specialist review inspected actual scripts/configuration/handoff. Unicode SDK paths and Windows execution-policy setup findings were corrected; focused re-review found no remaining actionable blocker.
- [Fresh Windows GitHub Actions run](https://github.com/Enan511/Driving-Companion/actions/runs/39066633312): bootstrap, doctor, wrapper validation, and Gradle check all passed in a clean checkout. This demonstrates fresh-machine setup, not phone or application functionality.
- The above tooling-only run predates Phase 1 and is not evidence for the app.

## Phase 1 implementation and review

- Selected-car storage and exact-address association; public API 36 Bluetooth presence events with API 31-35 compatibility adapters. BLE proximity is not connection evidence.
- Single serialized session owner, monotonic 20-second deadline, focused popup/background chronometer, expiry connection confirmation, and grace-only bounded wake lock.
- Local Room checkpoint/history, disconnected-gap exclusion, prepared history, latest-driven Resume previous ordering, one-time choice tokens, and original history preserved on New.
- Fresh evidence before active-state display; conservative unseen-gap recovery; unexpected service teardown freezes connection intervals. Failed startup and permission denial expose repair states.
- Three specialist passes inspected actual core rules, Android lifecycle/integration, and UI/performance. Reported defects were corrected rather than only noted.
- Final verification command: `scripts/gradle.ps1 :check :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon` passed. JUnit XML reports 31 total tests, zero failures/errors/skips; lint XML reports zero errors/warnings.
- `apksigner verify --verbose` passed (APK signature scheme v2); `aapt dump badging` verifies application identity, launcher, minimum API 31 and target API 36. This is packaging verification, not an install test.
- Six native Compose tests are compiled into the UI-test APK; no emulator/Pixel 8 execution has been performed.
- [Phase 1 fresh Windows CI](https://github.com/Enan511/Driving-Companion/actions/runs/39069273077) passed: bootstrap, doctor, wrapper validation, tests, lint, and both APK builds. The run publishes the debug APK and reports as 14-day artifacts; rebuild from Git after they expire.
- APKs, private history, device IDs, signing keys, tools, and caches remain outside Git.

The system's original Java configuration was not changed. The SDK manager's newer-XML warning is documented in `docs/environment.md`; required SDK files and plugin configuration were verified successfully. ADB/Java processes required elevated tool execution in the Codex sandbox; ordinary Windows shell checks also passed outside that sandbox.

## Next action

Connect the Pixel 8, install the debug APK, run native UI tests and the parked-car checklist in `docs/testing-phase1.md`. Record actual OS/car results and fix any native lifecycle defect before declaring F1 complete. Optional voice selection comes with F2 after speech recognition is verified. Do not start unrelated phases while critical F1 acceptance remains unresolved.

## External dependencies still open

- Pixel 8/car connection and phone OS version.
- Second GitHub account if it needs write access.
- Maps/place-search credentials/provider selection.
- Road-limit provider access, coverage, licensing, cost.
- Qwen PC hardware and secure backend endpoint.

## Continue from another PC

Fetch and check out the active branch above, read this file and `docs/development.md`, run bootstrap/doctor/check locally, then start at the recorded next action. Large tools, caches, private configuration, and device logs are intentionally not in Git.
