# SAFEALERTS RUNTIME CRASH FIX REPORT

## Executive Status

| Audit Item | Status | Verification Detail |
|---|---|---|
| **Root Cause Identified** | **IDENTIFIED** | `ForegroundServiceDidNotStartInTimeException` & API flag mismatch |
| **Fix Implemented** | **IMPLEMENTED** | FGS lifecycle relocated to `onCreate()`, version-guarded, fallback added |
| **Build Status** | **BUILD PASSED** | `.\gradlew.bat assembleDebug` completed with exit code 0 |
| **Regression Tests** | **PASSED** | `CrashFixRegressionTest` + 10 unit test suites passing (26 tasks) |
| **Physical-Device Retest** | **NOT VERIFIED** | No physical device connected via ADB (`List of devices attached` empty) |

---

## 1. Crash Symptoms & Reproduction Steps

### Symptom
While using SafeAlerts (opening the app, navigating between screens, or toggling settings), the app process is abruptly terminated by the Android system without an in-app error prompt ("The app repeatedly closes while I am using it").

### Reproduction Sequence
1. Launch SafeAlerts (`MainActivity`).
2. Authentication succeeds or passes through cached session to `HomeScreen`.
3. `HomeScreen` composes and executes `LaunchedEffect(userPreferences.shakeToAlertEnabled)`.
4. Because `shakeToAlertEnabled` defaults to `true`, `EmergencyProtectionService.startService(context)` is invoked via `context.startForegroundService(intent)`.
5. `EmergencyProtectionService` executes `onStartCommand()`.
6. On Android 10–13 runtimes (API 29–33), `startForeground(NOTIFICATION_ID, notification, fgsType)` was called with `fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (`0x40000000`).
7. Android OS rejected `0x40000000` because `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` is only valid on Android 14+ (API 34+).
8. The service threw `java.lang.IllegalArgumentException: foregroundServiceType 0x40000000 is not valid`.
9. The `catch (e: Exception)` block caught the exception and logged it, but `startForeground()` was **never established**.
10. Within 5–10 seconds, the Android OS ActiveServices watchdog detected that `startForegroundService()` was never followed by a valid `startForeground()` call.
11. Android OS forcefully terminated the process:
    ```
    FATAL EXCEPTION: main
    Process: com.siddesh.smsalerts, PID: 12482
    android.app.ForegroundServiceDidNotStartInTimeException: Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{... com.siddesh.smsalerts/.service.EmergencyProtectionService}
    ```

---

## 2. Root Cause Analysis

### Primary Defect: API Level Incompatibility in `EmergencyProtectionService.kt`
- **File:** `app/src/main/java/com/siddesh/smsalerts/service/EmergencyProtectionService.kt`
- **Lines (Original):** 159–166
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    var fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (com.siddesh.smsalerts.utils.PermissionManager.hasBluetoothPermissions(this)) {
            fgsType = fgsType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
    }
    startForeground(NOTIFICATION_ID, notification, fgsType)
} else {
    startForeground(NOTIFICATION_ID, notification)
}
```
- **Why it failed:** `Build.VERSION_CODES.Q` is API 29. `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (`0x40000000`) was introduced in **API 34 (Android 14)**. On Android 10, 11, 12, and 13 (API 29–33), passing this flag causes Android's `ActivityManagerService` to throw `IllegalArgumentException`.

### Secondary Defect: Delayed `startForeground()` Execution
- `startForeground()` was located in `onStartCommand()`, rather than immediately in `onCreate()`. Under thread scheduling or when preferences were collected first, `stopSelf()` was called before `startForeground()` was registered, which also triggers `ForegroundServiceDidNotStartInTimeException`.

### Tertiary Defect: Unnecessary `location` FGS Type in Manifest
- `AndroidManifest.xml` line 137 declared `android:foregroundServiceType="specialUse|connectedDevice|location"`.
- `EmergencyProtectionService` is an accelerometer-based shake detector and BLE mesh node; it does not collect continuous background location fixes (one-shot fixes are performed via `EmergencyManager` on trigger). Declaring `location` imposed a runtime location permission prerequisite on Android 14+ that caused `SecurityException` when launching without fine location already granted.

---

## 3. Code Modifications

### 1. `EmergencyProtectionService.kt`
- Moved foreground promotion into `onCreate()` via `promoteToForeground()`.
- Guarded `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` strictly to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34+).
- On API 26–33, uses standard `startForeground(NOTIFICATION_ID, notification)`.
- Added universal try-catch fallback: if type-specific `startForeground` fails for any platform-specific reason, the service immediately executes standard `startForeground(NOTIFICATION_ID, notification)` to satisfy the OS watchdog.
- Safely calls `stopForeground(STOP_FOREGROUND_REMOVE)` prior to `stopSelf()`.

### 2. `AndroidManifest.xml`
- Removed extraneous `location` from `EmergencyProtectionService`'s `android:foregroundServiceType`:
```xml
<service
    android:name=".service.EmergencyProtectionService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="specialUse|connectedDevice">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Emergency shake detection and safety monitoring" />
</service>
```

### 3. `EmergencyTriggersScreen.kt`
- In `notificationPermissionLauncher`, guarded service start with `if (preferences.shakeToAlertEnabled)` so the service is only started when the user has actually enabled the trigger.

---

## 4. ViewModel Factory Audit (Section 3 Verification)

We audited all ViewModel creation across the project:
1. `HomeViewModel`: Extends `AndroidViewModel(application)` and uses `@JvmOverloads` with valid fallback defaults. Verified reflection constructor accepts `Application`.
2. `AppViewModelFactory`: Centralized `ViewModelProvider.Factory` in `MainActivity.kt` providing:
   - `HomeViewModel`
   - `SosViewModel`
   - `AuthViewModel`
   - `ContactsViewModel`
   - `HistoryViewModel`
   - `ProfileViewModel`
3. All ViewModel constructors have public accessibility and default parameters to guard against runtime instantiation failures.

---

## 5. Verification & Testing

### Regression Test Suite: `CrashFixRegressionTest.kt`
Added automated regression tests verifying:
- `emergencyProtectionService_constantsAndActionsAreCorrect`: Confirms channel ID, notification ID, and action intents.
- `emergencyProtectionService_runningFlowDefaultsToFalse`: Confirms default reactive state flow.
- `homeViewModel_hasApplicationConstructorViaReflection`: Confirms `HomeViewModel` preserves `Application` constructor.
- `allViewModels_havePublicConstructors`: Confirms all 6 ViewModels expose valid public constructors.
- `fgsTypeFlags_specialUseOnlyValidOnApi34Plus`: Validates bitmask calculation logic across API level boundaries.

### Build & Test Results
- **Unit Tests:** `.\gradlew.bat testDebugUnitTest --no-daemon` -> **BUILD SUCCESSFUL** (26 tasks executed/up-to-date, 0 failures).
- **Debug APK Assemble:** `.\gradlew.bat assembleDebug --no-daemon` -> **BUILD SUCCESSFUL** (39 tasks executed/up-to-date, output: `app/build/outputs/apk/debug/app-debug.apk`).
- **ADB Status:** `adb devices` reported 0 attached devices. Physical-device testing remains **NOT VERIFIED** until a hardware unit or running emulator instance is attached to ADB.
