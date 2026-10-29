# Codux

Codux is an Android-native Kotlin/Jetpack Compose front end for the Codex CLI installed in Termux. It does not contain or simulate an AI model.

## Current status

The repository started empty. The local environment has Codex CLI 0.165.0, Node 26.12.1 and npm 11.21.1. It has no Java, Gradle, Android SDK, adb, or Kotlin compiler, so an APK cannot currently be built here. A real non-interactive Codex task was run in `.codux-proof/`; Codex created `proof.txt` and its exact content was verified.

The Android process cannot execute a binary in Termux's private app sandbox. A future bridge must use Termux's explicitly enabled `RUN_COMMAND` permission or a narrowly bound local authenticated service. The starter app does not claim that this bridge is connected. The official App Server uses JSON-RPC over newline-delimited stdio; it can be started locally with `codex app-server --stdio`. Do not expose an unauthenticated network listener.

## Build prerequisites

Install a JDK 17, Gradle 8.9+, Android SDK platform 35 and Android build tools 35 on a development machine, then run `gradle assembleDebug`. The debug APK should appear at `app/build/outputs/apk/debug/app-debug.apk`.

## Planned implementation

1. App Server bridge and real sessions/events/cancel/approval handling.
2. Responsive Compose chat, session history and settings.
3. SAF project browser and editor with safe saves and diffs.
4. Authorized Termux command bridge and process output/cancel/timeout.
5. Tests, build validation and voice/background work.

Features are marked complete only after implementation and verification.

## GitHub Actions

`.github/workflows/android.yml` builds a debug APK on pushes to `main`/`master`, pull requests and manual runs. Download `codux-debug-apk` from the Actions run artifacts. This workflow does not publish a release or sign a production APK.

## Termux bridge status

Termux supports the `RUN_COMMAND` service for authorized third-party apps. The user must grant `com.termux.permission.RUN_COMMAND` and enable `allow-external-apps = true` in Termux settings. Codux currently does not send commands or claim a live Codex connection; that integration is the next implementation step.
