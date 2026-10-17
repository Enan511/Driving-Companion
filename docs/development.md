# Development and another-PC handoff

## Windows x64 setup

Run from the repository root in PowerShell. The folder name/location may differ between PCs.

If Windows blocks local scripts with an execution-policy error, open a process-only development shell first: `powershell.exe -NoProfile -ExecutionPolicy Bypass`. This does not change the machine or user policy. Use that shell in the repository folder; an organization-enforced policy may still apply.

```powershell
.\scripts\bootstrap.ps1 -AcceptAndroidLicenses
.\scripts\doctor.ps1
.\scripts\gradle.ps1 :check
```

The explicit license switch accepts the necessary Android SDK licenses for this local installation. Without it, bootstrap preserves existing license choices and SDK installation may prompt for acceptance. Review [Android SDK terms](https://developer.android.com/studio/terms) when needed.

Bootstrap downloads exact pinned archives and verifies their published checksums before extracting. JDK and Gradle archives use SHA-256. Google's repository publishes SHA-1 for the selected command-line tools archive; that published digest is recorded explicitly rather than misrepresented as SHA-256. SDK packages are installed through Google's SDK manager. Platform-tools and SDK platform patch revisions can change upstream; record actual installed revisions when diagnosing a cross-PC difference.

All project tools live in `.tools/`; Gradle caches and Android preferences live in `.local/`. These directories and `android/local.properties` are ignored by Git. Machine-wide Java/PATH settings are not changed. Setup currently supports Windows x64; macOS/Linux/ARM tool archives need a separate reviewed manifest/bootstrap before use. Git-tracked requirements and application code remain portable.

`scripts/env.ps1` configures only the current PowerShell process. `scripts/gradle.ps1` automatically loads it and forwards arguments to the committed Gradle wrapper. When directly using SDK commands, load it first:

```powershell
. .\scripts\env.ps1
sdkmanager --list_installed
```

The wrapper's Gradle version and distribution SHA-256 are committed. There is no global Gradle prerequisite. The first wrapper is generated with the verified project-local distribution. Dependencies use explicit versions in `android/gradle/libs.versions.toml`.

## What can be verified now

`doctor.ps1` checks tools, SDK files, wrapper checksum configuration, and PowerShell syntax. `gradle.ps1 :check` selects the repository verification task: toolchain checks, pure session tests, debug JVM Android integration tests, and Android lint. The leading colon avoids selecting every child project's broader all-variant `check` task. Add `:app:assembleDebug :app:assembleDebugAndroidTest` to produce the app and native UI-test APKs. The JVM Android tests use Robolectric/API 35; they are not physical-device or emulator evidence.

Android Studio is optional for command-line development. If installed separately, open `android/`, use the project JDK shown by `. scripts/env.ps1; $env:JAVA_HOME` as Gradle JDK, and use the SDK at `$env:ANDROID_HOME`. Do not invoke an empty-app wizard. An emulator/system image is not installed by this bootstrap; use a physical phone initially, and add an emulator deliberately when needed.

## Native device testing

On the Pixel 8, enable Developer options and USB debugging, connect a data-capable USB cable, unlock the phone, and approve its computer authorization if prompted.

```powershell
.\scripts\native.ps1 Devices
```

No app is installed by environment preparation. Choose the device's actual serial from that command:

```powershell
.\scripts\native.ps1 Install -Serial YOUR_DEVICE_SERIAL
.\scripts\native.ps1 Test -Serial YOUR_DEVICE_SERIAL
.\scripts\native.ps1 Logs -Serial YOUR_DEVICE_SERIAL
```

Install builds `:app:assembleDebug` and installs only on the explicitly selected device. Test runs `:app:connectedDebugAndroidTest`; Logs filters the reserved `DrivingCompanion` tag (sensitive event logging is not enabled). Session unit tests can run separately through `scripts/gradle.ps1 :core:drive:test`. See `docs/testing-phase1.md` for actual car/background acceptance. Debug signing keys may differ by PC; if Android rejects an update signed by the other PC, use an agreed private development signing setup outside Git or explicitly manage the existing installation without silently deleting its data.

Keep device identifiers, coordinates, routes, car addresses, and raw logs local. Put sanitized outcomes in `docs/STATUS.md`.

## Continue on another PC

1. Finish a coherent checkpoint, update STATUS, commit, and push the active branch before switching PCs.
2. Clone `https://github.com/Enan511/Driving-Companion.git` if needed.
3. Run `git fetch origin`, inspect `git status`, then check out the active branch listed in STATUS. Do not replace local uncommitted work.
4. On an existing clean checkout, use `git pull --ff-only` for that branch. On a new checkout, use `git switch --track origin/BRANCH_NAME`.
5. Read STATUS, requirements, and architecture. Run bootstrap and doctor locally; downloaded tools do not travel through Git.
6. Re-run the feature's documented checks on that PC/device before making new claims.

Both PCs use the same repository and branch history. Public read access allows cloning; pushing requires a GitHub account with write access. The second account has not been supplied. Never commit tokens to enable access.

## Git checkpoints

Use a branch per substantial feature. Make checkpoint commits after a useful implementation/test/review boundary, after resolving a blocker, and before finishing a session. Push periodically at those boundaries, not on a timer. Update STATUS with what works, exact checks, what is unverified, and the next action. If pushing fails, keep the local commit and report it; never claim another device can retrieve unpushed changes.

## Bootstrap recovery

- Partial downloads end in `.partial`; rerunning downloads that archive again. A verified cached ZIP can be reused.
- A checksum mismatch stops installation. Inspect the source/manifest before replacing a cached archive.
- Do not upgrade tool versions by editing only the wrapper or only the manifest: update matching pins, checksums, docs, and verify together.
- Installing Android Studio or Qwen/model runtimes is outside this preparation. Qwen hardware and deployment still need their own verification.
