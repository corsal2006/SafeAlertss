# SafeAlerts — Security & Privacy Architecture

This document details the security architecture, cryptographic controls, authorization rules, and privacy protections implemented across the SafeAlerts application.

---

## 1. Threat Model & Design Principles

SafeAlerts operates in safety-critical, adversarial, and privacy-sensitive environments. The system adheres to the following principles:

1. **Zero-Trust Relaying:** Intermediary nodes in the offline BLE mesh network cannot tamper with emergency payloads or inspect a user's private contacts.
2. **Data Minimization:** No unnecessary personal data is collected or broadcast. The walkie-talkie audio recording feature, microphone controls, and `RECORD_AUDIO` / `FOREGROUND_SERVICE_MICROPHONE` permissions have been completely removed from the application.
3. **No Hardcoded Secrets:** No API keys, Google client secrets, signing keys, or server credentials are committed to the codebase.
4. **Resilient Failure Modes:** The manual SOS button and emergency coordinator never require an active internet connection, Firebase authentication, Bluetooth connectivity, or GPS lock to initiate cellular SMS dispatch to saved contacts.
5. **Durable Atomic Storage:** Incidents and outbox dispatches are committed to disk before external dispatches commence, surviving unexpected process termination.

---

## 2. Bluetooth Mesh Cryptographic Protocols

### 2.1 Packet Integrity & Authentication
Each mesh packet (`MeshPacket.kt`) carries a canonical UTF-8 signing payload:
```
version|messageId|emergencyId|senderDeviceId|timestamp|expiryTimestamp|packetType|emergencyType|messageText|latitude|longitude
```
- **HMAC-SHA256:** Packet signatures are validated using HMAC-SHA256 across this canonical representation.
- **Tampering Resistance:** If any field (coordinates, message text, sender ID, or timestamp) is modified in transit by an intermediary peer, signature verification fails and the packet is dropped immediately.
- **Clock Skew Sanity Check:** Packets with timestamps more than 24 hours in the future or older than their `expiryTimestamp` are rejected immediately.

### 2.2 Pseudonymous Device Identifiers
- Rather than broadcasting the user's real name, email, or Firebase UID over public BLE advertisements, each device generates a pseudonymous ID (`MeshSecurity.generatePseudonymousDeviceId()`):
  ```kotlin
  "SA-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
  ```
- This prevents passive BLE sniffers from tracking individuals across physical locations.

### 2.3 Store-and-Forward Flooding Protection
- **Hop Limit (TTL):** Every packet starts with a maximum hop limit of 5. Every relay decrements remaining hops. Packets reaching 5 hops are dropped.
- **Deduplication:** A thread-safe LRU cache records observed `messageId`s for 1 hour. Replayed or duplicated packets are dropped before processing or retransmission.
- **Queue Limits:** The disk-backed queue stores a maximum of 100 pending packets. Once full, the oldest expired or lower-priority packets are pruned first.

---

## 3. Trusted Circle Consent & Mutual Authorization

1. **Mutual Consent Required:** Adding a user as a phone contact or sending an invitation does NOT automatically grant access to live location data.
2. **Status Enforcement:** Location coordinates are only rendered when both the sender and recipient have an explicit `ACCEPTED` status in `TrustedCircleManager`.
3. **Instant Revocation:** A user can stop sharing their location at any time with a single tap (`stopSharingLocation()`) without deleting their account or removing their circle members.
4. **Stale Data Protection:** Any location record older than 15 minutes is clearly marked as stale with a visual warning badge to avoid misinforming family or friends during a real emergency.

---

## 4. Cloud Firestore Security Rules

Cloud Firestore security rules (`firestore.rules`) strictly enforce user isolation and least privilege:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // User profile documents: Only owner can read/write
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      // Emergency alerts: User can manage their own alerts
      match /alerts/{alertId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
        
        // Live location updates subcollection
        match /locations/{locationId} {
          allow read, write: if request.auth != null && request.auth.uid == userId;
        }
      }
      
      // Emergency contacts: Only owner can manage
      match /contacts/{contactId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }

      // Trusted Circle Locations: Only authorized circle members
      match /trusted_circle_locations/{memberId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
      }
    }
    
    // Opportunistic Mesh Gateway Ingestion
    // Participating peer with internet uploads an offline relayed SOS
    match /mesh_relayed_emergencies/{emergencyId} {
      allow create: if request.auth != null 
        && request.resource.data.emergencyId is string
        && request.resource.data.messageText is string;
      allow read, update, delete: if false; // Immutable write-only ingestion
    }
  }
}
```

---

## 5. Local Device Privacy & Permission Auditing

1. **Removal of Audio Recording:**
   - The application does not request `RECORD_AUDIO` or declare `FOREGROUND_SERVICE_MICROPHONE`.
   - No audio files, microphone streams, or speech dictation buffers exist anywhere on the device.
2. **Emergency Calling Safeguards:**
   - In accordance with Android security policies, background automated calling cannot silently dial police or emergency services without user confirmation. SafeAlerts launches the system dialer (`ACTION_DIAL`) with pre-filled numbers and copies emergency briefing text to the clipboard so the user or bystander can review and confirm.
3. **Safe Storage & Logging:**
   - Private user contacts, tokens, and location histories are stored exclusively in app-private storage (`MODE_PRIVATE`).
   - Diagnostic crash dumps sanitize sensitive credentials before writing to disk.
