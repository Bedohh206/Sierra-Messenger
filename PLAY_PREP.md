Play Console preparation checklist

1) Create a release keystore (locally) and save it somewhere in your repo root (recommended path: `keystore/my-release-key.jks`):

On Windows (run in Git Bash / PowerShell with keytool available):

```bash
keytool -genkeypair -v \
  -keystore keystore/my-release-key.jks \
  -alias release \
  -keyalg RSA -keysize 4096 -validity 9125
```

Choose a secure password and remember it.

2) Copy `keystore.properties.template` to `keystore.properties` at project root and fill values:

```
storeFile=keystore/my-release-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=release
keyPassword=YOUR_KEY_PASSWORD
```

Keep `keystore.properties` out of source control (add to `.gitignore` if needed).

Optional helper script:

If you prefer a convenience script, run the PowerShell helper which will invoke `keytool` interactively and place the keystore at `keystore/my-release-key.jks`:

```powershell
.\scripts\generate_keystore.ps1
```

Then copy `keystore.properties.template` to `keystore.properties` and fill the passwords and `storeFile` path accordingly.

3) Build an Android App Bundle (AAB) for Play:

```bash
./gradlew :app:bundleRelease
```

Output bundle will be at `app/build/outputs/bundle/release/app-release.aab`.

4) Upload the AAB to Play Console: create a new app (or use existing), follow internal test / internal app sharing / production tracks, and upload the bundle. Enable Play App Signing (recommended) when prompted.

5) Additional Play Console items:
- Prepare store listing assets (icons, screenshots, feature graphic).
- Provide privacy policy URL (required for apps using Bluetooth/nearby features).
- Fill content rating questionnaire and targets.
- Provide contact details and testing instructions for internal testers.

6) If you prefer to use Google Play App Signing only and not manage your own keystore, you can upload an unsigned bundle via internal app sharing; however using a local keystore and uploading a signed AAB is the standard path.

Notes:
- Do NOT commit `keystore.properties` or your keystore to public repos.
- If you want, I can add a Gradle task to copy the keystore into a build-time location or add CI steps for automated signed builds (e.g., GitHub Actions). Let me know which CI you use.
 
CI (GitHub Actions) instructions

I added a sample GitHub Actions workflow at `.github/workflows/android-release.yml` which will build and sign your AAB using repository secrets. Set the following repository Secrets in GitHub for the workflow to run successfully:

- `KEYSTORE_BASE64` — base64-encoded contents of your `keystore/my-release-key.jks` (how to create shown below)
- `KEYSTORE_PASSWORD` — the store password
- `KEY_ALIAS` — the alias (e.g., `release`)
- `KEY_PASSWORD` — the key password

To create `KEYSTORE_BASE64` on Windows (PowerShell):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore\my-release-key.jks'))
```

Paste that output into the `KEYSTORE_BASE64` secret value. The workflow will decode the keystore, write `keystore.properties`, and run `./gradlew :app:bundleRelease`, then upload `app-release.aab` as a workflow artifact.

Invite / Friends flow

- The app supports an invite-based friends list to restrict interactions:
  - Inviting: a discovered peer may be added as a friend locally by tapping "Invite" on the Nearby Devices screen. This creates a local `Friend` entry containing `id`, `name`, and `address`.
  - Enforcement: incoming GATT messages are only accepted and emitted by the app if the sender's Bluetooth address matches an entry in the local friends table.
  - Scope: invites are currently handled locally (no cloud sync). For cross-device or remote invites a backend will be required.

Ensure your Play Store listing makes clear that the app uses Bluetooth for nearby discovery, that it does not share precise GPS location, and that interactions can be limited to invited friends via a local friend list.
