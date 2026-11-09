# SafeAlerts ₹0 implementation plan — 20 October 2026

This requirement supersedes earlier Blaze deployment instructions. Keep project `smselerts`, package and existing Google/email authentication configuration. Never provision billing, paid APIs, hosting, Cloud Functions or subscriptions. Necessary free Firestore rule changes require validation and deployment authorization.

Inspection found active Firebase callable functions for circle invitations and routing, Firebase SMS phone verification, Google Maps/Places SDKs, and automatic carrier SMS. These do not meet the requested budget. Existing native countdown, BLE, chat, shake, voice, location, profile, incident persistence and crash fixes remain.

| Area | Free implementation | Practical limit |
| --- | --- | --- |
| Circle invitations | Firestore transactions, random short codes, server-time expiry, one-time redemption, explicit acceptance, enforced per-account issuance/lookup budgets | Rules cannot enforce IP/global account-creation rate limits. No claim of server-equivalent abuse protection. |
| Sharing | Directional, expiring grants bound to the relationship creation timestamp | Old grants need explicit re-enabling; revocation/reacceptance cannot revive them. One-hour sharing sessions reduce writes. |
| Notifications | Authorized Firestore listener feeding Android notification worker; retain FCM receiving capability | No automatic FCM sending without a trusted server. App must be running and connected for listener delivery; no killed-app urgent-delivery guarantee. |
| Phone | Preserve genuine Firebase verified-number status, manually saved numbers explicitly unverified; email verification available | Firebase real SMS verification is unavailable on Spark. No fake phone verification. |
| Maps | Bundled BSD-licensed Leaflet and public OpenStreetMap raster tiles in existing map containers | Best effort, no SLA; identified client, browser HTTP cache, visible attribution, viewport-only requests. No downloads/prefetch. No paid autocomplete/route/ETA. Manual destination/deadline. |
| Emergency transport | Native BLE and authorized Firestore incident sync | Automatic carrier SMS disabled because charges cannot be guaranteed ₹0. No universal offline reception. |
| Scheduling/location | Existing AlarmManager/WorkManager, native location with visible foreground service | OS permission, battery and force-stop restrictions remain. |

Firestore Spark quotas are shared across the project: 1 GiB storage, 50,000 reads/day, 20,000 writes/day, 20,000 deletes/day and 10 GiB outbound/month. A 15-second publisher uses about 240 location writes/hour; each subscriber also consumes reads. Rules access checks can incur additional reads. No unlimited tracking claim, paid fallback, TTL billing feature, backup or PITR. Quota exhaustion must be reported and retries bounded/backed off. Device/network/provider costs outside this app cannot be guaranteed; use existing connectivity.

Implementation order: remove billable execution paths and deployment configuration; implement and adversarially test Spark circle rules and Android transactions; replace maps; add local incident notifications and honest limits; update setup/manual; build, unit/rules tests and available device QA. Review new live rules and confirm deployment authorization after local verification.

Completion update: the user selected emulator testing. The tested free Firestore rules were deployed to `smselerts` using the user's existing explicit deployment authorization, announced before deployment. No functions were deployed. Read-back confirmed an exact source match and billing disabled. See [SPARK_ZERO_COST_REPORT.md](SPARK_ZERO_COST_REPORT.md) for results and remaining physical-device checks.

Current primary sources checked on 20 October 2026:

- [Firebase pricing](https://firebase.google.com/pricing)
- [Firebase plan details](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans)
- [Authentication limits](https://firebase.google.com/docs/auth/limits)
- [Firestore quotas](https://firebase.google.com/docs/firestore/quotas)
- [FCM trusted server requirements](https://firebase.google.com/docs/cloud-messaging/server-environment)
- [Maps SDK billing requirements](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing)
- [OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/)
- [Leaflet downloads and license](https://leafletjs.com/download.html)
