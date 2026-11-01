# Codux

Codux is an Android-native Kotlin and Jetpack Compose client for Codex. It embeds the Termux terminal emulator and an ARM64 Termux Linux runtime inside Codux's private app storage; a separate Termux installation is not required. It does not bundle or simulate an AI model.

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

## GitHub Actions: debug and signed release artifacts

`.github/workflows/android.yml` runs unit tests and builds an unsigned-by-release-configuration debug APK for pushes to `main`, pull requests, and manual dispatch. It uploads the debug APK and test reports.

When the four repository Actions secrets listed below are configured, pushes to `main` and manual workflow runs also build and verify a signed release APK and a signed Android App Bundle (AAB). Pull-request builds never receive signing credentials. The signed files are uploaded together as the `codux-signed-release-<run number>` workflow artifact.

The first-time signing setup is automated by `.github/workflows/setup-signing.yml`. Because GitHub's built-in `GITHUB_TOKEN` cannot create repository Actions secrets, the owner must create a temporary fine-grained personal access token limited to this repository with **Secrets: read and write** and **Metadata: read-only**, then add it as the `CODUX_BOOTSTRAP_TOKEN` Actions secret. Also add `CODUX_BACKUP_PASSWORD` as an Actions secret (use a strong password of at least 20 characters and keep it safely). Run **Actions → Initialize Android release signing → Run workflow** once. The workflow generates a permanent JKS key, writes the four release-signing secrets through GitHub's encrypted Secrets API, creates an AES-256 encrypted recovery archive, uploads only that encrypted archive as a short-lived artifact, and deletes the temporary `CODUX_BOOTSTRAP_TOKEN` repository secret after success. It never uploads a plaintext keystore or prints passwords.

Download the `codux-signing-backup-encrypted` artifact promptly (it expires after 3 days), store it offline, and keep the `CODUX_BACKUP_PASSWORD` separately. Revoke the fine-grained token itself under **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens** after the setup succeeds; deleting the repository secret does not revoke the token. Do not commit the keystore, passwords, encrypted backup password, or Base64 value to the repository.

The setup workflow refuses to overwrite an existing signing identity. Never generate a replacement key for an app already distributed with the original key.

The CI release job also requires the published `codux-termux-bootstrap-v2` release. The bootstrap workflow `.github/workflows/termux-bootstrap.yml` publishes that tag. APK builds verify the bootstrap checksum before packaging it.

## Integrated runtime and Codex CLI

The terminal uses upstream Termux PTY/emulator libraries and a private Termux runtime inside Codux. The ARM64 bootstrap includes the Android-patched Codex CLI package maintained by the Termux project, so users do not need to install another terminal app or manually install the CLI. On first use, open **Terminal integrado** and run `codex login --device-auth`; complete the displayed device-code sign-in in a browser, then return to Codux and connect. Authentication remains the user’s own Codex account and is stored in Codux’s private app data. The runtime currently targets ARM64 Android devices; other ABIs do not receive the embedded bootstrap. The app targets API 28 in the local/debug variant so Android allows execution of the Termux binaries extracted into its private app data. The signed CI release uses the API 35 compatibility variant and its native execution shim.

Projects selected through SAF remain backed by Android document providers. A project path passed to a Linux process must also be accessible from the app's private runtime; a SAF URI alone is not a Linux filesystem path.

## Verification limits

GitHub Actions verifies JVM tests, APK/AAB compilation, and artifact signatures. Device behavior including bootstrap extraction, PTY launch, SAF provider operations, and Codex App Server compatibility requires Android-device validation. Terminal commands run with the app's Android UID and are explicitly confirmed; the App Server is loopback-bound and capability-token authenticated.
