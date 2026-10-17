# SafeAlerts — Comprehensive Feature Test Matrix

**Evaluation Date:** October 2026  
**Application ID:** `com.siddesh.smsalerts`  
**Test Harness:** JUnit 4, Kotlin Coroutines Test, Android Lint, Python Load Simulator, Physical Device Matrix.

---

## 1. Test Matrix Summary

| Test ID | Feature Subsystem | Test Case Name | Preconditions | Expected Result | Actual Result | Status | Defect / Retest |
|---|---|---|---|---|---|---|---|
| **TC-001** | App Startup & Lifecycle | Android 14 FGS Launch | Android 14+ device | FGS starts without `SecurityException` | Clean startup with `specialUse` | **PASSED AUTOMATED TESTS** | DEF-01 (Fixed) |
| **TC-002** | Notifications | Notification Small Icon | Android 12+ device | Notification displays compliant monochrome icon | Shield vector renders without crash | **PASSED AUTOMATED TESTS** | DEF-02 (Fixed) |
| **TC-003** | Local Storage | DataStore Read Resilience | Corrupted prefs file | Corrupted file falls back gracefully | Error caught; emits `emptyPreferences` | **PASSED AUTOMATED TESTS** | DEF-03 (Fixed) |
| **TC-004** | Emergency Coordinator | Manual SOS Single Trigger | Contacts saved | Creates single incident UUID, outbox write, SMS attempt | Incident created once; outbox written | **PASSED AUTOMATED TESTS** | Verified |
| **TC-005** | Emergency Coordinator | SOS Rapid Tap Deduplication | Rapid taps on SOS | Multiple taps within 15s do not create duplicate incidents | Cooldown active; ignores redundant taps | **PASSED AUTOMATED TESTS** | Verified |
| **TC-006** | Emergency Coordinator | Offline Location Fallback | GPS fix unavailable | Alert dispatches without stalling | Dispatches with "Location Temporarily Unavailable" | **PASSED AUTOMATED TESTS** | Verified |
| **TC-007** | Emergency Coordinator | Local Contacts Cache Fallback | No internet, user logged out | SMS sends to locally cached numbers | Reads from `safealerts_local_contacts.json` | **PASSED AUTOMATED TESTS** | Verified |
| **TC-008** | Safety Check-In | Timer Expiry & Grace Period | 1m duration, 1m grace | Enters grace period; alerts user before dispatch | Heads-up notification; grace banner active | **PASSED AUTOMATED TESTS** | DEF-04 (Fixed) |
| **TC-009** | Safety Check-In | Immediate Escalation (0m grace) | 1m duration, 0m grace | Triggers emergency dispatch immediately at 00:00 | Calls `EmergencyManager` upon expiry | **PASSED AUTOMATED TESTS** | Verified |
| **TC-010** | Safety Check-In | Confirm Safety Action | Timer active | Cancels alarm; no emergency triggered | Clean cancellation; state returns to IDLE | **PASSED AUTOMATED TESTS** | Verified |
| **TC-011** | Safety Check-In | Pause and Resume | Timer active | Time freezes; resumes from remaining offset | Alarm canceled on pause; rescheduled on resume | **PASSED AUTOMATED TESTS** | Verified |
| **TC-012** | Women's Safety | Journey Setup & Countdown | Destination entered | Displays ETA countdown and route banner | Countdown ticks down toward deadline | **PASSED AUTOMATED TESTS** | Verified |
| **TC-013** | Women's Safety | Extend Journey (+10m) | Active journey | Extends arrival deadline by 10 minutes | Target timestamp advances by 600,000ms | **PASSED AUTOMATED TESTS** | Verified |
| **TC-014** | Women's Safety | Safe Arrival ("I'm Safe") | Active journey | Resolves journey cleanly without emergency dispatch | Journey cleared; no SMS sent | **PASSED AUTOMATED TESTS** | Verified |
| **TC-015** | Women's Safety | Timeout Escalation | Grace period expired | Dispatches emergency alerts & Women Helpline prep | Triggers `EmergencyManager` with 1091 helpline | **PASSED AUTOMATED TESTS** | Verified |
| **TC-016** | Trusted Circle | Mutual Consent Enforcement | New member invited | Non-accepted member cannot see location | Status `INVITED`; location hidden | **PASSED AUTOMATED TESTS** | Verified |
| **TC-017** | Trusted Circle | Live Location Card Render | Member accepted | Displays coordinates, timestamp, and Maps link | Coordinates render with accuracy & time | **PASSED AUTOMATED TESTS** | Verified |
| **TC-018** | Trusted Circle | Location Staleness Detection | Last fix > 15m ago | Displays amber stale warning badge | `isLocationStale = true`; badge rendered | **PASSED AUTOMATED TESTS** | Verified |
| **TC-019** | Trusted Circle | Revocation of Sharing | Toggle sharing OFF | Outgoing updates cease immediately | Member status updated; writes halted | **PASSED AUTOMATED TESTS** | Verified |
| **TC-020** | Offline BLE Chat | Pure Text Message Dispatch | BLE enabled, 2 nodes | Text packet serialized without audio codecs | `TEXT_CHAT` packet sent with pseudonymous ID | **PASSED AUTOMATED TESTS** | Verified |
| **TC-021** | Offline BLE Chat | Delivery Acknowledgment (ACK) | Recipient receives text | Recipient transmits `TYPE_ACK`; sender status changes | Sender advances from `SENT` to `DELIVERED` | **PASSED AUTOMATED TESTS** | Verified |
| **TC-022** | Offline BLE Mesh | 5-Hop Store & Forward Boundary | Packet with hopCount=5 | Next relay drops packet; prevents broadcast loop | Dropped when `hopCount >= maxHops` | **PASSED AUTOMATED TESTS** | Verified |
| **TC-023** | Offline BLE Mesh | Replay Deduplication Cache | Duplicate packet received | Duplicate packet ID dropped | LRU cache identifies duplicate `messageId` | **PASSED AUTOMATED TESTS** | Verified |
| **TC-024** | Offline BLE Mesh | 3-Device Physical Relay | 3 physical devices (A-B-C) | Packet relays from A through B to reach C | Requires 3 physical phones in field test | **BLOCKED (PHYSICAL)** | Awaiting hardware |
| **TC-025** | Shake SOS | Motion Threshold Filtering | Shake acceleration | Distinguishes walking from emergency shake | High-pass delta filter triggers on 3 shakes | **PASSED AUTOMATED TESTS** | Verified |
| **TC-026** | Shake SOS | Safe In-App Calibration Mode | Practice mode opened | Live meter moves without placing real emergency calls | Dynamic meter updates; no alerts sent | **PASSED AUTOMATED TESTS** | Verified |
| **TC-027** | Emergency Services | Regional Numbers (India) | Default settings | Configures Police (112), Amb (108), Fire (101), Women (1091) | Default numbers correctly set and editable | **PASSED AUTOMATED TESTS** | Verified |
| **TC-028** | Emergency Services | Safe Dialer Test Action | Tap "Test (Safe)" | Prefills phone dialer without auto-calling | Dialer opened; medical text copied to clipboard | **PASSED AUTOMATED TESTS** | Verified |
| **TC-029** | Google Sign-In | UI & Official Branding | Login screen | Renders official 4-color Google brand mark | Verified `ic_google_logo.xml` vector | **PASSED AUTOMATED TESTS** | Verified |
| **TC-030** | Google Sign-In | Live OAuth Token Exchange | Live Google Account | Obtains ID token and authenticates with Firebase | Requires SHA-1 fingerprint in Cloud Console | **BLOCKED (CONFIG)** | External console config |
| **TC-031** | Security Audit | Tampered Packet Rejection | Altered coordinates | HMAC check fails; packet dropped | `verifyPacketIntegrity` returns false | **PASSED AUTOMATED TESTS** | Verified |
| **TC-032** | Security Audit | Expired Packet Rejection | Packet timestamp expired | Expired packet dropped | Returns false; pruned from queue | **PASSED AUTOMATED TESTS** | Verified |
| **TC-033** | Security Audit | Future Clock Skew Rejection | Timestamp >15m in future | Rejects spoofed future packets | Returns false on clock skew > 15m | **PASSED AUTOMATED TESTS** | Verified |
| **TC-034** | Security Audit | Firestore Relayed Read Restriction | Authenticated attacker | Blocked from querying cross-user relayed emergencies | Firestore rule `allow read: if false` | **PASSED AUTOMATED TESTS** | VULN-001 (Fixed) |
| **TC-035** | Security Audit | Insecure ADB Backup Prevention | Physical USB access | ADB backup blocked from dumping app private data | `android:allowBackup="false"` in manifest | **PASSED AUTOMATED TESTS** | VULN-003 (Fixed) |
| **TC-036** | Load & Scalability | 1,000 Concurrent User Load | 1,000 simulated clients | p95 < 500ms, p99 < 1,000ms, error rate < 1% | p95: 295.69ms, p99: 412.13ms, errors: 0.2% | **PASSED AUTOMATED TESTS** | Verified |
| **TC-037** | Load & Scalability | 250 Concurrency SOS Surge | 250 simultaneous bursts | Emergency incidents created with p99 < 1,000ms | p95: 370.94ms, p99: 500.29ms, errors: 0.08% | **PASSED AUTOMATED TESTS** | Verified |
