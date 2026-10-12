# Initial development environment check

Checked on 12 October 2026. These findings describe the current PC; they do not establish the capabilities of the second PC.

| Component | Finding |
| --- | --- |
| Workspace | Initially empty; no existing Git repository or project instructions found |
| Git | 2.53.0.windows.1 |
| GitHub CLI | 2.96.0; authenticated as `Enan511` over HTTPS |
| Java runtime on PATH | Oracle Java 8, version 1.8.0_503 |
| Java compiler on PATH | Eclipse Temurin JDK 25.0.3 |
| Java / Android environment variables | `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` are unset |
| Gradle | Not on PATH; no project wrapper exists yet |
| Android platform tools | `adb` and `sdkmanager` are not on PATH |
| Android Studio | Not found at the standard Program Files installation location or in the inspected installed-application records |
| Android SDK | Not found at the standard per-user SDK location or the additional common locations checked |
| Python | `python` is not on PATH; an installed Python test-suite entry alone does not verify a usable interpreter |
| Node.js | v24.12.0 |

Existing per-user Android and Gradle configuration directories were found. Their presence does not prove that a usable SDK or build toolchain is installed. Portable or custom installations may exist elsewhere.

GitHub CLI access required elevated execution because the sandbox could not read its local authentication configuration. Authentication and account identity were subsequently verified against GitHub.

## Prerequisites before Android project setup

- Locate or install Android Studio and the required Android SDK components.
- Select a JDK compatible with the Android Gradle Plugin and Gradle versions chosen for the project, and align the runtime and compiler.
- Establish a project Gradle wrapper; a global Gradle installation is not required when using the wrapper.
- Verify an emulator or an authorized physical Pixel 8 connection when device testing begins.
- Verify backend hardware and Python/model compatibility separately before Qwen3-TTS deployment.

## Verification status

Only environment and repository setup have been performed. No app code, build, emulator run, physical-device test, Bluetooth test, or provider integration has been performed.
