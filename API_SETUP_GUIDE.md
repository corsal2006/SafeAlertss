# SafeAlerts — Firebase Spark / ₹0 setup

The budget is ₹0. Keep Firebase project `smselerts` on Spark. Do not provision billing, paid APIs, Cloud Functions or subscriptions. These instructions replace earlier billing-dependent setup guidance.

## Existing configuration

Keep `app/google-services.json`, the package `com.siddesh.smsalerts` and the existing Google/email sign-in methods. Do not regenerate signing keys. No Maps key is needed. The Android app has no Functions, Maps or Places SDK dependency. Existing secrets files are ignored and untouched.

Release signing now reads the ignored `signing.properties` or `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` environment variables. Use `tools/build-release.ps1` after restoring the existing credentials. Do not distribute the unsigned review APK or the earlier debug build. See [PLAY_PROTECT_REVIEW_20261024.md](PLAY_PROTECT_REVIEW_20261024.md) for the verified security changes and the key-migration decision if credentials cannot be recovered. A different signing certificate requires corresponding Firebase/Google sign-in registration and may prevent updates to existing installations.

Real Firebase SMS phone verification is unavailable on Spark. Profile shows manually entered phones as unverified and preserves genuine existing Firebase phone verification only for the matching number. Email verification uses Firebase's free supported email flow and its sending limits; it is not phone verification. No provider configuration change is required by the code.

## Circle rules and schema

The 24 October repair adds validated location metadata, private groups and owner-confirmed terminal-incident deletion to `firestore.rules`, plus `firestore.indexes.json`. The approved rules/index deployment succeeded; read-back exactly matched the tested rules and confirmed `billingEnabled: false` for `smselerts`. See `RELEASE_REVIEW_20261024.md` for the release evidence and `REPAIR_20261024.md` for device-test limits. The legacy callable backend remains disabled; never deploy the legacy functions.

| Collection | Access and purpose |
| --- | --- |
| `spark_circle_code_owners/{uid}` | Owner-only pointer and generation budget; up to 10 codes per 15-minute window |
| `spark_circle_codes/{sha256}` | Short-lived invitation; exact-hash lookup ticket required for peers; no listing; single-use |
| `spark_circle_lookups/{uid}` | Server-time per-account lookup budget, five per 15-minute window; no deletion/reset |
| `circle_relationships/{sha256(sorted UIDs)}` | Participants only; atomic redemption, recipient-only acceptance, either participant revocation |
| `users/{uid}/circle_members/{peer}` | Owner-controlled directional grant; accepted relation, one-hour expiry, matching `relationshipCreatedAt` required |
| `users/{uid}/live_location/current` | Owner writes; only current authorized peers read |
| `incidents/{id}` | Limited owner-created summary; authorized peer reads; owner-only state transitions |
| `users/{uid}` | Private profile; never shared with peers |
| `public_profiles/{uid}` | Signed-in exact lookup of display name/photo projection; no enumeration |
| `groups/{id}/members/{uid}` and `groups/{id}/messages/{id}` | Private group profiles and immutable ordered messages; current membership required |
| `group_invitations/{hash}` | Expiring single-use invitations with atomic membership acceptance |
| `users/{uid}/deleted_incidents/{id}` | Immutable owner-only opaque deletion ID/time, preventing delayed data restoration |

Invitation lifetime is 15 minutes using server time, not a caller-supplied expiry. Regeneration invalidates the old code; revocation and expiry prevent pending acceptance. Existing directional grants lack the new relationship version and must be explicitly re-enabled. Removing/reaccepting a member cannot reactivate an old grant. Account budgets are enforced by rules; there is no IP/global abuse limiter on Spark. A determined attacker creating many accounts can consume free quota. No unlimited scale or abuse-proof guarantee is made.

Expired codes are logically inaccessible for redemption but retained in Firestore. No billable TTL policy is configured. Monitor storage and remove obsolete records manually only with an authorized maintenance operation; never delete active owner/lookup budgets to reset limits.

History can permanently delete an owner's ended incident and saved locations after confirmation. Partial errors retain a retry path; previously received copies cannot be recalled. Only the opaque deletion ID/time remains. New location writes require an incident/session ID, including circle/journey sessions; older clients without that field must upgrade. Existing data remains readable under its original consent boundary. Account-wide deletion and automatic group-history retention are not implemented.

