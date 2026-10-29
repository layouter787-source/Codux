# Codux

Codux is an Android-native Kotlin and Jetpack Compose client for the Codex CLI running inside Termux. It does not bundle or simulate an AI model.

## Implemented in the working tree

- Compose chat with conversation history, streamed assistant replies, cancellation, approval prompts, voice dictation, and system/light/dark appearance.
- Local Codex App Server connection over an authenticated WebSocket bound to `127.0.0.1`. The bearer token is generated locally, encrypted with Android Keystore, and written to a private Termux file with mode `600`.
- SAF project browser and text editor, including nested folders, a 2 MB file limit, a conflict check, temporary writes, backup-and-rename saves, and unified diff previews.
- Explicitly confirmed Termux commands with streamed output, stdin, cancellation, timeout, and an ongoing session notification.
- GitHub Actions workflow for relevant changes on `main`, pull requests, and manual runs. It runs unit tests, builds the debug APK, and uploads it as `codux-debug-apk`.

The changes in this working tree have **not** yet been built or run on an Android device. Do not treat these features or the APK as verified until the GitHub Actions build and the device integration have actually passed.

## Build locally

Install JDK 17, Gradle 8.9, Android SDK platform 35, and build tools 35.0.0. Then run:

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

`.github/workflows/android.yml` runs on pushes to `main` when Android or Gradle build files change, on pull requests with those files, or through `workflow_dispatch`. A successful build uploads `codux-debug-apk`. It does not sign or publish a production release.

A workflow run must be observed in GitHub Actions to confirm the build and artifact. A push made with GitHub's `GITHUB_TOKEN` may not trigger another workflow; this workflow is intended to run from a normal user push or a manual dispatch.

## Connect Termux

1. Install the official Termux app and open it once.
2. In Termux, enable `allow-external-apps=true` in `~/.termux/termux.properties`, then restart Termux.
3. Install and authenticate the Codex CLI in Termux.
4. Open Codux and connect. Android/Termux must grant Codux `com.termux.permission.RUN_COMMAND`.
5. Select a project folder with the SAF picker. Set the corresponding Termux filesystem path under Settings as the Codex working directory.

The App Server WebSocket transport is experimental and documented as unsupported for production workloads. Codux binds it to loopback and authenticates the connection; it does not expose an unauthenticated network listener. The separate terminal feature runs outside the Codex sandbox and always requires a user confirmation before starting a command.
