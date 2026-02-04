Release: vc483e85-build
Date: 2026-01-31

Summary
- Built and published a signed Android App Bundle (AAB) for this commit/tag.

What changed
- Resolved a git rebase conflict in `.github/workflows/android-release.yml`.
- Bumped `compileSdk` and `targetSdk` to 35 and incremented `versionCode` to 2 (`versionName` 1.0.1).
- Fixed Kotlin compile issues in `ChatScreen.kt` and `NearbyPeersScreen.kt`.
- Updated Gradle properties to reduce library-constraint warnings (`android.dependency.useConstraints=false`).
- Updated `.gitignore` to exclude local build artifacts and keystore files.

Build & Signing
- Artifact: `app-release.aab` (signed and timestamped).
- Keystore used: local `keystore/my-release-key.jks` (private keystore not in repo).
- Signing verified by `jarsigner` (timestamp from DigiCert TSA present). Note: `jarsigner` reported a self-signed signer certificate warning; consider Play App Signing for Play Store distribution.

Tests
- Instrumentation tests (`:app:connectedAndroidTest`) run on connected devices; reports attached as `app/build/outputs/testReports.zip`.

Assets attached to this release
- `app-release.aab` (signed)
- `app/build/outputs/testReports.zip`
- `RELEASE_NOTES_vc483e85-build.md` (this file)

Notes & Next steps
- If publishing to Google Play, verify Play App Signing compatibility or enroll this app in Play App Signing.
- Optional: review CI workflow updates in `.github/workflows/android-release.yml` to ensure keystore secrets are provided in GitHub Actions.

Contact
- Repo: https://github.com/Bedohh206/Sierra-Messenger
