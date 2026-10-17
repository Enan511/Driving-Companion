# Qwen voice backend

This directory reserves the Android-independent backend boundary; no backend or model environment has been installed yet.

Before implementation, verify the intended PC's operating system, CPU, GPU/VRAM, RAM, model/runtime compatibility, measured synthesis latency, and secure access from the phone while driving. Agree the request/audio/cache contract with Android's announcement manager first.

Use an original British assistant voice. Keep model weights, generated audio, tokens, and private configuration outside Git. The Android client must support local essential speech without this service.
