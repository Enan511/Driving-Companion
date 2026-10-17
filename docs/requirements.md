# Driving Companion requirements

Authoritative project baseline, consolidated from the supplied 12 October 2026 handover and the 17 October 2026 development-direction update. Working app name: Jarvis Drive. Primary device: Google Pixel 8. Native Android; build from scratch.

## Development direction

- Begin at the functional core: selected-car Bluetooth activation and session management.
- No separate empty-app or decorative-skeleton milestone. The first installable app must exercise the first feature.
- Keep development tools local to the project folder and make setup repeatable from Git on another PC.
- Implement and verify one functional increment at a time. Preserve progress through checkpoint commits, pushes, and an explicit handoff record.

## MVP requirements and acceptance

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| CAR-01 | List paired Bluetooth devices; select the car once; persist a stable identifier/association rather than name alone | Real selected-car state survives restart; another device does not match |
| CAR-02 | Start/resume a single session on a validated selected-car connection using supported Android mechanisms | Physical-car tests with UI foreground/background and screen locked; process-reclaim and reboot cases documented |
| CAR-03 | End after approximately 20 seconds of disconnection; cancel ending on reconnect | Timing/state tests plus real reconnect/disconnect tests; no duplicate sessions |
| CAR-04 | No normal Start Drive button; manual triggers live only in diagnostics | UI inspection and automation tests |
| CAR-05 | Handle permissions, companion association, foreground-service restrictions, process lifecycle, and force-stop limitations honestly | OS-specific implementation review and device evidence; no universal wake-up claim |
| CAR-06 | During disconnect grace, show an in-app warning/countdown only while the app is focused; otherwise show the warning/countdown in the notification panel without bringing the app forward | Foreground/background/screen-lock tests; warning follows visibility changes without a new deadline |
| CAR-07 | Dismiss the disconnect warning on reconnect or grace expiry; a dismissed warning does not cancel the deadline; clear expired UI after process recreation | Reconnect/expiry/visibility/process-recreation tests using one authoritative deadline |
| HIST-01 | Persist local session history so a previous session can be selected and resumed, or a new session chosen | Database/restart tests; history survives process death and normal app restarts |
| HIST-02 | Reconnection during grace continues the same driving period automatically; a later verified car connection can start a new period attached to a previous or new history record | Grace versus post-expiry tests; stopped time is a gap rather than driving duration |
| HIST-03 | User chooses app-popup or voice interaction for Resume/New; background popup mode uses notification controls and never forces the app onscreen | Settings/presentation tests; voice permission/unavailable recognition falls back visibly |
| HIST-04 | Resuming history restores relevant saved context only; fresh connection/location/road data is required, and stopped services are not resurrected while the car remains disconnected | Stale-context and disconnected-resume tests |
| GREET-01 | One greeting per genuinely started driving period, including a new period attached to resumed history; no duplicate on a grace reconnect; local morning 04:00-11:59, afternoon 12:00-16:59, evening 17:00-21:59, night 22:00-03:59 | Boundary-time, resume, and reconnect tests |
| GREET-02 | Multiple variations; avoid recent repetition; settings and cached speech | Deterministic selection tests and audible device verification |
| PLACE-01 | Add/edit/delete/enable at least 20 places, each with name, address, coordinates, radius, departure/arrival messages, timestamps | Persistence and UI tests with multiple places |
| PLACE-02 | Real in-app search for venues/addresses; current location; interactive map selection; marker, visible radius, confirm action | Real provider results and saved selected coordinates; no WebView workaround |
| PLACE-03 | Initial radius range 100-500 m; show selected name/address and handle no results, ambiguity, missing credentials, throttling, errors, slow responses, and denied location | Relevant UI/provider failure tests |
| WAZE-01 | A saved-place action speaks its departure message and launches Waze navigation to saved coordinates | Real Waze deep-link test; missing-app and speech-timeout handling |
| WAZE-02 | Independently started Waze trips and internal speed/destination streams are not assumed available | Integration uses supported functionality; any experimental observer remains optional and separately scoped |
| ARRIVE-01 | Speak custom arrival once on a validated enabled-place entry during an active drive | Entry, continued presence, exit/re-entry, and session gating tests |
| ARRIVE-02 | Define starting-inside, GPS jitter, boundary hysteresis, overlapping-place ordering, cooldowns, registration recovery, and parked behaviour | Simulated transitions plus physical-device background tests |
| SPEED-01 | Use fresh, sufficiently accurate location speed; reject implausible samples; manage sampling with the driving session | Recorded sample tests, GPS accuracy evidence, stopped-session resource checks |
| LIMIT-01 | Resolve road limit through a replaceable map-matching provider; return limit, segment, source, confidence, timestamp | Real provider exercise, road transitions, parallel roads/overpasses, coverage limitations |
| LIMIT-02 | Unknown/stale/ambiguous limits display `--` and suppress road-limit warnings; log conflicting sources without treating voting as truth | Failure/staleness/conflict tests; no guessed urban defaults |
| LIMIT-03 | Conditional, variable, roadworks, and outdated static limits are treated conservatively; actual signs remain authoritative | Provider policy and documented limitations |
| WARN-01 | Determine stages from actual speed minus detected limit, using application code rather than an LLM | Parameterized tests across 40/50/60/80/100/110 km/h roads |
| WARN-02 | Configurable defaults: stage 1 at +4, stage 2 at +10, stage 3 at +20, stage 4 at +30 km/h | Boundary tests, settings persistence; thresholds are app settings, not legal allowances |
| WARN-03 | Hysteresis, sustained-condition filtering, per-stage cooldowns; higher stages can interrupt lower-stage cooldowns | Deterministic timing and noisy-speed replay tests |
| WARN-04 | Tone escalates from mild to urgent; serious high-stage messages; unknown limits cannot trigger normal speeding warnings | Message/priority tests and audible review |
| VOICE-01 | Original mature British assistant voice via separate Qwen3-TTS backend; no actor imitation; no full model inside APK | Actual synthesis, playback, voice review, APK contents |
| VOICE-02 | Cached Qwen -> dynamic Qwen -> Android TTS fallback; essential warnings do not wait for remote generation | Offline/timeout/provider-error tests; bounded response latency |
| VOICE-03 | Deterministic cache keys include normalized text, profile, style, model/version; bounded storage and cleanup | Cache hit/invalidation/eviction tests |
| VOICE-04 | One announcement at a time; priority: warning 4, 3, 2, arrival, departure, greeting, warning 1 | Queue/preemption tests |
| VOICE-05 | Respect calls and navigation; audio focus/ducking; car playback while music/Waze is active | Real device/car tests with music, Waze speech, incoming call, audio interruption |
| VOICE-06 | Optional bounded Resume/New voice commands; use a separate supported speech-recognition path, microphone permission, timeout, and app/notification fallback; no assumption of unrestricted background listening | Real voice selection plus denied permission, OS restriction, no-response, and recognition-failure tests |
| UI-01 | Only Home, Places, Speed, Settings as primary screens; dark restrained design, one accent, large targets, accessible contrast | Pixel 8 UI review; no avatar, clutter, or decorative dashboard |
| UI-02 | Configuration while parked; home shows vehicle/session, speed/limit, status, last announcement; technical detail in diagnostics | UI and interaction review |
| DATA-01 | Room for structured place data; DataStore for settings where justified; modular, event-driven design | Persistence tests and architecture review |
| QUALITY-01 | Progressive permission requests; clear denied/revoked/missing-provider states; no arbitrary perpetual polling | Permission-flow/lifecycle tests and battery review |
| QUALITY-02 | Lightweight background operation while Waze is foregrounded, screen locked, music active, within Android restrictions | Physical-device lifecycle, battery, CPU, memory, network tests |
| QUALITY-03 | Conventional Android Gradle packaging/signing; do not reduce target SDK to hide build defects | Install and launch tests, signing configuration review |
| DEV-01 | One public shared repository, separate Android/backend directories, feature branches, reproducible setup | Fresh-checkout verification; secrets/models/tools excluded |
| DEV-02 | Distinct implementation/test/readiness states; specialist reviews inspect actual work | STATUS and evidence updated at checkpoints; no invented results |

