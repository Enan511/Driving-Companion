# Handoff status

Updated: 17 October 2026.

## Active checkpoint

- Active branch: `chore/core-development-prep`.
- Scope: project-local native-development preparation, requirements, and reproducible another-PC handoff.
- No application code, backend code, empty app module, APK, or feature tests exist yet.
- Preparation is locally verified. The next implementation is F1 below; there is no separate empty-app milestone.

## Requirements progress

| Area | State |
| --- | --- |
| Public GitHub repository | Created and connected |
| Requirements and functional implementation order | Documented |
| Project-local JDK / Gradle | Downloaded and checksum-verified |
| Android SDK and native development commands | Installed; doctor verified in PowerShell 7 and Windows PowerShell 5.1 |
| Gradle / Android and Kotlin plugin resolution | `check` passed with project JDK/SDK |
| GitHub toolchain workflow | Passed on a fresh Windows runner for tooling commit `478a82b` |
| CAR-01 through CAR-05 (first functional increment) | Not started |
| Greetings, places, Waze, arrivals, speed, Qwen | Not started |
| Pixel 8 / car verification | Not performed |

## Verified commands and review

- `scripts/bootstrap.ps1 -AcceptAndroidLicenses`: installed project-local tools and SDK packages. Repeated successfully under Windows PowerShell 5.1 after fixing its native-stderr warning handling.
- `scripts/doctor.ps1`: passed under PowerShell 7.6 and Windows PowerShell 5.1; checks required files, script syntax, exact wrapper properties, and published wrapper-JAR SHA-256.
- `scripts/gradle.ps1 check --no-daemon`: passed with JDK 21 and Android platform 36/build tools 35.0.0. This is prerequisite verification, not application compilation or feature tests.
- `scripts/native.ps1 Devices`: ADB ran; no connected phone or emulator was listed.
- Specialist review inspected actual scripts/configuration/handoff. Unicode SDK paths and Windows execution-policy setup findings were corrected; focused re-review found no remaining actionable blocker.
- [Fresh Windows GitHub Actions run](https://github.com/Enan511/Driving-Companion/actions/runs/39066633312): bootstrap, doctor, wrapper validation, and Gradle check all passed in a clean checkout. This demonstrates fresh-machine setup, not phone or application functionality.
- No application or backend was implemented, built, installed, or device-tested.

The system's original Java configuration was not changed. The SDK manager's newer-XML warning is documented in `docs/environment.md`; required SDK files and plugin configuration were verified successfully. ADB/Java processes required elevated tool execution in the Codex sandbox; ordinary Windows shell checks also passed outside that sandbox.

## Next action after preparation

Read requirements and architecture, then create a feature branch for F1 selected-car Bluetooth/session management. Implement the pure session state machine with meaningful tests, then the Android adapter and a minimal useful vehicle/session UI in the first actual app module. Verify permissions and supported presence/background mechanisms against the real Pixel 8 OS and car. Do not start with a blank-app milestone.

## External dependencies still open

- Pixel 8/car connection and phone OS version.
- Second GitHub account if it needs write access.
- Maps/place-search credentials/provider selection.
- Road-limit provider access, coverage, licensing, cost.
- Qwen PC hardware and secure backend endpoint.

## Continue from another PC

Fetch and check out the active branch above, read this file and `docs/development.md`, run bootstrap/doctor/check locally, then start at the recorded next action. Large tools, caches, private configuration, and device logs are intentionally not in Git.
