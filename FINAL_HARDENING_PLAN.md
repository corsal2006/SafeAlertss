# Final hardening — 20 October 2026

Existing uncommitted implementation is retained. The two supplied archives contain 11 screenshots, not source code or crash logs.

## Dependency order

1. Emergency durability: persist cancellation deadlines, use elapsed realtime during a boot, recover through alarm/WorkManager, atomically claim dispatch, render the owner's local incident when cloud sync fails, isolate channel errors.
2. Profile and journey: genuine Firebase phone credential linking, OTP state/errors; spaced profile inputs; validated custom journey duration and explicit arrival/grace deadlines.
3. Trusted Circle: authenticated backend code issuance/redemption, 8 unambiguous characters, transactional collision checks, expiry/revocation, guessing limits, recipient acceptance; test rules for directional sharing and revocation.
4. Nearby: retain the real BLE GATT transport and encrypted signed relay protocol, show full verification fingerprints, bound/retry queues, add authenticated deletion requests and truthful acknowledgements.
5. Voice/UI/manual: preserve existing design; correct controls and fake preview data; validate final recognition only and retain explicit Android limitations.
6. Validation: incremental Android builds and behavioral tests; Firestore and callable emulator tests; emulator navigation/layout tests where available. Physical BLE, real OTP, FCM and two real account tests require available devices and configured backend.

## Initial observations

- `EmergencyManager.startCountdown` is only an in-memory coroutine; automatic triggers bypass it and screen entry defaults to 5 seconds.
- Incident screen reads category/status/location only from Firestore even when a valid owner-scoped local outbox record exists.
- Trusted Circle uses internal UIDs; no short-code backend exists.
- Phone verification is a label comparing FirebaseAuth phoneNumber with the text field; no verification action exists.
- Journey has durable session work but only four fixed duration choices.
- BLE already has AES-GCM, signed identities/relay chains, fragment assembly, replay checks and private group provisioning. It must not be represented as universal Bluetooth reception.
- Chat supports local deletion only; profile and countdown screenshots show crowding.
- No ADB devices are connected. No current crash stack trace was supplied. Historical release/performance claims are not current evidence.

Build baseline and final results are recorded separately in `FINAL_HARDENING_REPORT.md`.
