# Handoff status

Updated: 17 October 2026.

## Active checkpoint

- Active branch: `chore/core-development-prep`.
- Scope: project-local native-development preparation, requirements, and reproducible another-PC handoff.
- No application code, backend code, empty app module, APK, or feature tests exist yet.
- Current work: SDK installation and wrapper validation; final verification will be recorded before this checkpoint is pushed.

## Requirements progress

| Area | State |
| --- | --- |
| Public GitHub repository | Created and connected |
| Requirements and functional implementation order | Documented |
| Project-local JDK / Gradle | Downloaded and checksum-verified |
| Android SDK and native development commands | Installation/verification in progress |
| GitHub toolchain workflow | Written; not yet run |
| CAR-01 through CAR-05 (first functional increment) | Not started |
| Greetings, places, Waze, arrivals, speed, Qwen | Not started |
| Pixel 8 / car verification | Not performed |

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
