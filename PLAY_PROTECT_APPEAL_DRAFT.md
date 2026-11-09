# Play Protect appeal draft — not submitted

Do not submit until the final signed APK exists, its certificate is registered for authentication, and the reported warning has been captured. Use the [official Google Play Protect appeal entry point](https://developers.google.com/android/play-protect/warning-dev-guidance#appeals). Review all statements against the exact APK being submitted.

## Required evidence to complete

- Exact warning body, affected phone/Android version and installation source.
- Exact blocked APK filename and SHA-256. Do not assume an older distributed APK is identical to the repaired build.
- Final release APK download location approved by the owner, version name/code, SHA-256 and signing-certificate fingerprints. No public upload has been authorized or performed.
- Developer contact details and evidence of app ownership as requested by the form.

## Proposed explanation

SafeAlerts (`com.siddesh.smsalerts`) is a personal emergency-alert application. We request review of the reported harmful-app warning for the attached signed release and would appreciate the specific detection category if further remediation is necessary.

The user chooses saved emergency contacts and enables the safety features they want. `SEND_SMS` sends emergency messages through the user's SIM for an SOS or a user-enabled automatic trigger. It does not read incoming SMS or call history. The app discloses that carrier charges may apply. It does not act as a default SMS handler.

Location sharing runs during authorized SOS, journey or Trusted Circle sessions, with a visible foreground-service notification. Cloud access requires the recipient's membership and active sharing authorization. Microphone use supports foreground voice triggers and speech tests; the UI explains that the device's speech provider may process audio online if on-device recognition is unavailable. Bluetooth supports nearby private-group messages. These capabilities remain visible in the manifest and user controls.

The reviewed source uses Firebase for authentication/cloud data and bundled Leaflet with HTTPS OpenStreetMap tile requests. It does not declare accessibility, notification-listener, SMS inbox, call-log, all-files or APK-installation permissions. Our review found a vulnerable transitive Protobuf runtime; we upgraded it from 3.22.3 to patched 3.25.5 and reproduced that the malicious-input crash is now rejected safely. We also corrected privacy disclosures, restricted the boot receiver and explicitly disabled cleartext network traffic.

Local checks are summarized in `PLAY_PROTECT_REVIEW_20261024.md`. They are not a Google Play Protect certification. Please review the exact signed artifact identified above and advise whether the classification can be corrected or what additional issue must be addressed.

## Before sending

Replace the missing evidence with verified details, attach the final signed build only, and confirm the text accurately describes that build. Do not submit passwords, keystores, service-account credentials, real contacts, locations or emergency records. If Google Play distribution is planned, separately complete the applicable emergency-SMS permissions declaration; the exception requires Google's approval.