## Notifications and location

FCM receiving remains supported, but there is no automatic FCM sender in this architecture. Do not put service-account credentials in the APK. An authorized Firestore listener enqueues Android notification work for active peer incidents, verifies access again and suppresses repeat notifications. The app must be running and online; killed-app, force-stopped, offline and locked-screen urgent delivery is not guaranteed. Notification permission, sound and battery settings still apply.

Sharing targets a 15-second fused-provider cadence during authorized foreground location sessions. A single active incident uses at most roughly 240 latest-location writes/hour plus 60 minute-history writes/hour. Transactions also read the latest fix, incident and minute slot, and authorized subscribers incur reads. History uses 60 rolling minute slots per incident. The local encrypted journal holds at most 240 fixes and removes samples older than 24 hours when new fixes are saved. WorkManager retries a bounded set of latest fixes with exponential backoff; it does not drive sampling. Quota exhaustion prevents cloud confirmation while local sampling continues. An active sample stream may schedule work again after a previous worker exhausts retries; do not assume a project-wide abuse or cost governor. Spark billing remains disabled and there is no paid fallback.

Firestore free quotas are project-wide: 1 GiB stored data, 50,000 reads/day, 20,000 writes/day, 20,000 deletes/day and 10 GiB monthly outbound transfer. Daily quotas reset around midnight Pacific. Spark stops operations on quota exhaustion; there is no paid fallback. No TTL, backup, restore, clone or point-in-time recovery features are enabled. Network access still requires the user's existing connectivity.

## Free maps

Leaflet 1.9.4 is bundled under its BSD license in `app/src/main/assets/map/LEAFLET-LICENSE.txt`. JS/CSS hashes were verified against the official download page. Only trusted bundled scripts execute; native bridge inputs are validated. WebView blocks file/content access and navigation to arbitrary pages.

Public OpenStreetMap raster tiles are HTTPS, with a stable SafeAlerts User-Agent, normal browser HTTP caching and visible attribution. No prefetch, bulk downloads or offline maps. Tile availability has no SLA and access can be withdrawn. The tile provider sees requested map area and IP, not local marker names or incident text. No paid fallback is used. QA defaults to offline map tests; one explicit instrumentation test enables normal tile loading. The app uses manually entered labels, coordinates and map destination selection; no paid autocomplete, geocoding, route or ETA service is called.

## Emergency transports

The main app supports native SIM/carrier SMS after contextual permission and user configuration. It validates recipients and records submission and delivery-report callbacks separately; carrier charges may apply outside the app. The QA manifest removes SEND_SMS and its runner denies the SMS app operation, so automated tests cannot send carrier messages. Public helpline buttons use a prefilled native dialer and never claim a connected call. Native BLE, encrypted chat, local timers and authorized Firestore incident sync remain available subject to their actual connectivity/permission limits. Offline reception requires compatible SafeAlerts devices in the same private group.

## Local verification

Use Java 17 for Android builds and the Android Studio JBR for current Firebase CLI emulators. Start only local Auth/Firestore emulators under the synthetic project `demo-safealerts`:

```powershell
firebase emulators:start --only auth,firestore --project demo-safealerts
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
node --test functions/test/*.test.js
.\gradlew.bat assembleDebug testDebugUnitTest assembleQa assembleQaAndroidTest lintDebug
```

The separate QA package uses local Firebase only and defaults to disabled external map tiles; the dedicated basemap test explicitly loads ordinary OSM tiles. Connect physical QA devices using explicit ADB reverse ports 8080 and 9099. Run Node rules tests separately from Android tests because rules tests reset emulator data. Two synthetic accounts in an emulator test do not establish real-account deployment, BLE or background-delivery behavior. Direct BLE requires two physical phones; true relay requires three. Never test by calling emergency services or sending real SMS.

Current sources checked 20 October 2026: [Firebase pricing](https://firebase.google.com/pricing), [plan requirements](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Auth limits](https://firebase.google.com/docs/auth/limits), [Firestore quotas](https://firebase.google.com/docs/firestore/quotas), [FCM server requirements](https://firebase.google.com/docs/cloud-messaging/server-environment), [OpenStreetMap policy](https://operations.osmfoundation.org/policies/tiles/), [Leaflet](https://leafletjs.com/download.html).
