# SafeAlerts Spark-only implementation and verification

20 October 2026. The app now uses the existing Firebase project `smselerts` with billing disabled. The existing package, Google/email authentication configuration, theme and navigation were retained. Paid execution paths were removed or replaced. This is a tested debug build, not a claim of complete production or physical-device verification.

## Deliverable and live state

- Installable debug APK: [app-debug.apk](app/build/outputs/apk/debug/app-debug.apk), 24,099,731 bytes.
- APK SHA-256: `2587c1c508d677ab003160c558b4ef8005a42a1dd0080e025427bde2718fc317`.
- Free Firestore rules deployed successfully to `smselerts`, using the user's existing explicit deployment authorization. No Cloud Functions, hosting, new Firebase project, paid API or billing changes were made.
- Live ruleset: `projects/smselerts/rulesets/43a7e956-3a2e-4344-aea8-b9f9cdd9d1a7`.
- Live rules exactly match the locally tested file; SHA-256: `c2ee10e2b29641da4b700d0bca4975f5d5188f2ef3f7bdaa849fa67dfec147d9`.
- Read-only verification at 22:17 IST returned `billingEnabled: false`. Evidence: [deployment verification](build/spark-deployment-verification.json), [deployment log](build/spark-rules-deployment.log).
- The previous live rules were saved in [rules backup](build/smselerts-rules-before-spark.json). Existing owner-only profile/contact/alert access remains protected. Additional access is limited to the required invitation and authorized sharing features.

## What changed

| Area | Implemented behavior |
| --- | --- |
| Trusted Circle | Eight-character secure random invitation codes; 15-minute server-time expiry; regeneration, revocation, atomic single use and explicit recipient acceptance. Firestore rules enforce ten issuances and five lookup attempts per account per 15-minute window. Codes cannot grant location access themselves. |
| Sharing | Separate directional consent with a one-hour grant bound to the current relationship creation timestamp. Revoking and reaccepting cannot revive an old grant. Stale callbacks are invalidated and markers cleared. |
| Notifications | Authorized active-incident listeners enqueue Android notification work. The worker checks current server access and suppresses repeat notifications. FCM receiving remains, but there is no automatic FCM sender or killed-app delivery guarantee. |
| Phone and email | Genuine existing Firebase phone verification is retained only for the matching number. Manually entered numbers stay unverified. Real free email verification is available; it does not verify a phone number. SMS OTP requests were removed. |
| Maps and journey | Bundled Leaflet and free public OpenStreetMap tiles replace Google Maps/Places. Existing map containers, markers and journey controls remain. Long-press selects coordinates; labels are entered manually. No paid route, autocomplete, geocoding or invented ETA. Custom duration is 1–1,440 minutes with 0–60 minutes of grace. |
| Emergency dispatch | Automatic carrier SMS is disabled and SEND_SMS is absent from the main manifest. Existing incident coordination, native BLE and authorized cloud sync remain. Incident state is distinct from delivery outcomes. Manual external SMS/dialer actions remain explicit and may incur carrier charges. |
| Quota handling | One unresolved location write at a time, one-hour backoff after quota errors and bounded incident-sync retries. Exhausted retries show unconfirmed status. No paid fallback or billable TTL cleanup is configured. |
| Profile and guidance | DOB/age persistence, compact invitation/fingerprint cards, existing layout corrections and User Manual retained. Manual, setup and journey help now describe Spark restrictions and actual delivery requirements. |

The original screenshot ZIPs supplied visual evidence. The earlier physical-phone crash trace established an actual crash cause; screenshots alone did not prove the cause of every cloud error.

## Root causes and retained fixes

| Issue | Cause and correction |
| --- | --- |
| Nearby/chat crash | Removing an absent packet attempted to encrypt an empty queue without a group key. Missing removal is now a no-op, and Android Keystore regression coverage passes. Original evidence: `build/physical-original-crash-20261019.txt`. |
| Countdown inconsistency | Settings and coordinator used different preference keys and the UI defaulted to five seconds. One normalized 3/5/10-second value now drives all trigger paths. |
| Lost or duplicate incidents | UI-owned countdowns, repeated callbacks and cancellation/persistence races could lose or restart work. Persistent IDs, terminal cancellation tombstones and atomic state transitions now coordinate AlarmManager/WorkManager recovery. |
| Incident screen unavailable offline | The screen previously depended on Firestore despite owner-scoped local incident data. It now falls back to saved local data and reports cloud status separately. |
| Spark circle unavailable | The former implementation needed callable functions that cannot deploy on Spark. Client transactions now operate under tested server-enforced Firestore rules. |
| Incident creation denied | A transaction must read a missing document before creating it. Rules now allow signed-in reads of an absent incident while keeping existing incidents private to owners and authorized peers. |
| Online notification missed | Cached query results were ignored until server confirmation, but the listener omitted metadata-only changes. Including metadata updates now triggers delivery after server confirmation; the regression and full suite pass. |
| Stale sharing access | Grants were not bound to a relationship generation. Version-bound grants and callback generations prevent reacceptance or stale callbacks from restoring old access. |

Other retained work includes authenticated BLE packets, bounded store-and-forward queues, encrypted group/account-scoped chat history, deletion tombstones, shake detection, foreground on-device voice recognition, and real location/status reporting. Physical transport behavior remains subject to the checks below.

## Verification performed

