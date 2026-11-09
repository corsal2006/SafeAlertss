> Historical report. The ₹0 Spark-only requirements supersede any paid-service setup or deployment guidance below. Current implementation and verification: [SPARK_ZERO_COST_REPORT.md](SPARK_ZERO_COST_REPORT.md). Setup: [API_SETUP_GUIDE.md](API_SETUP_GUIDE.md).

# SafeAlerts production engineering upgrade report

Date: 18 October 2026. The existing Android application and its visual identity were retained. This is an implemented and locally tested upgrade, **not a claim that all production/physical-device requirements are verified**. Existing workspace edits were preserved. No commits, production deployments, real emergency SMS, or calls were made.

## Crash evidence and reliability repairs

The original user-reported crash stack traces were not supplied, and neither historical crash was reproduced in this run. No exception or Logcat stack trace is invented here. Earlier reports in this checkout contained unsupported stack traces, SDK versions and test counts; they are marked historical.

The supplied Home logo had no click action. Its existing graphic now opens the registered About route, with a 48 dp touch target and single-top navigation. The existing explicit ViewModel factory is retained. Instrumentation exercises 12 repeated logo/About/Back cycles, return to Home, and Activity recreation. This establishes the current interaction's behavior, **not the original crash's cause**.

Concrete timer defects found in source were repaired:

- Repeated primary-expiry callbacks could bypass an already-active grace period; delayed callbacks could restart grace relative to callback time instead of the saved deadline.
- Work did not consistently identify the timer session/deadline that created it, so stale alarms could affect a replacement or extended session.
- Long GPS/network/SMS work could outlive a receiver callback. Receivers now briefly enqueue durable work using `goAsync`, await enqueue within a bounded lifetime, and finish the broadcast. WorkManager performs dispatch.
- Concurrent incident and recipient writes lacked an atomic shared ledger. The outbox now uses AtomicFile and a shared lock; per-recipient reservations precede SmsManager handoff. Cancellation/resolution tombstones prevent revival.

`TimerPolicy`, atomic DataStore transitions, session-bound notification actions, exact/inexact alarms and unique WorkManager jobs now share one scheduler. Grace is anchored to the original deadline. Duplicate expiry retains the same incident ID. Startup/reboot restore saved sessions and unfinished dispatch. Cancellation/arrival/new sessions reject stale work. WorkManager/alarm timing can still be delayed by Android; force-stop requires reopening.

## Implemented behavior

| Area | Implemented change | Verification boundary |
| --- | --- | --- |
| Shared emergency coordination | One persistent incident, bounded profile/GPS/cloud work, independent SMS/BLE/location paths, per-recipient outcomes, restart recovery, owner/session validation, explicit resolution | Real DataStore/outbox/coordinator tests on emulator; carrier and power-management behavior need phones |
| Profile/onboarding | Name, private ISO birth date/date picker, computed age, optional phone/address, contacts/settings entry points, saved editing drafts, loading/errors, complete-later access | Birthday/leap/future/invalid-date unit cases and emulator logout/login persistence |
| Message templates | General Check-In excludes journey helpline; journey includes destination/expected arrival; actual name/age/fix time/accuracy/configured services; no invented tracking URL | Formatter tests and actual coordinator output in emulator |
| Journey/map | Existing Maps/Places flow, saved destination coordinates/deadlines, arrival/extension/cancel guards, authenticated Routes callable for actual driving route/ETA, manual deadline remains explicit | Builds; live Google route/billing/Places integration requires configured backend |
| Trusted Circle | Exact account-ID invitations, recipient acceptance, independent expiring grants, real profile projection, avatar/initial markers, location timestamps/staleness, active incident markers and detail navigation | Nine Firestore emulator authorization scenarios; live multi-account GPS/FCM still required |
| Live location | One existing foreground service and one location loop, active-session reasons, 15-second target, permission/auth/consent rechecks, actual fix timestamps, server-confirmed upload state | Code path/build validated; no physical movement or background endurance measurement |
| Offline SOS/mesh | Private user-shared AES-256-GCM group secret wrapped by Android Keystore; P-256 packet/relay signatures; fresh signed peer challenge responses; MTU framing, durable bounded encrypted queue/inbox, replay/hop/expiry/size/rate limits, retries, real GATT/ACK states | Crypto/tamper/malformed-packet unit tests; two/three-phone radio verification remains mandatory |
| Offline Chat | Authenticated compatible-peer text only, persistent bounded history, local deletion/tombstones, duplicate handling, queued/sent/acknowledged/failed states, composer insets | Build/code tests; physical transport and reconnect tests remain |
| Shake/voice | Five qualifying peaks with release/debounce/window/cooldown; opt-in on-device “help me” recognition on supported Android 12+, foreground lifecycle handling, practice mode | Pure shake/phrase tests; real sensor/microphone/OEM behavior not verified |
| Notifications | Private high-priority FCM workflow with dispatch ledger; durable recipient authorization check; genuine nearby SOS receipt notification; incident/map/dialer actions | Backend syntax and rules checked; FCM deployment and audible/locked-screen delivery unverified |
| UI/manual | Touch targets, wrapping button heights, profile/keyboard insets, real status text, scrolling offline SOS inbox, 20-section manual from Settings | Repeated navigation and 200% font profile test; this is not exhaustive visual QA for every screen/device |
| Privacy | Owner-private birth date/address/contact data, no public phone directory, no client-authorized self-grants, no automatic calls, no fabricated identities/GPS, payload-free application diagnostics | Rules tests and source audit; independent security assessment still advised before release |

