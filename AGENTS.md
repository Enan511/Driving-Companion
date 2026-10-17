# Working instructions

Read `docs/STATUS.md`, `docs/requirements.md`, and `docs/development.md` before changing the project. The requirements and the user's newer instructions govern scope.

## Implementation approach

- Build real features incrementally, starting with selected-car Bluetooth activation and driving-session management. Do not introduce a blank demo app, dummy dashboard, placeholder toggles, or unrelated features as a milestone.
- Phase 1 now has a real app module and a pure Kotlin session module. Extend functional increments; do not add placeholder screens or pretend unimplemented voice/location features work.
- Separate pure driving-session decisions from Android lifecycle, Bluetooth, storage, UI, and audio adapters.
- Start with only the modules justified by implemented code. See `docs/architecture.md`.
- Use the checked-in Gradle wrapper through `scripts/gradle.ps1` and the project-local SDK/JDK. Never silently use the system Java 8 or JDK 25.
- Do not reuse previous APKs or prototype sources.
- Use specialist agents/review passes for architecture, lifecycle, integration, UI, and performance when the corresponding implementation exists. Reviews inspect actual code and meaningful tests.

## Verification and handoff

- Maintain `docs/requirements.md` requirement IDs and `docs/STATUS.md` at each meaningful checkpoint.
- Distinguish implemented, JVM-tested, emulator-tested, Pixel-8-tested, and blocked. Toolchain verification is not an application test.
- Exercise real failure paths; never claim Bluetooth wake, car audio, geofencing, or speed-limit coverage works based solely on compilation.
- Check Git state and fetch the active branch before starting on another PC. Do not overwrite or discard another person's changes.
- Use a feature branch for feature work. Commit coherent, verified checkpoints and push them before ending a work session when GitHub access is available. Do not create a timed automation for this.
- Record the exact active branch, last verified commands, remaining blockers, and next action in `docs/STATUS.md`. Never include secrets, private device identifiers, or private locations in public logs/docs.
- The repository is public. Keep `.tools`, `.local`, API tokens, signing keys, generated audio, model weights, and machine-specific paths out of Git. Do not commit an APK as a source handoff.
