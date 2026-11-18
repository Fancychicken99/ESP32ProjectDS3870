# Guidance for coding agents

These instructions apply throughout this repository. Read `README.md` before
changing code; it contains the board settings, BLE interface, and manual tests.

## Project and current baseline

This is a beginner's ESP32-S3 and Android course project. The first milestone
is complete: the custom Android app connected over BLE and physically toggled
the onboard RGB LED on a BLU G50 Plus running Android 10. Preserve this working
behavior when adding features. Read any new assignment requirements before
choosing the next feature; if no objective is given, ask what to work on next.

- `firmware/LedCheck/`: standalone diagnostic that alternates dim red and off.
- `firmware/BleLed/`: Arduino firmware advertising as `DS3870 LED`.
- `android/`: native Java Android app with connection and LED controls.
- `README.md`: setup, build, upload, and physical verification instructions.

## Working with the user

- Explain steps in plain language and work in small, reviewable increments.
- State which actions you can perform and which require the user's PC, board,
  or phone. Do useful independent work before asking a blocking question.
- Inspect the current branch, HEAD, working tree, and available tools first.
  `Dev` is the development branch used for the verified milestone; do not
  assume every session starts there or switch away from existing user work.
- Preserve existing changes. Do not commit, push, merge, or publish an
  environment unless the user instructs you to do so.
- Never request secret values in chat or write credentials into the repository.
- Cloud tasks already have an isolated workspace. Use the existing checkout;
  do not create a Git worktree unless requested.

## Hardware and BLE contract

- Board: ESP32-S3 with 16 MB quad flash, 8 MB embedded PSRAM, and native USB
  Serial/JTAG; Arduino profile `ESP32S3 Dev Module`.
- The onboard addressable RGB LED was physically confirmed on GPIO 48.
  Use the Arduino-ESP32 `rgbLedWrite` API; ordinary `digitalWrite` does not
  implement the NeoPixel protocol. Keep the default red brightness dim (16).
- Service UUID: `ac4a9f3c-6c4a-4f33-aaae-b650f17d6390`.
- LED characteristic UUID: `ac4a9f3c-6c4a-4f33-aaae-b650f17d6391`.
- State is one raw byte: `0x00` off, `0x01` dim red. The characteristic supports
  reads and writes with response. These are bytes, not ASCII strings.
- Keep firmware and Android definitions consistent. Update both sides and the
  README together for an intentional protocol change.
- Preserve Android 10 (API 29) support and permission handling for both Android
  10/11 and Android 12+. Do not introduce a framework migration or dependency
  upgrade merely to implement a small feature.

## Build and validation

Check tool availability in the current environment. Prior Windows tools were
installed in temporary folders and are not guaranteed to exist in a new session.
Use the README's configuration-path guidance when needed.

Firmware uses Arduino-ESP32 core `3.3.13`. From the repository root, use the
same board options for compilation and upload:

```sh
arduino-cli compile --fqbn 'esp32:esp32:esp32s3:FlashSize=16M,FlashMode=qio,PartitionScheme=app3M_fat9M_16MB,PSRAM=opi,USBMode=hwcdc,CDCOnBoot=cdc,UploadSpeed=115200' firmware/LedCheck
arduino-cli compile --fqbn 'esp32:esp32:esp32s3:FlashSize=16M,FlashMode=qio,PartitionScheme=app3M_fat9M_16MB,PSRAM=opi,USBMode=hwcdc,CDCOnBoot=cdc,UploadSpeed=115200' firmware/BleLed
```

The Android build uses JDK 17, Gradle 8.9, Android SDK Platform 35, and Android
Gradle Plugin 8.7.3. There is currently no Gradle wrapper. From the repository
root:

```sh
gradle -p android assembleDebug
```

- Compile affected firmware sketches after firmware edits; build the APK after
  Android edits. For documentation-only changes, review the diff and run
  `git diff --check`; full builds are unnecessary.
- Keep generated APKs, build outputs, local SDK paths, tool installations,
  caches, and signing credentials out of version control.
- Do not assume cloud access to Windows USB devices, the board, or the phone.
  Check actual local device access before attempting an upload or installation.
- Recheck the serial port before every upload. COM3 and COM4 were observed
  previously but are not fixed assignments. Follow the README for BOOT/RESET
  recovery and remember that uploading `LedCheck` replaces the BLE firmware.
- A BLE scanner can isolate communication issues, but does not replace testing
  the custom Android app. Disconnect the scanner before connecting the app.
- For relevant changes, use the README's manual checks: connect, read state,
  toggle on/off, observe the LED, and disconnect/reconnect to confirm state.
- Report compilation, upload, physical LED behavior, BLE communication, and
  custom-app validation separately. Never treat a successful build or upload
  as proof of physical behavior. Mark hardware checks unverified until observed
  in the current test or explicitly confirmed by the user.

## Scope and documentation

Make focused changes using the existing Arduino C++ and native Java structure.
Keep the standalone LED diagnostic useful for isolating hardware issues. Update
the README when setup steps, board settings, protocol, or user-visible behavior
change. At handoff, state what changed, checks actually run, and any remaining
physical-device steps. Do not describe unrun checks as passing.
