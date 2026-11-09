package com.siddesh.smsalerts.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.ui.components.GlassCard

@Composable
fun UserManualScreen(onBack: () -> Unit) {
    val sections = listOf(
        "Complete your profile" to "Open your Home avatar, then Edit. Enter your name and select a birth date. Future and invalid dates are rejected. Address and phone are optional. Save requires server confirmation; emergency features remain accessible if you complete these later.",
        "Phone and email verification" to "Phone verification via SMS OTP is not enabled in this configuration. Manually saved phone numbers remain unverified. In Profile, Verify email requests a verification link; after opening it, tap Refresh verification.",
        "Age and privacy" to "Age is calculated from your saved birth date and today's date. It cannot be edited separately. Emergency messages include age only when available. Your birth date and home address remain private.",
        "Emergency contacts" to "Save actual contacts for manual contact. Automatic carrier SMS is disabled to avoid charges. Internet circle notifications use accepted, unexpired directional sharing grants, not your phone contact list.",
        "Emergency numbers" to "Open Settings → Emergency Services & Numbers. Verify the correct local numbers with the service and enter them. General Check-In messages use police/emergency and ambulance settings. Journey messages can also include the configured Women's Safety helpline. Call actions open the dialer for you to confirm.",
        "Start or cancel Check-In" to "Choose a duration and Immediate, 1 min, 2 mins or 5 mins grace. Start the timer. Confirm I'm Safe or cancel before expiry. The deadline is saved on your phone. Exact-alarm access improves timeliness; Android can delay the fallback scheduler.",
        "What expiry does" to "After the saved deadline plus grace, the app creates one incident and attempts enabled channels automatically. It retrieves location with a timeout and keeps processing if GPS is unavailable. An incident being active does not mean anyone received it. Resolve the incident when safe.",
        "Women's Safety Journey" to "Enter a destination label and optionally long-press the free map to choose coordinates. Preset labels do not identify your home or office. Choose a duration (1–1,440 minutes) and grace (0–60 minutes); check the exact deadline before starting. No address autocomplete, route or travel ETA is provided in ₹0 mode.",
        "Arrival and extensions" to "Use +10 min or +15 min to extend your saved deadline. Confirm arrival when safe. Cancel if the journey is abandoned. Changing or cancelling a session invalidates older expiry callbacks. A GPS fix is not an automatic arrival confirmation.",
        "Trusted Circle invitations" to "Open Trusted Circle → Add Member. Generate the eight-character code and copy or share it privately. The other signed-in user chooses Enter Code, then reviews the inviter and accepts or rejects. Codes expire after 15 minutes, are single-use, and can be revoked or regenerated. A code never grants location access by itself.",
        "Groups and cloud chat" to "Open Offline Chat → Groups & cloud history. Create a group, issue a single-use 32-character invitation and have the other signed-in user check and accept it. Select Use for nearby chat on each phone, then Start Nearby when you want Bluetooth transfers. Online messages keep their IDs when retried. Cloud confirmed means the server stored a message; only a nearby acknowledgement proves peer receipt. Removing a member rotates the private channel key. A member leaving pauses sending until the owner reconnects and rotates it. Saved offline copies cannot be recalled.",
        "One-way location sharing" to "After acceptance, turn on Share My Live Location for each chosen member. The other person chooses independently whether to share back. Grants expire after one hour. Re-enable explicitly when needed. Stop All Sharing or remove a member to revoke. Internet is required to confirm remote revocation; publishing stops locally while that change is pending.",
        "Circle notifications and quotas" to "Circle alerts can appear while this app is running and connected with an active sharing grant. Direct SMS is recommended for immediate dispatch when offline or in critical emergencies. Cloud sync quotas apply.",
        "Free map limitations" to "Maps use OpenStreetMap, with visible attribution and normal HTTP caching. Tile availability is best effort; no offline map downloads are offered. The provider receives the viewed area and your IP address. Names, incident text and destination labels stay in the local map renderer. Coordinates remain usable when tiles are unavailable.",
        "Offline SOS compatibility" to "Offline SOS attempts encrypted BLE transfers to compatible SafeAlerts v2 participants in your private nearby group. An ordinary Bluetooth phone cannot read the protocol. No app-free universal delivery is supported. A discovered device is not proof of receipt.",
        "Offline Chat" to "Both phones need compatible SafeAlerts software and the same private group code. Choose Offline Chat, select an authenticated peer or your group, and send text. Messages queue while no receiver is reachable. No voice recording or walkie-talkie audio is used.",
        "Connect nearby phones" to "Enable Bluetooth and grant scan/connect/advertise permissions. Create a private nearby group on one phone and enter its full code on the others. Everyone holding the code can read group packets. Compare all characters of the full verification fingerprints in person; copy preserves the complete value. Use Connect / Retry if needed. Names are self-reported.",
        "Multi-hop relay" to "Compatible group participants may store and forward SOS packets. Signatures, expiry, duplicate suppression and a maximum of five hops limit forwarding. A private queue holds up to 12 packets within its size bound. Range, interference, battery and Android background limits affect delivery; there is no unlimited range guarantee.",
        "Shake SOS" to "Enable Shake SOS in Emergency Triggers. Five qualifying shake peaks within three seconds trigger the shared emergency workflow, with debouncing and a cooldown. Normal motion can still cause false triggers. Use practice mode with protection off before enabling it. Background sensor availability varies by phone.",
        "Cancellation window" to "Choose 3, 5 or 10 seconds in Emergency Triggers. Manual, shake, voice and Offline SOS triggers use this saved window. Tap I'm Safe before expiry to cancel. Check-In and Journey expiry follow their own deadline and grace. Once dispatch starts, resolve the incident when safe; sent messages cannot be recalled.",
        "Voice trigger" to "Enable microphone access and Voice Trigger, then keep Home visible. Say help me or a saved custom phrase. Add phrases of 2–8 words; use the safe speech test first. Custom phrases are encrypted on this device and can be removed. On-device recognition requires a supported Android 12+ speech service. Otherwise your phone's speech provider may process audio online. Listening stops when the screen leaves the foreground; locked or force-stopped operation is not guaranteed.",
        "Permissions" to "Grant SMS, location, nearby devices, microphone and notifications when prompted. Automatic SMS requires the SEND_SMS permission to transmit direct emergency messages to your saved contacts. Location sharing uses a visible foreground-service notification.",
        "Unavailable services" to "Missing GPS is shown as unavailable or with the last fix time. Internet maps and cloud sync cannot refresh offline or when the free quota is exhausted. Bluetooth off or absent compatible peers leave nearby messages queued or failed. Notification permission, sound settings and Android battery restrictions can prevent alerts.",
        "Understand statuses and deletion" to "Queued means stored locally. SUBMITTED means handed off to Android telephony. SENT_TO_CARRIER means the cellular network accepted the message. DELIVERED means the carrier reported delivery to the recipient device. FAILED indicates radio, SIM, or carrier errors. Delete locally removes your copy. Offline phones may receive queued messages when connection is restored.",
        "Test safely" to "Use consenting test contacts and synthetic emulator accounts. Disable emergency triggers during UI practice and never dial emergency services for testing. Verify direct BLE on two phones and relay on three. Builds and emulator tests do not prove physical radio or remote delivery.",
    )
    Scaffold(topBar = { SafeAlertTopBar("User Manual", true, onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(sections) { index, section -> GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${section.first}", style = MaterialTheme.typography.titleMedium)
                    Text(section.second, style = MaterialTheme.typography.bodyMedium)
                }
            } }
        }
    }
}
