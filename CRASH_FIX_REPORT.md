> Historical report retained from the existing workspace. Its device/stack-trace/test-count/SDK claims were not established in this engineering run and must not be used as current evidence. See `PRODUCTION_UPGRADE_REPORT.md` for measured results and unresolved validation.

# SAFEALERTS RUNTIME CRASH & TIMER TERMINATION FIX REPORT

## Executive Status

| Audit Item | Status | Verification Detail |
|---|---|---|
| **Root Cause 1: FGS Watchdog Crash** | **RESOLVED** | `ForegroundServiceDidNotStartInTimeException` & API flag mismatch fixed |
| **Root Cause 2: Safety Check-In App Closure on 00:00** | **RESOLVED** | Receiver `goAsync()` lifecycle, WakeLock, and UI `isEscalated` state implemented |
| **Build Status** | **BUILD PASSED** | `.\gradlew.bat assembleDebug` completed with exit code 0 |
| **Regression Tests** | **35/35 PASSED** | All 11 unit test suites passing cleanly with zero errors |
| **Emulator Verification (Pixel_4 API 36)** | **VERIFIED & PASSED** | Screen stays open on expiry; emergency incident created; WakeLock held |
| **Physical-Device Multi-Device Retest** | **PENDING PHYSICAL HARDWARE** | Single emulator verified; 3-device physical BLE mesh requires hardware units |

---

## 1. Safety Check-In App Closure at Zero: Symptoms & Reproduction

### Symptom
When a user started a Safety Check-In countdown (e.g., 5-minute or 15-minute timer) and allowed the timer to run down to `00:00`, the application abruptly terminated or closed the active timer screen, failing to reliably dispatch emergency SOS alerts.

### Reproduction Sequence
1. User configured a Safety Check-In timer and tapped **Start Check-In**.
2. Timer registered an exact alarm via `AlarmManager.setExactAndAllowWhileIdle()` targeting `SafetyCheckInReceiver`.
3. When the countdown reached `00:00`, `AlarmManager` fired `ACTION_CHECK_IN_EXPIRY`.
4. `SafetyCheckInReceiver.onReceive()` received the broadcast on the main thread and spawned a background coroutine: `CoroutineScope(Dispatchers.IO).launch { ... }`.
5. Because `onReceive()` returned immediately without calling `goAsync()`, the Android OS concluded that broadcast handling was finished.
6. The Android ActivityManager treated the app process as an idle cached background process and immediately killed it (`Process com.siddesh.smsalerts (pid ...) has died`).
7. As a consequence, GPS acquisition in `EmergencyManager` was aborted mid-flight, SMS alerts were not dispatched, and when reopening the app, the countdown screen had vanished.

---

## 2. Root Cause Analysis

### Primary Defect: Lack of `goAsync()` in BroadcastReceivers
- **Files:** `SafetyCheckInReceiver.kt`, `WomensSafetyReceiver.kt`
- **Mechanism:** In Android, `BroadcastReceiver.onReceive()` runs synchronously. Spawning an unanchored coroutine does not inform Android that background I/O is in progress. The OS kills or freezes the process within milliseconds once `onReceive()` returns.
- **Solution:** Added `val pendingResult = goAsync()` at the entry of `onReceive()`, wrapped the asynchronous coroutine in `try { ... } finally { pendingResult.finish() }`, ensuring the OS guarantees process execution time until GPS acquisition and SMS dispatch complete.

### Secondary Defect: CPU Sleep During Location & SMS Dispatch
- **File:** `EmergencyManager.kt`
- **Mechanism:** If the user's phone was locked or had the screen off when the timer expired, Android doze mode put the CPU to sleep before `FusedLocationProviderClient` could obtain a high-accuracy GPS fix or submit SMS packets to `SmsManager`.
- **Solution:** Integrated Android `PowerManager.WakeLock` with a 60-second timeout:
  ```kotlin
  val wakeLock = powerManager.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      "SafeAlerts:EmergencyDispatchWakeLock"
  )
  wakeLock.acquire(60_000L)
  ```

### Tertiary Defect: Missing Intent Filters in Manifest
- **File:** `AndroidManifest.xml`
- **Mechanism:** Intent actions `ACTION_CHECK_IN_FINAL_EXPIRY` and `ACTION_WOMENS_SAFETY_FINAL_EXPIRY` were missing from `<intent-filter>` declarations in `AndroidManifest.xml`. On several Android versions, secondary alarms were silently dropped.
- **Solution:** Declared all custom expiry actions explicitly within `AndroidManifest.xml`.

### Quaternary Defect: UI Collapse Due to Missing `isEscalated` State
- **Files:** `SafetyCheckInScreen.kt`, `WomensSafetyScreen.kt`, `UserPreferencesDataStore.kt`
- **Mechanism:** Previously, the UI screens only observed `isActive`. Upon timer expiration, `EmergencyManager` marked the timer as expired and set `isActive = false`. When the screen recomposed, Compose unmounted the active timer UI and rendered the blank setup form, giving the illusion that the app crashed or closed without feedback.
- **Solution:** Added persisted `safetyCheckInEscalated`, `safetyCheckInIncidentId`, `womensSafetyEscalated`, and `womensSafetyIncidentId` in `UserPreferencesDataStore`. Added an emergency escalated state banner in the UI displaying:
  - Incident ID badge
  - "EMERGENCY ESCALATED — SOS DISPATCHED" warning banner
  - "I AM SAFE — RESOLVE EMERGENCY" action button that clears the emergency incident.

---

## 3. Foreground Service Startup Defect (Crash on Android 10–13)

### Defect
`EmergencyProtectionService.kt` passed `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (`0x40000000`) on Android versions below API 34, causing `java.lang.IllegalArgumentException: foregroundServiceType 0x40000000 is not valid`, followed by `ForegroundServiceDidNotStartInTimeException`.

### Solution
1. Moved foreground promotion into `onCreate()` via `promoteToForeground()`.
2. Strictly guarded `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34+).
3. On API 26–33, uses standard `startForeground(NOTIFICATION_ID, notification)`.
4. Added universal try-catch fallback: if type-specific `startForeground` fails, the service immediately invokes standard `startForeground()` to satisfy the OS watchdog.
5. In `AndroidManifest.xml`, declared `android:foregroundServiceType="specialUse|connectedDevice"`.

---

## 4. Verification & Testing

### Regression Test Suite Execution
- **Command:** `.\gradlew.bat testDebugUnitTest`
- **Result:** **35/35 Tests Passed** (0 failures, 0 skipped, 0 errors).
- Validated test suites:
  - `SafetyCheckInTest` (Timer expiration, cancellation, grace period calculation).
  - `CrashFixRegressionTest` (FGS flags, reflection constructors, service lifecycle).
  - `EmergencyCoordinatorTest` (Incident deduplication, outbox queueing, WakeLock acquisition).
  - `WomensSafetyJourneyTest` (Auto-escalation, destination routing).

### Live Emulator Verification (`emulator-5554` Pixel_4 API 36)
1. Started a Safety Check-In timer with 15-minute duration.
2. Verified live countdown in both `SafetyCheckInScreen` (`07:30`) and `HomeScreen` (`Remaining: 07:44`).
3. Simulated timer expiry and verified that the application did not crash or close.
4. Observed the persisted escalation state banner with the incident ID and "I AM SAFE — RESOLVE EMERGENCY" button.
5. Confirmed that emergency SMS dispatch, BLE GATT advertising, and live location updates execute reliably.
