> Historical report. The ₹0 Spark-only requirements supersede any paid-service setup or deployment guidance below. Current implementation and verification: [SPARK_ZERO_COST_REPORT.md](SPARK_ZERO_COST_REPORT.md). Setup: [API_SETUP_GUIDE.md](API_SETUP_GUIDE.md).

# SafeAlerts hardening verification — 20 October 2026

This is an implementation and verification report, not a production certification. Existing uncommitted work was preserved. The supplied ZIPs contained eleven screenshots; the connected phone later supplied an actual app crash trace.

## Diagnosed issues and changes

| Evidence / root cause | Change |
| --- | --- |
| The phone's saved crash on 19 October at 08:09:44 follows `MeshSecurity.encrypt → MeshStoreAndForwardQueue.save → removePacket → WalkieTalkieScreen` on the main thread. Removing an absent packet attempted to encrypt an empty queue even without a private group key. The UI coroutine did not handle the failure. | Missing-packet removal is now a no-op. Stop/delete failures are visible, and a real Android Keystore regression test exercises removal without a group. Original trace: `build/physical-original-crash-20261019.txt`. |
| Countdown settings wrote one preference key while the coordinator read a different key; screen entry also defaulted to five seconds. Automatic triggers bypassed the cancellation window. | One normalized 3/5/10-second setting drives manual, review, shake, voice and Offline SOS countdowns. Existing preferences are read compatibly. |
| Countdown ownership existed only in a UI coroutine, so process loss could lose the pending action. Review also had an independent direct-SMS path. | Countdown incident and deadlines are persisted first. Elapsed realtime is used within a boot, with bounded wall-clock recovery across boots. Alarm/WorkManager invoke the existing coordinator through atomic state transitions. Review uses the same coordinator. |
| Repeated UI entry and stale callbacks could restart presentation; a cancellation during setup could race the first persistence. | Screen recreation resumes its saved incident ID. Cancellation can persist a terminal tombstone; stale expiry cannot dispatch it. Per-recipient submission reservations remain idempotent. |
| Incident details depended on Firestore even when an owner-scoped local incident existed; the screenshot showed cloud unavailability alongside local activity. | Owner details fall back to saved local state and metadata. Cloud status is tracked separately; GPS, SMS, Bluetooth and cloud failures retain separate outcomes. The screenshots alone do not establish the precise production Firebase failure. |
| Trusted Circle exposed internal UIDs and lacked a short-code backend. | Eight-character codes, secure generation/collision checks, 15-minute expiry, revocation/regeneration, UID/IP guessing limits, single-use redemption, and recipient-only acceptance are implemented in trusted functions. No location grant is implied by a code. |
| Queued callbacks could repopulate a location or danger marker after a grant was replaced/revoked. | Relationship and grant generations invalidate stale callbacks; expired/revoked grants clear markers and subscriptions. Rules continue enforcing authorization. |
| Profile phone status had no verification action; fields were crowded. | Firebase credential linking/update, SMS code entry, resend/session limits and genuine provider errors; spaced profile fields, existing date picker and calculated age, enabled-contact count. |
| Journey offered only presets and cramped extension/cancel controls. | Custom 1–1,440-minute duration, 0–60-minute grace, exact safety deadline separate from route ETA, paired extensions and full-width cancel. Durable session checks are retained. |
| Nearby display used short identity labels for verification; chat only supported local deletion. | Full SHA-256 verification fingerprints remain copyable and comparable. Added authenticated direct-message deletion requests, sender-scoped tombstones, durable retries, and encrypted account/group-scoped chat history. Queue bounds prioritize SOS and expose expiry/eviction. |
| Voice controls were cramped, previews contained invented location data, and recognition retries could reset too eagerly. | Validated custom phrases, encrypted phrase storage, final-result matching with confidence/cooldown protections, real profile preview, and explicit foreground/on-device availability. No fake listening state or example location is used. |

## Verification

