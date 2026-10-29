# Codux

Codux is an Android-native Kotlin and Jetpack Compose client for the Codex CLI running inside Termux. It does not bundle or simulate an AI model.

## Features

- Material 3 chat with conversation history, streamed assistant replies, cancellation, approvals, voice dictation, text-file attachments, Markdown code blocks, and system/light/dark appearance.
- Local Codex App Server connection over an authenticated WebSocket bound to `127.0.0.1`. The bearer token is generated locally, encrypted with Android Keystore, and written to a private Termux file with mode `600`.
- SAF project list and details with search, nested file browsing, import/create/rename/copy/move/delete actions, and a safe text editor with a 2 MB limit, conflict checks, temporary writes, backup-and-rename saves, and unified diff previews.
- Explicitly confirmed terminal commands with streamed output, stdin, recent-command shortcuts, cancellation, timeout, and an ongoing session notification.
- GitHub Actions workflow for relevant changes on `main`, pull requests, and manual runs.

## Build locally

Install JDK 17, Gradle 8.9, Android SDK platform 35, and build tools 35.0.0. Then run:

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

`.github/workflows/android.yml` runs on pushes to `main` when Android or Gradle build files change, on pull requests with those files, or through `workflow_dispatch`. It installs JDK 17, Gradle 8.9 and Android SDK 35, runs `gradle testDebugUnitTest`, builds with `gradle assembleDebug`, and uploads `codux-debug-apk` plus the unit test reports. It does not sign or publish a production release.

A workflow run must be observed in GitHub Actions to confirm the build and artifact. A push made with GitHub's `GITHUB_TOKEN` may not trigger another workflow; this workflow is intended to run from a normal user push or a manual dispatch.

## Connect Termux

1. Install the official Termux app and open it once.
2. In Termux, enable `allow-external-apps=true` in `~/.termux/termux.properties`, then restart Termux.
3. Install and authenticate the Codex CLI in Termux.
4. Open Codux and connect. Android/Termux must grant Codux `com.termux.permission.RUN_COMMAND`.
5. Select a project folder with the SAF picker. Set the corresponding Termux filesystem path under Settings as the Codex working directory.

The GitHub workflow verifies JVM unit tests and APK compilation. Android-specific behavior, including SAF provider operations and the Termux `RUN_COMMAND` permission, still requires validation on a configured Android device. Codux currently uses the Termux app as its local Linux execution runtime; the standalone Codex CLI does not list Android as a supported host platform, so Codux does not claim to bundle or run Codex independently of Termux. The App Server WebSocket transport is experimental and documented as unsupported for production workloads. Codux binds it to loopback and authenticates the connection; it does not expose an unauthenticated network listener. Terminal commands run outside the Codex sandbox and always require user confirmation.
