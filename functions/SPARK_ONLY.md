# Local test tools and historical backend source

The Android app does not call these legacy Cloud Functions. `firebase.json` has no functions deployment entry. Do not deploy `circle.js`, `notifyEmergency`, `journeyRoute` or any other function: SafeAlerts has a strict ₹0 Firebase Spark budget.

`test/spark.test.js` exercises the new client-transaction security rules with synthetic emulator identities. `test/rules.test.js` covers private profiles, directional consent and incidents. Node packages here are development tools, not a hosted backend. Legacy callable tests are retained as historical regression fixtures and do not demonstrate Spark functionality.

Run current Spark tests only against the local emulator:

```powershell
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
node --test --test-concurrency=1 test/spark.test.js test/rules.test.js
```