## Measured validation

- Initial working-tree baseline: debug build and existing 64 unit tests passed. The initial tree already contained substantial uncommitted work.
- Final JVM suite: **75 tests, 0 failures, 0 errors**. This includes existing legacy simulation tests; they are not substitutes for hardware tests. New regression tests exercise the production date, timer, crypto, signing and five-shake logic.
- Android instrumentation: **12 tests passed** on an isolated Pixel_4 AVD, Android 16 / API 36, serial `emulator-5556`. Nine durable-state/profile/security tests (including Android Keystore/encrypted queue and account-switch rejection), two navigation/font tests and one recovery test. The dedicated `com.siddesh.smsalerts.qa` build uses local Firebase Auth/Firestore on every startup path, with synthetic configuration/accounts, the SEND_SMS permission removed, automatic SMS disabled and the emulator SEND_SMS app-op denied. The tests run the actual `SafeAlertApp`. No physical phones were connected for validation.
- Firestore rules: **9 tests passed** using three synthetic account identities plus unauthenticated access. Cases cover private DOB/address, sender impersonation/self-acceptance, pending grants, one-way/mutual sharing, revocation/expiry, unauthorized location writes, incident ownership, private public-profile fields, forged timestamps and terminal-state reactivation.
- Backend JavaScript: `node --check functions/index.js` passed. This does not execute a deployed FCM or Routes function.
- Final debug APK/test APK build and lint: **passed** with `abortOnError = true`; **0 errors/fatals, 89 warnings, 1 hint**. Warnings include older dependencies/target SDK, unused resources, Kotlin/style suggestions and context-holder checks; these are not described as a clean release audit.
- Final Android suite passed all 12 tests in 62.38 seconds through `:app:connectedQaAndroidTest`; earlier debug/test-runner experiments are superseded by the isolated QA build.
- Separate-process recovery: **two consecutive seed/force-stop/restore cycles passed**. Each restore asserted a different process ID and the same saved Firebase account without signing in again. Both the saved expired timer and unfinished incident reached ACTIVE using their original IDs; SMS remained disabled. Restore phases took 3.704 and 3.718 seconds.
- The recovery harness initially timed out. Test-only Firebase initialization did not cover Android-initiated background starts, so it was replaced by the dedicated QA build. An immediate process stop could also race Firebase's asynchronous sign-in preference write. The seed phase now waits for the synthetic account to exist on disk before the host stops it, and the restore phase asserts account identity explicitly. The failed runs remain recorded; they are not counted as passes. Recovery does not guarantee preservation of a sign-in whose storage write has not completed.
- Per-test Logcat from the Gradle run contains **no FATAL EXCEPTION entries**. That does not recover or identify the original historical crash.
- Visual inspection of `build/qa-profile-large.png` at 200% font scale exposed and verified a fix for the shared muted-text token: it incorrectly used the dark border color. It now uses the existing readable secondary text color while borders/palette remain intact.
- `git diff --check` passed (only line-ending notices). An added-line scan found no Google API key, private-key block or AWS access-key patterns; this is a limited scan, not a comprehensive secret audit.

