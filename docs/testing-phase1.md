# Phase 1 verification

## Automated checks

```powershell
.\scripts\gradle.ps1 :check :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

- Pure Kotlin session suite: identity, duplicate/stale events, monotonic deadlines, reconnect/expiry ordering, process/reboot recovery, disconnected gaps, prepared history, one-time history reassignment, JSON state, and most recently driven history ordering.
- JVM Android suite (Robolectric API 35): Room checkpoint roundtrip/concurrent atomicity/file reopening, notification visibility/countdown/actions/immutable tokens, and manifest component protections.
- Android lint and packaging of the real app and six native Compose UI tests.
- UI tests exercise real state presentation and callbacks, not live Bluetooth hardware. They execute only with `native.ps1 Test` on a connected phone/emulator. Compiling their APK is not equivalent to running them.

## Physical Pixel 8 setup

1. Pair the car in Android Bluetooth settings first. Perform configuration while parked.
2. Enable USB debugging, connect the phone, unlock it, and approve this PC if prompted.
3. Run `scripts/native.ps1 Devices`, then `scripts/native.ps1 Install -Serial YOUR_DEVICE_SERIAL`.
4. Open Driving Companion. Allow Nearby devices access, choose the paired car, and complete Android's companion association dialog. The car may need to be available/discoverable for system association.
5. Allow notifications if background reconnect countdowns/choices should appear. The UI exposes a repair path for denied access. Notification channels can also be changed by the user in Android settings.
6. Verify that the app reports the selected car and automatic association state accurately. If association is unavailable, foreground profile observation is not proof of background wake support.

## Acceptance checklist — record actual results

| Scenario | Expected result |
| --- | --- |
| Connect the selected car with app open | One active driving period; selected identity displayed |
| Connect earbuds/another speaker | No selected-car session starts |
| Connect with app backgrounded / Waze foreground / screen locked | Supported companion event starts the real foreground session; notification state agrees |
| Disconnect with app focused | In-app countdown warning with original remaining time |
| Switch apps during countdown | No popup over the other app; notification-panel countdown continues from the same deadline |
| Return to app during countdown | In-app warning uses remaining time, not another 20 seconds |
| Reconnect before deadline | Warning clears; original period/session continues; no duplicate period |
| Remain disconnected | Warning clears at expiry; bounded fresh connection confirmation completes; active resources/service stop; history remains |
| Lock screen during grace | Deadline/cleanup verified under real power behavior; no indefinite wake lock |
| Reconnect after expiry | New driving period starts automatically; Resume/New app or notification choice is offered if eligible history exists |
| Choose Resume, including resuming an older record | New period attaches exactly once to the chosen history; next Resume previous offers the most recently driven history |
| Choose New or dismiss the choice | New record remains; old history preserved |
| Pick history while disconnected | Selection prepared only; no Start Drive action or resources are activated |
| Deny/revoke Nearby devices | No falsely active monitoring; clear repair explanation and Android settings route |
| Deny notifications | No claim that a background drawer warning is visible; in-app state and lifecycle deadline remain accurate |
| Forget/change car | Existing period ends, observer/unused association is removed, history remains |
| Process reclaimed / phone rebooted | Fresh platform reconciliation, no stale active-state display; no assumed continuity across an unseen gap |
| Android force-stop | Document Android's stopped-state restrictions; reopen before expecting callbacks again. Force-stop is not a process-reclamation test. |

History duration is based on verified connected intervals and conservatively excludes unseen recovery gaps; it is not a certified route/trip recorder. API 31–35 uses legacy companion callbacks plus bounded audio-profile verification; API 36+ uses explicit Bluetooth presence events. Both paths still need hardware evidence. Phone/car OEM behavior, permission changes, foreground-start restrictions, user notification settings, and association discoverability must be recorded rather than assumed.

## Results discipline

Record sanitized outcomes in STATUS: phone Android/API version, scenario, observed result, app commit, and any failure. Keep real Bluetooth addresses, device serials, routes, personal car names, and raw device logs outside the public repository. A new PC must use the active feature branch and rebuild; Git does not transfer private session history or signing keys.

Current native state: no device/emulator has been connected in this work session. Do not mark these checks passed without actual execution. Voice commands and audible greetings are not Phase 1 functionality.
