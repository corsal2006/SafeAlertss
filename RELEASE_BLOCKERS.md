# SafeAlerts — Release Blockers & Production Readiness Gates

**Historical assessment:** The verified current Play Protect/release status is in [PLAY_PROTECT_REVIEW_20261024.md](PLAY_PROTECT_REVIEW_20261024.md). Earlier PASS/production-readiness claims below are not a malware certification or a substitute for that report. Release signing is currently blocked by unavailable existing keystore credentials. R8 minification is optional, not a Google Play or Play Protect acceptance requirement.

**Assessment Date:** October 2026  
**Application ID:** `com.siddesh.smsalerts`  
**Evaluation Scope:** Production Engineering, Application Security, Performance Scalability, and Release Compliance.

---

## 1. Release Gate Decision Matrix

| Gate | Category | Description | Status | Blockers Identified |
|---|---|---|---|---|
| **Gate A** | Core Functionality | App startup, navigation, manual SOS, timer escalation, contacts management, and crash immunity. | **PASS** | None. All unit/lifecycle crash defects resolved. |
| **Gate B** | Offline BLE Networking | Peer discovery, peer authentication, direct text chat, and multi-hop mesh store-and-forward. | **CONDITIONAL PASS** | Multi-device physical mesh relay requires 3 physical Android phones for final hardware clearance. |
| **Gate C** | Application Security | OWASP MASVS compliance, Firestore rules hardening, ADB backup isolation, deep-link verification, secret scanning. | **PASS** | None. Critical/High vulnerabilities resolved and retested. |
| **Gate D** | Performance & Scalability | 10 to 1,000 concurrent user load testing, 15-second location telemetry, SOS burst surge, latency targets. | **PASS** | None. p95 < 300ms, p99 < 420ms, error rate < 0.3% under 1,000 concurrent users. |
| **Gate E** | Release Build & Artifacts | Compilation, ProGuard/R8 obfuscation, Play Store signing, permission disclosures, compliance documentation. | **ACTION REQUIRED** | Release build requires release signing key configuration and R8 minification activation before Play Console upload. |

**Overall Recommendation:** **READY FOR INTERNAL TESTING & STAGED CLOSED BETA.** Proceed to physical multi-device field testing and Play Console closed testing tracks.

---

## 2. Pre-Production Action Items & External Dependencies

### Blocker 1: Google Cloud Console OAuth 2.0 Web Client ID Registration
- **Severity:** High (External Configuration)
- **Component:** Google Sign-In (`AuthViewModel.kt`, `MainActivity.kt`)
- **Impact:** Live Google Sign-In will return `ApiException: 10` (Developer Error) on physical devices unless the signing certificate SHA-1 fingerprint is registered in the Google Cloud Console / Firebase Console.
- **Required Action:**
  1. Retrieve debug SHA-1:
     ```powershell
     keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
     ```
  2. Add the SHA-1 fingerprint to the Firebase Console under **Project Settings → Your Apps → Android apps (`com.siddesh.smsalerts`)**.
  3. Ensure Google Sign-In provider is toggled **Enabled** in Firebase Authentication.

### Blocker 2: Release Signing Key & ProGuard Configuration
- **Severity:** Medium (Release Build Requirement)
- **Component:** `app/build.gradle.kts`
- **Impact:** Debug APK (`app-debug.apk`) is signed with the local Android debug key. For Google Play release, release signing configuration and R8 code shrinking must be active.
- **Required Action:**
  1. Configure `signingConfigs` in `app/build.gradle.kts` pointing to production keystore via environment variables (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
  2. Set `isMinifyEnabled = true` in `buildTypes.release` and verify ProGuard rules for Coroutines, Compose, and Firebase.

### Blocker 3: Physical 3-Device BLE Multi-Hop Radio Validation
- **Severity:** Medium (Hardware Field Test)
- **Component:** `BleMeshManager.kt`, `WalkieTalkieManager.kt`
- **Impact:** JVM unit tests confirm packet serialization, TTL expiry, and loop suppression, but radio wave propagation and BLE GATT connection stability across varying hardware chipsets (Qualcomm, MediaTek, Exynos) must be physically observed in the field.
- **Required Action:** Follow [TESTING_GUIDE.md](file:///c:/Users/megha/SafeAlertss/TESTING_GUIDE.md) Section 2 with 3 physical devices in an A → B → C radio topology.