Useful local evidence: `build/qa-final-validation.log`, `build/qa-recovery-build.log`, `build/process-recovery-seed-1.log`, `build/process-recovery-restore-1.log`, `build/process-recovery-seed-2.log`, `build/process-recovery-restore-2.log`, `build/firestore-rules-tests.log`, `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/androidTests/connected/qa/index.html`, `app/build/reports/lint-results-debug.html`. Instrumentation also captures per-test Logcat under `app/build/outputs/androidTest-results/connected/qa/`.

## Backend/configuration and offline behavior

`firestore.rules`, `firebase.json` and the Node 22 `functions/` project are prepared. **They have not been deployed to production.** The existing Firebase project is reused; synthetic testing uses the separate local `demo-safealerts` emulator project. The local test machine has Node 25; deployment declares Node 22. Verify the deployment runtime in staging.

Configure Android-restricted Maps/Places credentials, the server-only `ROUTES_API_KEY` secret, enabled APIs/billing, auth providers/certificates, FCM tokens and notification channels. Follow `API_SETUP_GUIDE.md`; no privileged key is included in source. The current Android build stays at min SDK 24 and target/compile SDK 34. Updating target SDK/store policy compliance remains a release task.

Offline-capable: stored timers and incidents, cached profile/contact data, age calculation, SMS when a carrier/SIM is available, available local GPS fixes, and compatible private-group BLE exchange/relay. Internet-dependent: account sign-in/server confirmation, invitation/consent confirmation, remote maps/location refresh, FCM and route/Places services. Some previously cached map tiles may display; the app does not claim offline maps coverage.

## Exact limitations and unfinished release checks

1. Original crash roots remain unverified without a reproducible device case and the original exception trace. Current regressions pass; historical report claims are not evidence.
2. No two- or three-physical-device BLE tests were possible. Range, reliable challenge/GATT behavior, MTU fragmentation across vendors, battery/locked-screen behavior, relay topology, ACK loss and departure/rejoin remain release gates. Ordinary phones without compatible software cannot receive these packets. Names are self-reported; compare fingerprints. Everyone with the private group secret can decrypt group traffic; there is no per-user exclusion inside a group.
3. FCM, deployed rules, real driving routes and live cross-account map refresh have not been exercised against production/staging services. No successful remote notification or route response is claimed. Notification sound/DND/lock-screen presentation depends on Android/user settings; no forced full-screen alarm was added.
4. SMS Submitted means Android accepted the call. Sent-to-carrier and Delivered require their respective real multipart callbacks. A crash between reservation and handoff leaves an uncertain status; automatic resend is deliberately avoided. Actual modem/carrier delivery, dual-SIM behavior and permission changes need approved test recipients.
5. Location refresh targets 15 seconds; GPS/network/OS delays can make it slower or stale. A new location foreground service starts while the app is visible or reuses an already-authorized session. This build does not request background-location access. Resolve/revoke ends publishing for that session; remote revocation needs server confirmation or grant expiry.
6. Voice is supported only where Android 12+ on-device recognition exists, while Home is visible (or explicit foreground practice). No always-on microphone/locked-screen voice claim. Shake thresholds are heuristic and require physical calibration/testing; sensor monitoring is not guaranteed under every power state.
7. No secure public tracking web service exists, so messages omit tracking links. Authorized location viewing is in-app. Offline SOS has no cloud gateway upload of strangers' locations. Local chat deletion cannot erase another phone's copy; authenticated delete-for-both is not implemented.
8. A single emulator and font test do not establish full small-screen/tablet/accessibility coverage. API 24–35 devices, real reboot/doze/battery saver, GPS denial/unavailable, Bluetooth off/revocation, prolonged offline use, and simultaneous multi-device account edits remain to be tested.
9. Release signing, store target-SDK/permission review, backend quotas/abuse/load tests, production monitoring, and an independent review of the new mesh protocol remain before distribution as a safety-critical production release.