| Check | Result and evidence |
| --- | --- |
| Debug build, unit tests and lint after notification fix | Successful; [build log](build/spark-build-final-r2.log). |
| Final Debug, QA and instrumentation APK rebuild after two journey-help wording corrections | Successful in 50 seconds; [delivery build log](build/spark-build-delivery.log). |
| JVM tests | **77 passed**, 15 suites; zero failures, errors or skipped tests. XML results: `app/build/test-results/testDebugUnitTest`. |
| Firestore authorization and Spark transactions | **18 passed**, zero failures; [rules test log](build/spark-all-rules-r2.log). Covers expiry, revocation, per-account budgets, single use, recipient-only acceptance, private data, directional sharing and old-grant resurrection. Expected permission-denied logs are negative-test evidence. |
| Android emulator suite | **20 passed** in 118.605 seconds; [suite log](build/spark-android-final-suite-r2.log). Includes 12 durable-safety tests, four navigation/UI tests, process recovery and three Spark integration tests. |
| Android Spark integration | Actual Android Firebase transactions with two synthetic local accounts; acceptance/revocation; one real Android notification without duplicate posting; local map markers, escaped label text and coordinate callback. All passed. External map tiles are deliberately disabled in QA. |
| Separate-process recovery on final rebuilt QA APK | Seed passed, QA app force-stopped, restore passed in a different process using the same saved account and incident IDs. Restore took 6.75 seconds. [Seed](build/spark-recovery-seed.log), [restore](build/spark-recovery-restore.log). Recovery is after relaunch, not background execution while force-stopped. |
| UI | Repeated logo taps/recreation, 200% font, profile/DOB/age, compact invitation entry and custom journey duration with keyboard were exercised by the passing suite. [Profile capture](build/spark-qa-profile-details-large.png). |
| Lint | **0 errors**, 92 warnings and two hints. Warnings remain; this is not a lint-clean release certification. [Lint report](app/build/reports/lint-results-debug.html). |
| Production deployment read-back | Exact rules match and billing disabled. No production accounts, emergency calls or carrier SMS were used for testing. |

The initial full Spark suite exposed the notification failure described above; its fix passed a focused retest and then all 20 tests. The last source change was journey-help wording only; the final APK was rebuilt and its separate-process recovery rechecked.

## Remaining limits and checks

- The user selected emulator testing. **No physical-phone validation was performed for this Spark build.** Earlier physical results in historical reports do not certify these changes.
- Two real production accounts still need end-to-end invitation, consent, sharing and notification checks against the deployed rules. Synthetic local accounts validate implementation but do not establish production account/provider behavior.
- Direct BLE SOS/chat/acknowledgement/deletion require two physical phones; actual store-and-forward relay requires three. Internet-disabled, Bluetooth-disabled, reconnect and battery/vendor behavior remain unverified physically.
- Actual GPS movement, voice recognition, shake sensitivity and locked/background notification behavior need phones. Voice is foreground-only and depends on an available Android 12+ on-device speech service. No universal offline reception or background microphone promise is made.
- Spark does not support real Firebase SMS phone authentication. Email verification is the available free account-verification flow. No automatic FCM sender has been provisioned; online listener notifications require the recipient's app to be running, an active authorized sharing grant and notification permission.
- Public OSM tiles are best effort, have no SLA and may be unavailable. This pass tested the map renderer with external tiles disabled, not live tile delivery. There is no paid fallback, bulk download or offline-map promise. The provider receives the requested map area and IP.
- Free Firestore quotas are project-wide: 1 GiB storage, 50,000 reads/day, 20,000 writes/day, 20,000 deletes/day and 10 GiB outbound/month. A 15-second location publisher uses about 240 writes/hour plus subscriber/rule-check reads. Spark quota exhaustion stops cloud operations; it does not enable billing.
- Invitation budgets are per account, not global/IP abuse prevention. Expired code records remain stored; no billed TTL feature is enabled. Authorized manual storage maintenance may eventually be needed.
- The APK is a debug build. Store signing, current store-policy compliance and full production certification were not performed. Existing user work was preserved; no Git commit or PR was created.

## Changed-file guide and configuration

The complete current working-tree inventory is in [spark-working-tree-files.txt](build/spark-working-tree-files.txt); it includes changes from earlier hardening work, not just this Spark pass.

Core Spark changes are in `firestore.rules`, `firebase.json`, both Gradle files, `AndroidManifest.xml`, `SparkCircleRepository.kt`, `TrustedCircleManager.kt`, `PhoneVerification.kt`, `EmergencyMessagingService.kt`, `LiveLocationManager.kt`, `IncidentSyncWorker.kt`, `SmsSender.kt`, `EmergencyManager.kt`, `UserPreferencesDataStore.kt`, `SafeAlertApp.kt`, `FreeMap.kt` and `assets/map/`. Existing profile, journey, circle, incident, SOS, settings, permission and manual screens were updated in place. Tests are in `SparkInstrumentedTest.kt`, the existing Android/JVM suites and `functions/test/`.

No Maps key or Cloud Functions setup is required. Preserve `app/google-services.json`, signing material and existing Auth methods. Legacy functions are retained only as inactive historical source, excluded from `firebase.json`; see [Spark setup](API_SETUP_GUIDE.md) and [functions notice](functions/SPARK_ONLY.md).

Current primary sources checked: [Firebase pricing](https://firebase.google.com/pricing), [plan requirements](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Auth limits](https://firebase.google.com/docs/auth/limits), [Firestore quotas](https://firebase.google.com/docs/firestore/quotas), [FCM trusted-server requirements](https://firebase.google.com/docs/cloud-messaging/server-environment), [Maps billing requirements](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing), [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/) and [Leaflet license/download](https://leafletjs.com/download.html).
