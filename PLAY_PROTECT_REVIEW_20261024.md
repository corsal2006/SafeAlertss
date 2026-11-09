# SafeAlerts Play Protect repair and release review

## Current outcome

The app owner reports that Google Play Protect blocks installation as harmful. The specific blocked APK, full warning text and Google's detection category have not been captured. The changes below fix verified security and release-quality issues; none establishes why Google flagged the APK or proves that Google has cleared it.

The updated application builds as **1.0.1, version code 2**, with the same `com.siddesh.smsalerts` package. **Distribution remains blocked by missing credentials for the existing `safealerts-release.jks`.** The keystore is preserved. No replacement key was generated, and the production app/data on the phone were not replaced or erased.

## Verified findings and changes

| Finding | Repair / evidence |
|---|---|
| The previously delivered debug APK is debuggable and uses an Android Debug certificate. Its v2 signature is valid, but it is a development artifact. | Added environment/local-file production signing configuration, explicit non-debuggable release settings, key validation, and a release command that refuses absent credentials or an Android debug certificate. Signing alone does not establish a clean Play Protect verdict. |
| Firestore transitively resolved `protobuf-javalite:3.22.3`, affected by GHSA-735f-pc8j-v9w8. | Raised the compatible runtime minimum to **3.25.5**. A 10,000-level unknown-field input reproduced `StackOverflowError` with 3.22.3 and produced bounded parser rejection with 3.25.5. Added both hostile-input and ordinary-message compatibility regression tests. |
| Privacy text incorrectly denied continuous background location sharing and stated speech recognition was always local. | Updated Settings, the voice settings/manual and contextual permission explanations to describe active background sharing and the speech-provider network fallback accurately. Functionality is preserved. |
| Boot receiver was exported to other applications. | Made it non-exported. Android system broadcasts and same-app access remain supported; arbitrary third-party explicit calls are restricted. An actual reboot was not performed during this follow-up. |
| Production cleartext prohibition was implicit. | Made `usesCleartextTraffic=false` explicit. The isolated QA variant retains its local-emulator network configuration. |

The Protobuf advisory concerns parser denial of service, not proof of malware or the cause of the Play Protect classification. The upstream fix is documented in the [maintainer advisory](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8).

SMS, foreground location, contacts, notifications, alarms, Bluetooth and voice permissions remain available. No permission was hidden or renamed, no security scanner was disabled, and no code obfuscation was introduced to change detection. R8 shrinking remains off to preserve the tested serialization paths; shrinking is optional and is not a Play Protect remedy.

## Validation

- Release build, QA build and instrumentation build: passed. See `build/play-protect-patched-build.log`.
- Release JVM suite: **103 tests, zero failures/errors/skips**, including two new parser regressions.
- Release lint: **zero errors, 100 warnings, three hints**. Warnings are not a claim of a clean audit.
- Public OSV lookup: **161 exact versions checked** (160 resolved Maven libraries and bundled Leaflet 1.9.4); **zero known advisory matches after the patch**. This is a version lookup, not a reachability analysis, malware scan or security certification. Only public package names/versions were sent; no APK/source/user data was uploaded.
- Physical UI tests before the dependency patch: five passed on I2305 / Android 16 using the isolated `.qa` package and local Firebase.
- Final patched physical regression run: results recorded in `build/play-protect-patched-phone.log`; final status is added below after completion.
- Binary artifact checks: production package, version, no debug/test-only flags, backups disabled, no cleartext override, private boot receiver, required feature permissions and map assets present, no private signing/config files packaged.
- Sensitive capabilities absent from the audited production manifest: SMS inbox reading/receiving, call-log reading, accessibility service, notification-listener service, arbitrary APK installation and all-files access.
- Missing-credential signing gate: correctly fails before distribution. See `build/release-signing-gate.log`.

Evidence: `build/release-artifact-audit.json`, `build/release-dependencies.json`, `build/release-dependency-audit-before.json`, `build/release-dependency-audit.json`, `build/protobuf-before.log`, and `build/protobuf-regression-proof.json`.

The unsigned review artifact is `app/build/outputs/apk/release/app-release-unsigned.apk`. It **cannot be installed or distributed**. Its SHA-256 is recorded by the artifact audit; signing will change that hash. The previous debug deliverable remains for development history, not public distribution.

## Signing decision needed

The owner does not know the existing keystore credentials. There are two legitimate paths:

1. Recover the existing credentials from the original developer or a password-manager/secure backup. Fill the ignored `signing.properties` locally. Never post the password in chat or commit the file. Preserve the existing key.
2. If recovery is impossible, deliberately establish a **new release signing identity**, keeping the package name. Before distributing it, register its public SHA-1/SHA-256 fingerprints for Firebase/Google sign-in, validate Google/email login and test the signed APK through the normal installer with Play Protect enabled. A different certificate normally cannot update existing installations; users may need a reinstall, which can erase local preferences, pending incidents and offline keys. The currently connected production app is a debug/test-only installation. Creating a new key will not clear Google's classification by itself.

Option 2 requires the owner's decision because they asked to preserve features and existing use. No uninstall, key replacement, Firebase OAuth change, APK upload or appeal submission has been performed. A key must be securely backed up for future updates.

After credentials are available, run with Java 17+ and the Android SDK available:

```powershell
.\tools\build-release.ps1 -SdkPath 'C:\Users\megha\AppData\Local\Android\Sdk'
```

The command checks signing, builds the signed release, runs release unit tests/lint, then audits the actual APK. Alternatively provide `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` in the environment. Environment values override the local signing file.

To repeat the dependency inventory/check:

```powershell
.\gradlew.bat -I tools/release-inventory.gradle :app:releaseDependencyInventory
.\tools\check-release-dependencies.ps1
```

## Google review still required

Google distinguishes harmful-app classification, sensitive-permission installation blocks, unknown-app scans and old-target compatibility warnings. The reported category here is harmful-app blocking; local signing/build checks cannot reverse Google's classification. Follow [Google's developer guidance and Play Protect appeal process](https://developers.google.com/android/play-protect/warning-dev-guidance#appeals) after validating the final signed artifact. The companion `PLAY_PROTECT_APPEAL_DRAFT.md` is a reviewable draft, not a submitted appeal.

Emergency SMS is retained because it is core functionality. Google lists physical safety/emergency SMS as a possible exception to its SMS policy, subject to declaration, review and approval; this is not automatic acceptance. See [Google's SMS permission policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en).

This remains SDK target 34. A future Google Play publication must independently satisfy the then-current target SDK, permission declarations, foreground-service declarations, privacy policy, Data safety and developer verification requirements. This follow-up does not certify Play Store eligibility or promise acceptance by every scanner.

## Files changed in this follow-up

- `.gitignore`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/siddesh/smsalerts/ui/components/EmergencyPermissionDialog.kt`
- `app/src/main/java/com/siddesh/smsalerts/utils/PermissionManager.kt`
- `app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/EmergencyTriggersScreen.kt`
- `app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/UserManualScreen.kt`
- `app/src/test/java/com/siddesh/smsalerts/ProtobufSecurityRegressionTest.kt`
- `signing.properties.example`; ignored local `signing.properties` with blank credentials
- `tools/build-release.ps1`, `tools/verify-release.ps1`, `tools/release-inventory.gradle`, `tools/check-release-dependencies.ps1`
- This review, the appeal draft, and cross-references in the existing repair/setup/release reports
