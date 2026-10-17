# SafeAlerts — Testing & Hardware Verification Guide

This guide describes how to verify SafeAlerts capabilities on physical Android devices, emulators, and local test setups.

---

## 1. Automated Test Suite (Desktop JVM)

All unit tests run on the desktop JVM without requiring an emulator or physical device:

```bash
# Run all unit tests
.\gradlew.bat testDebugUnitTest --no-daemon

# Run specific test suites
.\gradlew.bat testDebugUnitTest --tests com.siddesh.smsalerts.EmergencyOrchestrationUnitTest --no-daemon
.\gradlew.bat testDebugUnitTest --tests com.siddesh.smsalerts.SafetyCheckInTest --no-daemon
.\gradlew.bat testDebugUnitTest --tests com.siddesh.smsalerts.WomensSafetyUnitTest --no-daemon
.\gradlew.bat testDebugUnitTest --tests com.siddesh.smsalerts.TrustedCircleUnitTest --no-daemon
.\gradlew.bat testDebugUnitTest --tests com.siddesh.smsalerts.OfflineChatMeshUnitTest --no-daemon
```

---

## 2. Multi-Device Bluetooth Mesh Network Verification

To test genuine multi-device store-and-forward relaying, configure **3 physical Android devices**:

### Physical Test Topology:
- **Device A (Sender / Originator):** Set Airplane Mode ON. Enable Bluetooth only.
- **Device B (Relay Node):** Set Airplane Mode ON. Enable Bluetooth only. Position midway between Device A and Device C.
- **Device C (Gateway Node):** Enable Wi-Fi or Mobile Data + Bluetooth.

### Step-by-Step Test Procedure:
1. Install `app-debug.apk` on all three devices.
2. Launch SafeAlerts on each device. Grant Bluetooth & Nearby Devices permissions when prompted.
3. In **Settings → Emergency Triggers**, verify that **Offline Bluetooth Mesh Network** is toggled **ON** on all devices.
4. Move Device A out of direct radio range of Device C, but within range of Device B.
5. On Device A, tap the SOS button.
6. **Expected Behavior on Device A:**
   - Cellular SMS attempts transmission (reports cellular unavailable in Airplane Mode).
   - Emergency packet is committed to local disk outbox (`safealerts_mesh_queue.json`) and broadcast via BLE GATT.
7. **Expected Behavior on Device B (Relay):**
   - Device B discovers the packet from Device A.
   - Device B validates HMAC signature and checks deduplication cache.
   - Device B increments hop count (`hopCount = 2`) and retransmits the packet.
8. **Expected Behavior on Device C (Gateway):**
   - Device C receives the relayed packet from Device B.
   - Because Device C has internet connectivity, it uploads the emergency packet to Cloud Firestore (`mesh_relayed_emergencies/{emergencyId}`).
   - Verify document appearance in Firebase Console under `mesh_relayed_emergencies`.

---

## 3. Pure Offline Text Chat & ACK Verification

> [!NOTE]
> All microphone, audio recording, and voice walkie-talkie features have been completely removed. This screen is now dedicated exclusively to pure offline text chat and mesh messaging.

### Step-by-Step Test Procedure:
1. On Device A and Device B, tap **Offline Chat & SOS Mesh** from the Home screen.
2. Ensure both devices see each other in the "Nearby Active SafeAlerts Devices" bar.
3. Tap on the peer's chip to select them as the direct recipient (or keep "Broadcast to All Nearby").
4. **Text Chat & Delivery Status:**
   - Type `"Checking offline mesh connection"` in the message input and tap Send.
   - Device A immediately marks the message with a blue `"SENT"` tag.
   - When Device B's radio receives the packet, it renders the message with Device A's pseudonymous ID.
   - Device B automatically transmits a `TYPE_ACK` packet back to Device A.
   - Upon receiving the ACK, Device A updates the message status tag to green `"DELIVERED"`.

---

## 4. Safety Check-In Timer & Grace Period Verification

Verify the repaired background escalation workflow without waiting hours:

1. On the Home screen, tap **Safety Check-In**.
2. Select **Duration:** Choose `15 min` or tap Custom and enter `1 min` for rapid testing.
3. Select **Grace Period:** Choose `1 min` (or `0 min` for immediate escalation).
4. Tap **"Start Safety Check-In"**.
5. Press the device Home button or lock the screen to place the app in the background.
6. **Expiry Stage 1 (Initial Duration Expires):**
   - When the timer reaches 00:00, `SafetyCheckInReceiver` triggers an urgent heads-up notification: `"Safety Check-In Expired! Are you safe? 1m grace period remaining."`
   - Reopen the app: the screen displays the orange **Grace Period Active** banner with a countdown.
7. **Expiry Stage 2 (Escalation):**
   - **Scenario A (User confirms safe):** Tap `"I'm Safe (Check In)"`. The timer cancels and no emergency is triggered.
   - **Scenario B (Timeout):** Do not tap. Once the grace period expires, `EmergencyManager.triggerEmergency()` authoritatively creates an emergency incident, sends SMS alerts to saved contacts, and begins location sharing.

---

## 5. Women's Safety Journey Verification

1. On the Home screen, tap **Women's Safety Journey**.
2. Enter a destination name (e.g., `"City Center Metro"`).
3. Select an estimated duration (e.g., `15 min`).
4. Select a grace period (e.g., `2 min`).
5. Tap **"Start Safe Journey"**.
6. **Active Journey Controls:**
   - Verify the countdown displays remaining journey time.
   - Tap **"+10 Min (Extend)"**: verify the target arrival time shifts forward by 10 minutes.
   - Tap **"I'm Safe (Arrived)"**: verify the journey resolves cleanly without dispatching alerts.
   - **Timeout Escalation:** If the journey and grace period expire without confirmation, the app initiates emergency dispatch to trusted contacts and prepares Women's Helpline (1091) escalation.

---

## 6. Trusted Circle Mutual Consent & Location Verification

1. On the Home screen, tap **Trusted Circle**.
2. Tap **"+ Invite Member"** and enter a friend's name and phone number.
3. Notice the member card appears with status `"Pending Invite"`.
4. In test mode, tapping the status chip toggles between `INVITED`, `ACCEPTED`, and `REJECTED`.
5. When set to `ACCEPTED`:
   - Member card displays live coordinates, accuracy, and last update timestamp.
   - Tap **"Open in Google Maps"** to launch directions to their coordinates.
   - If a member's location is older than 15 minutes, verify the amber `"Location data may be stale (>15 min ago)"` badge appears.
6. Toggle **"Share My Live Location with Circle"** off: verify your location updates cease immediately.

---

## 7. Shake-to-Trigger SOS Practice & Calibration Mode

1. Navigate to **Settings → Emergency Triggers**.
2. Scroll to the **Shake to Trigger SOS** card and toggle it **ON**.
3. Tap **"Open Shake Calibration & Practice Mode"**.
4. Shake the phone gently, moderately, and vigorously.
5. **Observe:**
   - The real-time acceleration force bar moves dynamically.
   - The threshold indicator displays your configured sensitivity (e.g., 18.0 m/s²).
   - When your shake exceeds the threshold, the shake counter increments.
   - When 3 shakes are detected in succession, the simulator displays `"SIMULATED SOS TRIGGERED"`.
   - **No actual SMS or emergency calls are placed** while inside Calibration Mode.

---

## 8. Emergency Services Safe Dialer Verification

> [!WARNING]
> **NEVER auto-dial real emergency numbers (112, 108, 101, 1091) during testing.**

1. On the Home screen or in Emergency Services settings, locate:
   - **Police (112)**
   - **Ambulance (108)**
   - **Fire Brigade (101)**
   - **Women's Helpline (1091)**
2. Tap **"Test (Safe)"** on any service.
3. **Verify:**
   - The Android phone dialer opens with the number pre-filled.
   - The call is NOT automatically placed; the dial button requires manual user press.
   - An emergency briefing containing your name, medical notes, blood group, and GPS coordinates is copied to your clipboard.
