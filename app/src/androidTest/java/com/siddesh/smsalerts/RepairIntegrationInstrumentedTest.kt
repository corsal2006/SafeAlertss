package com.siddesh.smsalerts

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.siddesh.smsalerts.data.location.*
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.repository.*
import com.siddesh.smsalerts.mesh.MeshSecurity
import com.siddesh.smsalerts.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Only emulator accounts and the isolated QA app. No carrier/SOS recipient transport. */
@RunWith(AndroidJUnit4::class)
class RepairIntegrationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val password = "Synthetic-Test-1234!"
    private suspend fun account(prefix: String): Pair<String, String> {
        val email = "$prefix-${UUID.randomUUID()}@example.invalid"
        auth.signOut()
        val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
        db.document("users/$uid").set(mapOf("uid" to uid, "fullName" to "Synthetic $prefix", "dateOfBirth" to "2000-01-01")).await()
        return uid to email
    }
    @Before fun guard(): Unit = runBlocking {
        assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
        assertEquals("com.siddesh.smsalerts.qa", context.packageName)
        UserPreferencesDataStore(context).apply {
            updateDirectSms(false); updateBluetoothMeshEnabled(false); updateLocationSharing(false)
            updateShakeToAlert(false); updateVoiceAlert(false)
            cancelOrCompleteSafetyCheckIn(); cancelOrCompleteWomensSafetyJourney()
        }
    }
    @Test fun twoAccountsJoinPersistDeduplicateAndLoseAccessAfterRemoval() = runBlocking {
        val (a, emailA) = account("group-a")
        val repo = GroupRepository(context)
        val id = repo.create("Synthetic private group")
        val code = repo.invite(id)
        val (b, emailB) = account("group-b")
        val preview = repo.preview(code)
        assertEquals(a, preview.getString("ownerUid"))
        assertEquals(id, repo.accept(preview))
        assertEquals("Synthetic group-b", db.document("groups/$id/members/$b").get(Source.SERVER).await().getString("name"))
        val group = db.document("groups/$id").get(Source.SERVER).await()
        repo.activate(group)
        assertEquals(id, MeshSecurity.cloudGroupId(context))
        val message = QueuedGroupMessage(UUID.randomUUID().toString(), "Synthetic durable group message", System.currentTimeMillis())
        GroupMessageOutbox(context, b, id).add(message)
        assertEquals(message, GroupMessageOutbox(context, b, id).read().single())
        repeat(2) { repo.send(id, message.id, message.text, message.timestamp) }
        assertEquals(1, db.collection("groups/$id/messages").get(Source.SERVER).await().size())
        GroupMessageOutbox(context, b, id).status(message.id, "CLOUD_CONFIRMED")
        assertEquals("CLOUD_CONFIRMED", GroupMessageOutbox(context, b, id).read().single().status)
        auth.signInWithEmailAndPassword(emailA, password).await()
        val key = db.document("groups/$id").get(Source.SERVER).await().getString("meshKey")
        repeat(2) { repo.remove(id, b) }
        assertNotEquals(key, db.document("groups/$id").get(Source.SERVER).await().getString("meshKey"))
        auth.signInWithEmailAndPassword(emailB, password).await()
        try { db.document("groups/$id").get(Source.SERVER).await(); fail("Removed member retained cloud access") }
        catch (e: com.google.firebase.firestore.FirebaseFirestoreException) { assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code) }
        MeshSecurity.clearGroup(context)
    }
    @Test fun historyCannotExposeOrRemoveAnotherAccountsSavedIncident(): Unit = runBlocking {
        val (a, _) = account("history-a")
        val first = UUID.randomUUID().toString()
        val outbox = EmergencyOutbox(context)
        outbox.saveIncident(EmergencyIncident(incidentId = first, userId = a, state = "RESOLVED"))
        val (b, _) = account("history-b")
        val second = UUID.randomUUID().toString()
        outbox.saveIncident(EmergencyIncident(incidentId = second, userId = b, state = "RESOLVED"))
        val store = androidx.lifecycle.ViewModelStore()
        val vm = com.siddesh.smsalerts.ui.viewmodel.HistoryViewModel(context = context)
        store.put("qa-history", vm)
        try {
            vm.loadHistory()
            assertTrue(vm.alerts.value.any { it.alertId == second })
            assertFalse(vm.alerts.value.any { it.alertId == first })
            assertFalse(outbox.deleteIncident(first))
            assertNotNull(outbox.getIncident(first))
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
    }
    @Test fun onePhoneReportsActualDiscoveryWithoutClaimingPeerDelivery(): Unit = runBlocking {
        account("nearby")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (permission in listOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE")) {
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.$permission").use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
        }
        val mesh = com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(context)
        compose.runOnUiThread { MeshSecurity.createGroup(context); mesh.startMesh() }
        try {
            delay(5000)
            val report = org.json.JSONObject().put("bluetoothEnabled", mesh.isBluetoothEnabled())
                .put("scanning", mesh.isScanning.value).put("advertising", mesh.isAdvertising.value)
                .put("discoveredPeers", mesh.discoveredPeers.value.size).put("authenticatedPeers", mesh.discoveredPeers.value.count { it.isAuthenticated })
                .put("status", mesh.status.value).put("payloadsSentByTest", 0)
            File(context.filesDir, "qa-nearby-discovery.json").writeText(report.toString())
            assertFalse(mesh.transferStatus.value.values.any { it.startsWith("RECEIVED_BY_PEER") })
            if (!mesh.isBluetoothEnabled()) assertFalse(mesh.isScanning.value)
            else assertTrue("Discovery failed: ${mesh.status.value}", mesh.isScanning.value)
        } finally { compose.runOnUiThread { mesh.stopMesh(); MeshSecurity.clearGroup(context) } }
    }
    @Test fun physicalFusedFixesArePersistedAndConfirmedWithoutCarrierSms() = runBlocking {
        val (owner, _) = account("location")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (permission in listOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION", "POST_NOTIFICATIONS")) {
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.$permission").use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
        }
        val provider = LocationService(context)
        val initial = provider.getCurrentLocation()
        val report = org.json.JSONObject().put("initialResult", initial.javaClass.simpleName).put("reason", initial.explanation())
        File(context.filesDir, "qa-physical-location.json").writeText(report.toString())
        Assume.assumeTrue("Physical GPS unavailable: ${initial.explanation()}", initial is LocationResult.Success)
        initial as LocationResult.Success
        assertTrue(LocationQuality.coordinates(initial.latitude, initial.longitude))
        assertTrue(initial.accuracy >= 0)
        val id = UUID.randomUUID().toString()
        val prefs = UserPreferencesDataStore(context)
        val outbox = EmergencyOutbox(context)
        val tracker = LiveLocationManager.getInstance(context)
        outbox.saveIncident(EmergencyIncident(incidentId = id, userId = owner, state = "ACTIVE", emergencyType = "SYNTHETIC QA LOCATION"))
        db.document("incidents/$id").set(mapOf("ownerUid" to owner, "senderName" to "Synthetic QA", "senderPhone" to "",
            "policeNumber" to "", "helplineNumber" to "", "category" to "QA LOCATION", "status" to "ACTIVE",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
        try {
            prefs.updateLocationSharing(true)
            tracker.recordFix(owner, id, initial)
            // Foreground-start eligibility is part of this test's setup. OEM permission/UI changes
            // may leave the launcher on top even though ActivityScenario still owns the activity.
            instrumentation.runOnMainSync {
                context.startActivity(android.content.Intent(context, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            withTimeout(5_000) { while (!SafeAlertApp.isVisible) delay(50) }
            compose.runOnUiThread { tracker.startLiveSharing(id) }
            val twoUpdates = withTimeoutOrNull(50_000) {
                while (LocationStore(context).read(owner).count { it.session == id } < 3) delay(500)
                true
            } == true
            val samples = LocationStore(context).read(owner).filter { it.session == id }
            report.put("sampleCount", samples.size).put("continuousUpdatesObserved", twoUpdates)
                .put("cachedInitial", initial.isCached).put("source", initial.source).put("serviceRunning", EmergencyProtectionService.isServiceRunning)
                .put("sampleIntervalsMs", org.json.JSONArray(samples.zipWithNext { x, y -> y.point.timestamp - x.point.timestamp }))
            val confirmed = LocationPublisher.publish(context, owner)
            val remote = db.document("users/$owner/live_location/current").get(Source.SERVER).await()
            assertEquals(confirmed, remote.getLong("timestamp"))
            assertTrue((remote.get("sessionIds") as List<*>).contains(id))
            assertTrue(db.collection("users/$owner/alerts/$id/locations").get(Source.SERVER).await().documents.any { it.getString("ownerUid") == owner })
            report.put("cloudTimestampMatches", true)
            File(context.filesDir, "qa-physical-location.json").writeText(report.toString())
            assertTrue("Continuous callback did not deliver two further physical fixes; see qa-physical-location.json", twoUpdates)
        } finally {
            tracker.stopLiveSharing(id); prefs.updateLocationSharing(false); outbox.updateState(id, "RESOLVED")
        }
    }
}