## Non-MVP scope

No food ordering, news, weather, conversational memory, job diaries, spending dashboards, calendar/email/messaging, Spotify controls, OBD-II, full Android Auto UI, or general-purpose AI chat. Waze remains the navigation app.

## Dependencies requiring decisions

- Verify the actual phone Android version and supported Bluetooth/companion-presence path.
- Select map rendering and place-search providers independently after credential/cost/terms evaluation.
- Confirm road-limit provider access, geographic coverage, licensing, pricing, and measured matching quality. Mapbox, Google Roads, and OSM are candidates, not completed integrations.
- Verify the second PC's CPU/GPU/RAM before choosing Qwen deployment; secure the reachable driving-time endpoint.
- Confirm the second GitHub account if collaborator access is needed. It has not been supplied or invited.
- Verify a permitted speech-recognition interaction for voice session selection. Qwen3-TTS generates speech; it is not the command recognizer.

## Session-design proposals awaiting confirmation

- Keep automatic driving activation independent of Resume/New history selection. Proposed default: begin a new history record on a later connection and retain it if the choice is ignored; choosing Resume attaches the new connected period to the chosen prior record without restarting the service or replaying greetings. This default is a design proposal, not an explicit user selection.
- Put session history under Home as a secondary screen, preserving the four main tabs. Store history on the phone; development Git handoffs do not synchronize private driving history.

See [Bluetooth/session interaction design](bluetooth-session-design.md) for diagrams and presentation rules, and [STATUS](STATUS.md) for implementation versus test evidence. Phase 1 now implements the app/notification history path; voice remains deferred.

## Phase 1 traceability

CAR-01 through CAR-07, HIST-01/HIST-02/HIST-04, and the app/notification part of HIST-03 have source implementations. Core rules and JVM Android storage/notification/manifest tests exist; native UI tests compile. Actual selected-car callbacks, locked/background operation, reboot recovery, notification rendering, power behavior, and companion-association discoverability still require Pixel 8/car verification. HIST-03 voice and VOICE-06 are not implemented. No feature is called production-ready on build evidence alone.

## Completion rules

A feature needs real implementation, permissions/lifecycle handling, relevant tests, explicit failure behaviour, matching UI, exercised integration, documented device limits, and no known critical defect. Implemented does not mean device-tested or production-ready.
