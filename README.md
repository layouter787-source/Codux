# Codux

Codux is an Android-native Kotlin and Jetpack Compose client for the Codex CLI. The app integrates the Termux terminal emulator and is being migrated to a Termux Linux runtime private to Codux; it does not bundle or simulate an AI model.

## Features

- Material 3 chat with conversation history, streamed assistant replies, cancellation, approvals, voice dictation, text-file attachments, Markdown code blocks, and system/light/dark appearance.
- Codex App Server over an authenticated WebSocket bound to `127.0.0.1`; the capability token is generated locally, encrypted with Android Keystore, and stored in Codux private files.
- SAF project list and details with search, nested file browsing, import/create/rename/copy/move/delete actions, and a safe text editor with a 2 MB limit, conflict checks, temporary writes, backup-and-rename saves, and unified diff previews.
- Explicitly confirmed terminal commands with streamed output, stdin, recent-command shortcuts, cancellation, timeout, and an ongoing session notification.
- Termux terminal emulator source modules under their upstream license terms.

## Build locally

Install JDK 17, Gradle 8.9, Android SDK platform 35, build tools 35.0.0, and NDK 29.0.14206865. Then run:

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. No APK is installed automatically.

## GitHub Actions

`.github/workflows/android.yml` runs for relevant pushes to `main`, pull requests, and manual dispatch. It runs unit tests, assembles the debug APK, and uploads the APK and test reports. A published ARM64 Termux bootstrap is included in subsequent APK builds. `.github/workflows/termux-bootstrap.yml` builds that bootstrap for the Codux package path and publishes its source manifest and assets. The bootstrap build is still being validated; until the release asset exists, the integrated shell reports that its runtime is unavailable.

## Integrated runtime and Codex CLI

The terminal uses the upstream Termux PTY/emulator libraries in the Codux app. The App Server launcher now targets the private Codux runtime and no longer requests the external Termux `RUN_COMMAND` permission. The embedded ARM64 Linux bootstrap must be available in the APK before shell commands can run. The Codex CLI must also be installed and authenticated inside that runtime before the App Server can start. Codex does not publish an official Android/Termux host target, so runtime compatibility of a CLI build and authenticated App Server operation must be verified on an Android device.

Projects selected through SAF remain backed by Android document providers. A project path passed to a Linux process must also be accessible from the app's private runtime; a SAF URI alone is not a Linux filesystem path.

## Verification limits

GitHub Actions verifies JVM tests and APK compilation. Device behavior including bootstrap extraction, PTY launch, SAF provider operations, and Codex App Server compatibility requires Android-device validation. Terminal commands run with the app's Android UID and are explicitly confirmed; the App Server is loopback-bound and capability-token authenticated.
