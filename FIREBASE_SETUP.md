# SafeAlert — Firebase Configuration & Console Guide

This document describes the exact Firebase configuration for **SafeAlert — SMS Emergency Alert System**.
The application is pre-configured with the uploaded `google-services.json` from Firebase project **`smselerts`**.

---

## 1. Firebase Project Overview

| Property | Value |
|---|---|
| **Firebase Project ID** | `smselerts` |
| **Project Number** | `663925742780` |
| **Android Application ID** | `com.siddesh.smsalerts` |
| **Storage Bucket** | `smselerts.firebasestorage.app` |
| **Web Client ID (Google OAuth)** | `663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com` |

---

## 2. Debug Signing Certificate Fingerprints (Required for Google Sign-In)

To allow **Google Sign-In** to function without developer error code 10/12500, add the following SHA-1 fingerprint to your Android app inside the Firebase Console:

1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select project **`smselerts`**.
3. Click the gear icon (**Project Settings**) in the top left.
4. Scroll down to the **Your apps** section and select the Android app (`com.siddesh.smsalerts`).
5. Click **Add fingerprint** and paste:

```text
SHA-1: 82:A4:CD:1A:26:D5:B5:35:BE:CC:F3:8A:15:7C:2F:E0:F9:40:9D:FB
```

```text
SHA-256: 6D:54:67:B0:5B:4F:FD:87:88:77:D7:63:87:03:C1:7D:86:D3:22:53:C0:DB:04:FC:F4:B5:91:A5:19:B8:BA:50
```

6. Click **Save**.

---

## 3. Enable Authentication Sign-In Providers

Inside Firebase Console, navigate to **Build** → **Authentication** → **Sign-in method**:

### A. Email/Password & Passwordless Email Link
1. Click **Add new provider** (or edit **Email/Password**).
2. Enable the **Email/Password** toggle.
3. Enable the **Email link (passwordless sign-in)** toggle.
4. Click **Save**.

### B. Google Sign-In
1. In the **Sign-in method** tab, click **Google**.
2. Toggle **Enable**.
3. Choose a **Project support email** from the dropdown.
4. Ensure the Web SDK configuration uses Web Client ID `663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com`.
5. Click **Save**.

---

## 4. Enable Cloud Firestore & Security Rules

1. Navigate to **Build** → **Firestore Database**.
2. Click **Create database** if not already created (choose any region, e.g. `asia-south1` or `us-central1`).
3. Click the **Rules** tab and paste the exact rules below:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isAuthenticated() {
      return request.auth != null;
    }

    function isOwner(userId) {
      return isAuthenticated() && request.auth.uid == userId;
    }

    // User profile document: users/{userId}
    match /users/{userId} {
      allow read, write: if isOwner(userId);

      // Trusted Contacts sub-collection
      match /trustedContacts/{contactId} {
        allow read, write, delete: if isOwner(userId);
      }

      // Legacy/compatible contacts sub-collection
      match /emergencyContacts/{contactId} {
        allow read, write, delete: if isOwner(userId);
      }

      // Alerts audit sub-collection
      match /alerts/{alertId} {
        allow read, create, update, delete: if isOwner(userId);
      }

      // Legacy/compatible alerts sub-collection
      match /emergencyAlerts/{alertId} {
        allow read, create, update, delete: if isOwner(userId);
      }
    }

    // Deny all other access
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```
4. Click **Publish**.

---

## 5. Firestore Data Structure

### `users/{userId}`
- `uid`: String (Firebase Auth UID)
- `name`: String (Full name)
- `email`: String (User email)
- `phoneNumber`: String (User phone number)
- `photoUrl`: String? (Google profile photo URL if available)
- `authProvider`: String ("email_password", "email_link", or "google")
- `createdAt`: Long (Epoch millis)
- `updatedAt`: Long (Epoch millis)

### `users/{userId}/emergencyContacts/{contactId}`
- `id`: String (Document ID)
- `name`: String (Contact name)
- `phoneNumber`: String (Emergency contact phone number)
- `relationship`: String (Relationship type, e.g., Family, Friend, Doctor)
- `isActive`: Boolean (Whether contact is included in SOS dispatch)
- `createdAt`: Long
- `updatedAt`: Long

### `users/{userId}/emergencyAlerts/{alertId}`
- `alertId`: String (Unique alert UUID)
- `emergencyType`: String (Medical, Accident, Fire, Personal Safety, Other)
- `message`: String (Dispatched SMS text)
- `latitude`: Double? (GPS latitude fix)
- `longitude`: Double? (GPS longitude fix)
- `mapsUrl`: String? (Google Maps navigation link)
- `timestamp`: Long (Dispatch epoch time)
- `recipients`: List<String> (List of phone numbers alert was sent to)
- `recipientCount`: Int (Number of recipients)
- `status`: String ("SENT", "PARTIAL", "FAILED", "CANCELLED")
- `failureReason`: String? (Detailed error explanation if any)
- `createdAt`: Long

---

## 6. How to Test the 4 Authentication Methods

1. **Email + Password Registration**:
   - Tap **Create Account** on Login screen.
   - Enter Full Name, Email, Password (min 6 chars), and Phone Number.
   - Tap **Create Account**. User document is automatically merged in Firestore and app navigates to Home.
2. **Email + Password Login**:
   - Enter registered Email and Password.
   - Tap **Login**. App authenticates and loads user dashboard.
3. **Passwordless Email Link**:
   - Tap **Sign in with Email Link**.
   - Enter your email and tap **Send Sign-In Link**.
   - Check your inbox on your test device and tap the received sign-in link.
   - The app's deep link filter opens the app and signs you in securely.
4. **Google Sign-In**:
   - Tap **Continue with Google**.
   - Select your Google account from the standard Google Play services dialog.
   - Profile photo, name, and email are automatically synchronized with Firestore.
5. **Forgot Password**:
   - Tap **Forgot Password?** on Login screen.
   - Enter email and tap **Send Reset Link**. Follow instructions in received email.
