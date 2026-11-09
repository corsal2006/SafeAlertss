# Exact files changed in the 24 October repair

Later Play Protect/security follow-up files are listed separately in [PLAY_PROTECT_REVIEW_20261024.md](PLAY_PROTECT_REVIEW_20261024.md).

Source/test files below were compared with the starting snapshot in build/repair-baseline-hashes.json. Pre-existing working-tree changes are not attributed to this repair. The JSON companion contains SHA-256 hashes for these source files.

## Source and tests

- app/src/androidTest/java/com/siddesh/smsalerts/DeniedLocationInstrumentedTest.kt (new)
- app/src/androidTest/java/com/siddesh/smsalerts/HistoryDeletionInstrumentedTest.kt (new)
- app/src/androidTest/java/com/siddesh/smsalerts/HomeNavigationInstrumentedTest.kt
- app/src/androidTest/java/com/siddesh/smsalerts/RepairIntegrationInstrumentedTest.kt (new)
- app/src/androidTest/java/com/siddesh/smsalerts/SparkInstrumentedTest.kt
- app/src/main/AndroidManifest.xml
- app/src/main/assets/map/map.css
- app/src/main/assets/map/map.js
- app/src/main/java/com/siddesh/smsalerts/data/location/GooglePlacesSearchService.kt
- app/src/main/java/com/siddesh/smsalerts/data/location/LocationQuality.kt (new)
- app/src/main/java/com/siddesh/smsalerts/data/location/LocationResult.kt
- app/src/main/java/com/siddesh/smsalerts/data/location/LocationService.kt
- app/src/main/java/com/siddesh/smsalerts/data/location/LocationStore.kt (new)
- app/src/main/java/com/siddesh/smsalerts/data/location/OsmRoutingService.kt
- app/src/main/java/com/siddesh/smsalerts/data/model/EmergencyIncident.kt
- app/src/main/java/com/siddesh/smsalerts/data/model/TrustedCircleMember.kt
- app/src/main/java/com/siddesh/smsalerts/data/preferences/UserPreferencesDataStore.kt
- app/src/main/java/com/siddesh/smsalerts/data/repository/AlertsRepository.kt
- app/src/main/java/com/siddesh/smsalerts/data/repository/GroupMessageOutbox.kt (new)
- app/src/main/java/com/siddesh/smsalerts/data/repository/GroupRepository.kt (new)
- app/src/main/java/com/siddesh/smsalerts/data/repository/HistoryDeletionRepository.kt (new)
- app/src/main/java/com/siddesh/smsalerts/data/repository/SparkCircleRepository.kt
- app/src/main/java/com/siddesh/smsalerts/MainActivity.kt
- app/src/main/java/com/siddesh/smsalerts/mesh/BleMeshManager.kt
- app/src/main/java/com/siddesh/smsalerts/mesh/MeshPacket.kt
- app/src/main/java/com/siddesh/smsalerts/mesh/MeshSecurity.kt
- app/src/main/java/com/siddesh/smsalerts/mesh/MeshStoreAndForwardQueue.kt
- app/src/main/java/com/siddesh/smsalerts/SafeAlertApp.kt
- app/src/main/java/com/siddesh/smsalerts/service/EmergencyManager.kt
- app/src/main/java/com/siddesh/smsalerts/service/EmergencyOutbox.kt
- app/src/main/java/com/siddesh/smsalerts/service/EmergencyProtectionService.kt
- app/src/main/java/com/siddesh/smsalerts/service/EmergencyRecovery.kt
- app/src/main/java/com/siddesh/smsalerts/service/EmergencyServicesManager.kt
- app/src/main/java/com/siddesh/smsalerts/service/GroupSessionManager.kt (new)
- app/src/main/java/com/siddesh/smsalerts/service/GroupSyncWorker.kt (new)
- app/src/main/java/com/siddesh/smsalerts/service/IncidentSyncWorker.kt
- app/src/main/java/com/siddesh/smsalerts/service/LiveLocationManager.kt
- app/src/main/java/com/siddesh/smsalerts/service/LocationSyncWorker.kt (new)
- app/src/main/java/com/siddesh/smsalerts/service/TrustedCircleManager.kt
- app/src/main/java/com/siddesh/smsalerts/service/WomensSafetyManager.kt
- app/src/main/java/com/siddesh/smsalerts/ui/components/EmergencyPermissionDialog.kt
- app/src/main/java/com/siddesh/smsalerts/ui/components/FreeMap.kt
- app/src/main/java/com/siddesh/smsalerts/ui/components/SafeAlertGoogleMap.kt
- app/src/main/java/com/siddesh/smsalerts/ui/navigation/NavGraph.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/history/HistoryScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/history/IncidentScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/home/HomeScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/settings/UserManualScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/sos/EmergencyReviewScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/sos/SosCountdownScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/trustedcircle/CircleCodeDialog.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/trustedcircle/TrustedCircleScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/walkie/GroupChatScreen.kt (new)
- app/src/main/java/com/siddesh/smsalerts/ui/screens/walkie/WalkieTalkieScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/screens/womensafety/WomensSafetyScreen.kt
- app/src/main/java/com/siddesh/smsalerts/ui/viewmodel/HistoryViewModel.kt
- app/src/main/java/com/siddesh/smsalerts/utils/EmergencyMessageFormatter.kt
- app/src/main/java/com/siddesh/smsalerts/walkie/WalkieTalkieManager.kt
- app/src/test/java/com/siddesh/smsalerts/IncidentRetentionTest.kt (new)
- app/src/test/java/com/siddesh/smsalerts/LocationQualityTest.kt (new)
- functions/test/groups.test.js (new)
- functions/test/precision.test.js (new)
- functions/test/rules.test.js
- functions/test/spark.test.js

## Configuration and reports

- app/build.gradle.kts
- firebase.json
- firestore.rules
- firestore.indexes.json
- API_SETUP_GUIDE.md
- REPAIR_20261024.md
- RELEASE_REVIEW_20261024.md
- REPAIR_FILES_20261024.md (this manifest)

Generated evidence and APKs are under ignored build/.
