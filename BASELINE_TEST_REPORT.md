> Historical report retained from the existing workspace. Its device/stack-trace/test-count/SDK claims were not established in this engineering run and must not be used as current evidence. See `PRODUCTION_UPGRADE_REPORT.md` for measured results and unresolved validation.

# BASELINE TEST REPORT — SAFEALERTS

**Target Application:** SafeAlerts (`com.siddesh.smsalerts`)  
**Package:** `com.siddesh.smsalerts`  
**Build System:** Gradle (Kotlin DSL / Groovy wrapper), Target SDK 35, Min SDK 26, Compile SDK 35  
**Audit Environment:** Windows 11, OpenJDK 17, Android SDK platform-tools 36.0.0, Pixel_4 API 36 Emulator (`emulator-5554`)  
**Evaluation Date:** October 2026  

---

## 1. Project Inspection & Architecture Inventory

| Architectural Component | Implementation Details | Health Assessment |
|---|---|---|
| **Language & Framework** | Kotlin 1.9.22, Jetpack Compose (Material3), Coroutines 1.7.3 | Modern, declarative, fully typed |
| **Application ID & Package** | `com.siddesh.smsalerts` (Namespace: `com.siddesh.smsalerts`) | Standard Android package structure |
| **Gradle SDK Configuration** | `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` | Compliant with Google Play Store 2024–2026 requirements |
| **Navigation & Screen Stack** | Compose Navigation (`NavHost`), `Screen` sealed hierarchy (`Login`, `Register`, `Home`, `SafetyCheckIn`, `WomensSafety`, `TrustedCircle`, `NearbyChat`, `Settings`) | Type-safe route navigation |
| **State Management & DI** | `AndroidViewModel`, Kotlin `StateFlow` / `SharedFlow`, Singleton Managers (`EmergencyManager`, `LiveLocationManager`, `TrustedCircleManager`) | Decentralized singleton pattern with StateFlow observers |
| **Firebase Integration** | Firebase Auth (Email/Password, Google Sign-In with Credential Manager), Cloud Firestore | Live online authentication and cloud sync |
| **Alarm & Background Schedulers** | `AlarmManager.setExactAndAllowWhileIdle()` (`SafetyCheckInReceiver`, `WomensSafetyReceiver`) | Exact alarm scheduling with wake-from-idle permissions |
| **Location Subsystem** | `FusedLocationProviderClient`, high-accuracy priority GPS tracking, 15-second tracking interval | Live GPS acquisition with fallback to coarse location |
| **Bluetooth Subsystem** | BLE GATT Server & Advertiser, BLE Scanner, AES-GCM encrypted mesh protocol with deduplication outbox | Custom decentralized peer-to-peer mesh networking |

---

## 2. Baseline Build & Test Execution Results

Prior to structural modifications, the baseline test suite and Gradle tasks were executed.

### A. Gradle Debug Build (`.\gradlew.bat assembleDebug`)
- **Command:** `.\gradlew.bat assembleDebug`
- **Result:** **PASSED** (Exit Code 0)
- **Artifact:** `app/build/outputs/apk/debug/app-debug.apk` (Size: ~18.4 MB)
- **Observations:** Android Gradle Plugin 8.2.2 compiled with zero errors.

### B. Unit & Integration Test Suites (`.\gradlew.bat testDebugUnitTest`)
- **Command:** `.\gradlew.bat testDebugUnitTest`
- **Total Test Suites Executed:** 11 suites, 35+ test cases
  1. `EmergencyCoordinatorTest`
  2. `SafetyCheckInTest`
  3. `LiveLocationCadenceTest`
  4. `WomensSafetyJourneyTest`
  5. `BleMeshProtocolTest`
  6. `EmergencyContactsAndNumbersTest`
  7. `SecurityAndPrivacyAuditTest`
  8. `CrashFixRegressionTest`
  9. `UserPreferencesDataStoreTest`
  10. `TrustedCircleManagerTest`
  11. `IncidentHistoryTest`
- **Result:** **35/35 PASSED** (0 failures, 0 skipped, 0 errors)
- **Execution Time:** ~1m 20s

---

## 3. Baseline Defects & Critical Issues Discovered

### Defect 1: Safety Check-In App Closure on Zero Count
- **Symptom:** When countdown timer reached `00:00`, the app screen disappeared or closed abruptly.
- **Root Cause A (BroadcastReceiver Lifecycle):** `SafetyCheckInReceiver` initiated coroutines on `Dispatchers.IO` without calling `goAsync()`. Android OS synchronous broadcast lifecycle expired within milliseconds, causing the runtime to terminate the process during background GPS/SMS dispatch.
- **Root Cause B (Missing Manifest Filter):** `ACTION_CHECK_IN_FINAL_EXPIRY` was missing from `AndroidManifest.xml` intent-filters, dropping secondary alarm intents on older Android versions.
- **Root Cause C (Missing UI Escalated State):** `SafetyCheckInScreen` only checked `isActive`. When the timer expired, `isActive` flipped to `false`, immediately collapsing the countdown screen to the blank timer setup form without informing the user that an emergency had been escalated.

### Defect 2: Emergency SOS Dispatch Reliability & CPU Sleeping
- **Symptom:** Emergency messages failed to deliver if the device went into deep sleep during location acquisition.
- **Root Cause:** `EmergencyManager.triggerEmergency()` did not acquire a partial `WakeLock`. When the screen locked, Android doze mode put the CPU to sleep before `FusedLocationProviderClient` could obtain a GPS fix or submit SMS packets to `SmsManager`.

### Defect 3: Live Location Null Accuracy
- **Symptom:** Location broadcasts reported accuracy as `null` instead of real GPS metrics.
- **Root Cause:** `LiveLocationManager` had a hardcoded `accuracy = null` in the data model mapping.

### Defect 4: Add Member Dialog Keyboard Overflow
- **Symptom:** In `TrustedCircleScreen`, opening the keyboard pushed action buttons off screen.
- **Root Cause:** The `AlertDialog` content `Column` was missing `Modifier.verticalScroll(rememberScrollState())`.

### Defect 5: Missing Emergency Dialers in Settings
- **Symptom:** Emergency services section had no verified Indian numbers (112, 108, 101, 1091) or quick-dial action buttons.

---

## 4. Physical-Device Retest Verification Status

- **Android Emulator (Pixel_4 API 36):** **VERIFIED & PASSED**
  - Live app installation, Firebase sign-up, permission granting, active countdown, Women's Safety destination selection, Bluetooth GATT server start, and Trusted Circle contact picker launch verified.
- **Multi-Device Physical BLE Mesh (3+ Physical Devices):** **PENDING PHYSICAL HARDWARE**
  - Requires multiple physical Android devices with physical Bluetooth antennas. Correctly simulated via `BleMeshProtocolTest` in unit test suite.