| Check | Actual result |
| --- | --- |
| Baseline debug build, unit tests, lint | Passed before this pass. |
| Final app build | Debug APK, QA APK and QA instrumentation APK built successfully in 38 seconds; `build/final-hardening-build-20261020-r5.log`. |
| JVM tests | 77 passed across 15 suites, zero failures. |
| Android lint | Zero errors; 96 warnings and 2 hints remain, including dependency, deprecated-API, resource and style notices. This is not a warning-free release. |
| Firestore rules and callable-handler integration | 16 tests passed against local Firestore. Coverage includes unauthorized/private reads, directional grants/revocation, code expiry, collision/revocation behavior, rate limits, single-use redemption, recipient-only acceptance and old-grant cleanup. `build/backend-hardening-20261020.log`. |
| Pixel 4 AVD | 15 instrumentation tests passed. Included cancellation windows, expiry, deduplication, stale timer callbacks, profile persistence, Keystore encryption, no-group removal, repeated logo navigation and large-font Compose assertions. |
| Physical I2305, Android 16 | **Final full suite: 17/17 passed in 35.577 seconds** on the final rebuilt QA APK (`build/physical-final-suite-20261020.log`). Earlier locked-screen and disconnected-USB setup failures are retained in the logs. Expanded coverage includes profile verification controls, custom-duration validation and compact code entry with keyboard visible. |
| Separate-process recovery | Passed on the physical phone and AVD. Seeded a countdown, expired timer and pending incident; stopped only QA; restored in a different process without another login and verified the same owner/IDs. See `build/recovery-seed-*.log` and `build/recovery-restore-*.log`. Force-stop itself does not permit background execution; recovery is verified after relaunch. |

Local tests use isolated Firebase emulator fixtures and the separate `.qa` application. Its manifest removes SEND_SMS and the runner denies its SMS app-op. These fixtures are not evidence of two real production accounts. No emergency calls or real SMS dispatches were made.

The emulator displayed a **System UI** ANR dialog during the first UI run, obstructing its initial screenshot despite passing Compose assertions. Clean physical-phone captures subsequently verified the profile at 200% font scale, including separate birth date, age, verification status and button: `build/qa-profile-large-physical.png` and `build/qa-profile-details-large-physical.png`. The test restores the original font setting afterward. No QA app crash report was present when checked after the expanded suite.

## Backend deployment

The user approved deployment to the existing `smselerts` project. The three invitation functions were attempted first so new rules would not disable the old flow before the replacement backend existed.

Firebase rejected deployment because the project requires the **Blaze pay-as-you-go plan** to enable the required services. The new functions and rules were not deployed in that attempt. Billing changes remain the user's decision. Log: `build/firebase-circle-deploy-20261020.log`.

The user indicated they will enable Blaze and report readiness. Do not assume it has been enabled until verified. Existing FCM and Routes functions also require their configured services; neither remote notification delivery nor real route responses were verified in this pass.

## Files changed in this pass

Paths below are relative to `app/src/main/java/com/siddesh/smsalerts/` unless specified. The checkout already had substantial unrelated/uncommitted changes; `git diff` contains both prior work and this pass.