## Files

Primary new files include `TimerPolicy.kt`, `SafetyTimerWork.kt`, `EmergencyRecovery.kt`, `EmergencyDispatchWorker.kt`, `IncidentSyncWorker.kt`, `EmergencyMessagingService.kt`, `SmsStatusReceiver.kt`, `ProfileDates.kt`, `EmergencyMessageFormatter.kt`, `JourneyRoute.kt`, `ShakePattern.kt`, `MeshCrypto.kt`, `IncidentScreen.kt`, `UserManualScreen.kt`, the Android instrumentation sources, `ProductionSafetyRegressionTest.kt`, `functions/index.js`, and `functions/test/rules.test.js`.

Existing managers, preferences/models/repositories, SMS/location/voice/BLE code, Home/Profile/Journey/Circle/Nearby/Settings navigation/screens, manifests/build configuration and rules were updated in place. `PrivacyLog.kt` prevents runtime payloads/URLs/auth values from entering application logs while retaining exception classes/frames. The exact working-tree file inventory below includes edits/additions that predated this task; it must not be interpreted as every line being newly authored here.

```text
Modified tracked files (includes pre-existing edits):
.gitignore
CRASH_FIX_REPORT.md
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/siddesh/smsalerts/MainActivity.kt
app/src/main/java/com/siddesh/smsalerts/SafeAlertApp.kt
app/src/main/java/com/siddesh/smsalerts/data/location/LocationResult.kt
app/src/main/java/com/siddesh/smsalerts/data/location/LocationService.kt
app/src/main/java/com/siddesh/smsalerts/data/model/TrustedCircleMember.kt
app/src/main/java/com/siddesh/smsalerts/data/model/UserProfile.kt
app/src/main/java/com/siddesh/smsalerts/data/preferences/UserPreferencesDataStore.kt
app/src/main/java/com/siddesh/smsalerts/data/repository/AuthRepository.kt
app/src/main/java/com/siddesh/smsalerts/data/repository/ContactsRepository.kt
app/src/main/java/com/siddesh/smsalerts/data/repository/LocalContactsCache.kt
app/src/main/java/com/siddesh/smsalerts/data/repository/UserRepository.kt
app/src/main/java/com/siddesh/smsalerts/data/sensor/ShakeDetector.kt
app/src/main/java/com/siddesh/smsalerts/data/sms/SmsSender.kt
app/src/main/java/com/siddesh/smsalerts/data/voice/VoiceEmergencyDetector.kt
app/src/main/java/com/siddesh/smsalerts/mesh/BleMeshManager.kt
app/src/main/java/com/siddesh/smsalerts/mesh/MeshPacket.kt
app/src/main/java/com/siddesh/smsalerts/mesh/MeshPeer.kt
app/src/main/java/com/siddesh/smsalerts/mesh/MeshSecurity.kt
app/src/main/java/com/siddesh/smsalerts/mesh/MeshStoreAndForwardQueue.kt
app/src/main/java/com/siddesh/smsalerts/service/BootReceiver.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyManager.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyOutbox.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyProtectionService.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyServicesManager.kt
app/src/main/java/com/siddesh/smsalerts/service/LiveLocationManager.kt
app/src/main/java/com/siddesh/smsalerts/service/SafetyCheckInManager.kt
app/src/main/java/com/siddesh/smsalerts/service/SafetyCheckInReceiver.kt
app/src/main/java/com/siddesh/smsalerts/service/TrustedCircleManager.kt
app/src/main/java/com/siddesh/smsalerts/service/WomensSafetyManager.kt
app/src/main/java/com/siddesh/smsalerts/service/WomensSafetyReceiver.kt
app/src/main/java/com/siddesh/smsalerts/ui/components/TopBars.kt
app/src/main/java/com/siddesh/smsalerts/ui/navigation/NavGraph.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/auth/LoginScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/home/HomeScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/home/SafetyCheckInScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/profile/ProfileScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/EmergencyTriggersScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/trustedcircle/TrustedCircleScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/walkie/WalkieTalkieScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/womensafety/WomensSafetyScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/theme/Color.kt
app/src/main/java/com/siddesh/smsalerts/ui/viewmodel/HomeViewModel.kt
app/src/main/java/com/siddesh/smsalerts/ui/viewmodel/ProfileViewModel.kt
app/src/main/java/com/siddesh/smsalerts/utils/PermissionManager.kt
app/src/main/java/com/siddesh/smsalerts/walkie/WalkieTalkieManager.kt
app/src/test/java/com/siddesh/smsalerts/AdvancedEmergencyTriggersTest.kt
app/src/test/java/com/siddesh/smsalerts/EmergencyOrchestrationUnitTest.kt
app/src/test/java/com/siddesh/smsalerts/SecurityAuditUnitTest.kt
app/src/test/java/com/siddesh/smsalerts/TrustedCircleUnitTest.kt
app/src/test/java/com/siddesh/smsalerts/mesh/MeshNetworkUnitTest.kt
build.gradle.kts
firebase.json
firestore.rules

Untracked files (includes pre-existing additions):
API_SETUP_GUIDE.md
BASELINE_TEST_REPORT.md
PRODUCTION_UPGRADE_REPORT.md
app/src/androidTest/java/com/siddesh/smsalerts/DurableSafetyInstrumentedTest.kt
app/src/androidTest/java/com/siddesh/smsalerts/HomeNavigationInstrumentedTest.kt
app/src/androidTest/java/com/siddesh/smsalerts/ProcessRecoveryInstrumentedTest.kt
app/src/androidTest/java/com/siddesh/smsalerts/QaTestRunner.kt
app/src/main/java/com/siddesh/smsalerts/data/location/JourneyRoute.kt
app/src/main/java/com/siddesh/smsalerts/data/sensor/ShakePattern.kt
app/src/main/java/com/siddesh/smsalerts/data/sms/SmsStatusReceiver.kt
app/src/main/java/com/siddesh/smsalerts/mesh/MeshCrypto.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyDispatchWorker.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyMessagingService.kt
app/src/main/java/com/siddesh/smsalerts/service/EmergencyRecovery.kt
app/src/main/java/com/siddesh/smsalerts/service/IncidentSyncWorker.kt
app/src/main/java/com/siddesh/smsalerts/service/SafetyTimerWork.kt
app/src/main/java/com/siddesh/smsalerts/service/TimerPolicy.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/history/IncidentScreen.kt
app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/UserManualScreen.kt
app/src/main/java/com/siddesh/smsalerts/utils/EmergencyMessageFormatter.kt
app/src/main/java/com/siddesh/smsalerts/utils/PrivacyLog.kt
app/src/main/java/com/siddesh/smsalerts/utils/ProfileDates.kt
app/src/qa/AndroidManifest.xml
app/src/qa/google-services.json
app/src/qa/res/xml/debug_network_security.xml
app/src/test/java/com/siddesh/smsalerts/EmergencyUpgradeValidationTest.kt
app/src/test/java/com/siddesh/smsalerts/ProductionSafetyRegressionTest.kt
functions/index.js
functions/package-lock.json
functions/package.json
functions/test/rules.test.js
local.defaults.properties
```

## Delivered artifact

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (26452774 bytes). SHA-256: `5582b15c47f10431df1ceea3521bceaa6645e0151779d74caf605bbdab242d2d`. It is debug-signed; no production release/signing/distribution was performed.
