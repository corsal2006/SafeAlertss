# SafeAlert — Testing & Validation Guide

This document outlines the rigorous testing protocol for validating the **SafeAlert SMS Emergency Alert System** on physical Android hardware and emulators.

---

## ⚠️ Important Note on Real Hardware vs Emulator
> [!IMPORTANT]
> **Actual SMS transmission must be tested on a physical Android smartphone with an active SIM card and cellular network balance/plan.**
> Android Virtual Devices (Emulators) do not possess a physical GSM baseband radio and can only simulate SMS between two emulators on local ports (e.g., 5554 to 5556).

---

## Test Suite 1: Firebase Authentication & User State

### Test 1.1: New User Registration & Validation
1. Launch SafeAlert on a device with internet access.
2. Tap **Create Account**.
3. Attempt submitting empty fields → Verify validation alerts prevent submission.
4. Enter an invalid email (e.g. `user@test`) → Verify format validation error.
5. Enter mismatched passwords → Verify "Passwords do not match" error.
6. Enter valid credentials (Full Name, valid email, 10-digit phone, 6+ character password).
7. Tap **REGISTER**.
8. **Expected Result**: User account is created in Firebase Authentication, user document is initialized under `users/{userId}` in Firestore, and the app routes seamlessly to the Home Dashboard.

### Test 1.2: User Login & Session Persistence
1. Navigate to **Profile** → Tap **LOGOUT**.
2. Relaunch the application → Verify the app directs to **LoginScreen**.
3. Enter registered email and password → Tap **SIGN IN**.
4. **Expected Result**: Successfully signs in, loads user profile name in the top greeting ("Hello, {Name}").
5. Kill the app and reopen it → Verify user remains logged in (session persisted).

### Test 1.3: Password Reset
1. From LoginScreen, tap **Forgot Password?**.
2. Enter registered email address and tap **SEND RESET EMAIL**.
3. **Expected Result**: Confirmation banner displays; check inbox for Firebase password reset link.

---

## Test Suite 2: GPS Location Subsystem

### Test 2.1: First-Run Permission Flow
1. Install a fresh build or clear app data (`adb shell pm clear com.safealert.app`).
2. Open SafeAlert → Android OS permission dialog appears requesting Location.
3. Grant **While using the app** and select **Precise**.
4. **Expected Result**: Home dashboard location indicator turns green: "✓ GPS Ready / GPS Fix Active".

### Test 2.2: Location Acquisition & Maps Link Generation
1. Trigger an emergency alert.
2. Inspect the generated location coordinates on the Completed screen.
3. Tap **Open in Google Maps**.
4. **Expected Result**: Google Maps opens with a marker pinpointing the user's exact geographical position (`https://maps.google.com/?q={lat},{lng}`).

### Test 2.3: Location Services Disabled (Negative Test)
1. Turn off **Location / GPS** in the Android Quick Settings drop-down.
2. Open SafeAlert → Status indicator changes to "GPS Off / GPS Provider Disabled".
3. Trigger SOS → App handles absence gracefully without crashing, noting "Location Unavailable (GPS Disabled)" in the message and audit log.

---

## Test Suite 3: Native SMS Subsystem & Mobile Network

### Test 3.1: Contacts Configuration
1. Go to the **Contacts** tab.
2. If empty, verify the empty state illustration and "ADD EMERGENCY CONTACT" button.
3. Tap the **+** button.
4. Enter a test contact name (e.g., "Parent"), phone number, and relationship.
5. Tap **ADD CONTACT**.
6. **Expected Result**: Contact card appears immediately with phone number and quick Call button. Contact document is saved to Firestore under `users/{userId}/emergencyContacts`.

### Test 3.2: Direct SMS Broadcast on Real Phone
1. Ensure the phone has a valid SIM card with SMS capability.
2. Trigger the SOS alert and allow the 5-second countdown to complete.
3. SafeAlert queries Android's `SmsManager`:
   - Checks if message length > 160 characters; splits into multipart SMS if needed.
   - Dispatches SMS through the cellular base station.
4. **Expected Result**:
   - Status updates to "SMS Dispatched".
   - The recipient phone receives:
     ```text
     🚨 EMERGENCY ALERT
     Type: Medical Emergency
     Message: Medical emergency! I need immediate medical assistance.
     Location: https://maps.google.com/?q=...
     Time: 01 Oct 2026, 10:30 PM
     Please contact or dispatch emergency help immediately.
     ```
   - Delivery status appears as "SMS Transmitted" in recipient breakdown.

### Test 3.3: Native SMS Fallback Handling
1. If the app is run on a device where `SEND_SMS` permission is denied:
2. SafeAlert detects the permission block and surfaces an **OPEN SYSTEM SMS APP** button.
3. Tapping it opens the device's native messaging app with the emergency numbers pre-populated in the recipient field and the alert message text pre-typed, ensuring the user is never stranded.

---

## Test Suite 4: SOS Press-and-Hold & Cancellation

### Test 4.1: Accidental Tap Prevention
1. Tap the SOS button quickly once while in "2s Hold" mode.
2. **Expected Result**: The button does not trigger immediately; it requires maintaining pressure.

### Test 4.2: Press and Hold Execution
1. Press and hold the SOS button.
2. Notice the white circular progress stroke filling the circumference over 2 seconds.
3. Feel the haptic vibration feedback when the hold completes.
4. **Expected Result**: Transitions to the 5-second countdown screen.

### Test 4.3: Countdown Cancellation
1. During the countdown (5, 4, 3, 2, 1), tap **CANCEL EMERGENCY**.
2. **Expected Result**: Timer stops immediately, no SMS is sent, no audit record is written, and the app safely returns to the dashboard.

---

## Test Suite 5: Offline Resilience & Cloud Synchronization

### Test 5.1: Offline Emergency Dispatch
1. Turn off Wi-Fi and Mobile Data (leaving only cellular voice/SMS signal active).
2. Trigger an SOS alert.
3. **Expected Result**:
   - GPS fix is acquired via device hardware.
   - Native SMS is transmitted over the GSM cellular network without requiring internet.
   - SafeAlert writes the alert record to Firestore's offline persistent cache.
4. Re-enable Wi-Fi / Mobile Data.
5. **Expected Result**: Firestore background sync uploads the cached alert to the cloud database automatically.

---

## Test Suite 6: Emergency History & Auditing

### Test 6.1: History Listing
1. Navigate to the **History** tab.
2. Verify all past triggered alerts are chronologically ordered (most recent first).
3. Confirm badges indicate SMS status ("SMS Sent", "Partially Sent", etc.) and "GPS Attached".

### Test 6.2: Alert Audit Detail & Send Again
1. Tap any alert in the history list.
2. Verify full message, exact latitude/longitude, recipients list, and Google Maps button.
3. Tap **SEND THIS ALERT AGAIN**.
4. **Expected Result**: Re-arms the emergency countdown with the exact same alert parameters.