- Emergency: `service/EmergencyManager.kt`, `EmergencyOutbox.kt`, `EmergencyCountdownWork.kt`, `CountdownPolicy.kt`, `EmergencyRecovery.kt`, `BootReceiver.kt`, `IncidentSyncWorker.kt`; `data/model/EmergencyIncident.kt`; `data/preferences/UserPreferencesDataStore.kt`; `ui/viewmodel/SosViewModel.kt`; `ui/screens/sos/EmergencyReviewScreen.kt`, `SosCountdownScreen.kt`; `ui/screens/history/IncidentScreen.kt`; `ui/navigation/NavGraph.kt`; `app/src/main/AndroidManifest.xml`.
- Profile/journey: `data/repository/PhoneVerification.kt`; `ui/viewmodel/ProfileViewModel.kt`; `ui/screens/profile/ProfileScreen.kt`; `ui/screens/womensafety/WomensSafetyScreen.kt`; `ui/screens/home/SafetyCheckInScreen.kt`; `service/WomensSafetyManager.kt`, `SafetyCheckInManager.kt`.
- Trusted Circle: `service/TrustedCircleManager.kt`; `ui/screens/trustedcircle/CircleCodeDialog.kt`, `TrustedCircleScreen.kt`; `functions/circle.js`, `functions/index.js`, `functions/test/circle.test.js`, `functions/test/rules.test.js`; `firestore.rules`, `firebase.json`.
- Nearby: `mesh/MeshSecurity.kt`, `MeshPacket.kt`, `MeshPeer.kt`, `MeshStoreAndForwardQueue.kt`, `BleMeshManager.kt`; `walkie/WalkieMessage.kt`, `WalkieTalkieManager.kt`; `ui/components/FingerprintCard.kt`; `ui/screens/walkie/WalkieTalkieScreen.kt`.
- Voice/help: `data/voice/VoiceEmergencyDetector.kt`; `ui/screens/settings/EmergencyTriggersScreen.kt`, `UserManualScreen.kt`.
- QA/docs: `SafeAlertApp.kt`; `app/src/test/java/com/siddesh/smsalerts/CountdownPolicyTest.kt`; `app/src/androidTest/java/com/siddesh/smsalerts/DurableSafetyInstrumentedTest.kt`, `HomeNavigationInstrumentedTest.kt`, `ProcessRecoveryInstrumentedTest.kt`; `FINAL_HARDENING_PLAN.md`, this report, `API_SETUP_GUIDE.md`.

## Configuration and remaining release gates

See `API_SETUP_GUIDE.md` for exact Firebase providers, signing SHA certificates, SMS region/quota configuration, API key restrictions, Routes secret setup and reproducible test commands.

- Complete approved backend deployment after Blaze is available. Then use two consenting real accounts to verify invitation issuance/redemption/acceptance, one-way and mutual tracking, immediate online revocation, unauthorized reads, danger marker, FCM and incident deep-link/resolution.
- Perform real phone OTP verification. Provider setup and real SMS delivery cannot be established by code inspection or an emulator fixture.
- Two physical phones are required for direct BLE discovery, chat/SOS receipt, acknowledgements and deletion. Three are required for actual A→B→C relay with direct A→C connectivity excluded. Only one physical phone is currently connected.
- Repeat radio tests with internet disabled, permissions denied, Bluetooth off, interruption/rejoin, invalid packets, wrong groups, replay and acknowledgement loss. There is no universal reception, unlimited range or guaranteed delivery.
- Real microphone phrases, false-positive rejection and shake behavior still need physical exercise. Test carrier delivery only with consenting non-emergency contacts.
- New chat files are encrypted and separated by account/group. Legacy unscoped chat history is not silently assigned to another account; migration/retention needs explicit verification before upgrading an installation with valuable old chat history.
- Compatible peers must update together for authenticated deletion packet type 6. Older implementations can reject that new packet even if they support other v2 messages.
- Android may delay alarms/work under Doze and OEM power policies. Force-stop prevents autonomous recovery until relaunch. A location foreground service must satisfy while-in-use permission/start restrictions. Voice is foreground and requires an available on-device recognizer; no locked/force-stopped listening guarantee.
- Notification sound depends on permission, channel, DND and OS policy. Queued, submitted, peer-transferred, acknowledged and carrier-delivered remain distinct. The app does not claim emergency services were contacted merely because an incident is active.

## Deliverable

Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk` (26,322,594 bytes).

SHA-256: `F646FC75E153DDA9C48FD1179A01AEB6D6297F9F816D0545EF5B863CA3DB450E`.

This is a debug build, not a signed store-release certification. The QA app was installed separately; the phone's existing production package and account were not replaced.

The final full physical suite against the rebuilt QA APK passed all 17 tests. This validates the exercised code paths on one physical phone; it does not establish direct BLE, relay, carrier SMS, real OTP, voice recognition, real-account sharing or FCM delivery.
