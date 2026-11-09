package com.siddesh.smsalerts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.repository.UserRepository
import com.siddesh.smsalerts.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DurableSafetyInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = UserPreferencesDataStore(context)
    @Before fun prepare() = runBlocking {
        assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
        assertNotEquals(android.app.AppOpsManager.MODE_ALLOWED, context.getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow("android:send_sms", android.os.Process.myUid(), context.packageName))
        FirebaseAuth.getInstance().signOut()
        FirebaseAuth.getInstance().createUserWithEmailAndPassword("qa-${UUID.randomUUID()}@example.invalid", "Synthetic-Test-1234!").await()
        prefs.updateDirectSms(false); prefs.updateBluetoothMeshEnabled(false); prefs.updateLocationSharing(false)
        prefs.cancelOrCompleteSafetyCheckIn(); prefs.cancelOrCompleteWomensSafetyJourney()
    }
    @Test fun duplicatedExpiryCreatesOnePersistentIncidentWithoutSms() = runBlocking {
        val deadline = System.currentTimeMillis() - 120_000
        prefs.startSafetyCheckIn(1, deadline, 1)
        val session = prefs.preferencesFlow.first().checkInSessionId
        repeat(10) { assertEquals("EXPIRED", prefs.advanceTimer(false, session, deadline, System.currentTimeMillis())) }
        val manager = EmergencyManager.getInstance(context)
        repeat(2) { manager.triggerEmergencySuspending("TIMER_EXPIRY", forcedIncidentId = session) }
        val persisted = EmergencyOutbox(context).getAllIncidents().filter { it.incidentId == session }
        assertEquals(1, persisted.size)
        assertEquals("ACTIVE", persisted.single().state)
        assertTrue(persisted.single().recipientStatuses.isEmpty())
        assertFalse(persisted.single().messageText.contains("Women's Safety"))
    }
    @Test fun cancellationAndNewSessionRejectStaleCallbacks() = runBlocking {
        val deadline = System.currentTimeMillis() - 1000
        prefs.startSafetyCheckIn(1, deadline, 0)
        val old = prefs.preferencesFlow.first().checkInSessionId
        prefs.cancelOrCompleteSafetyCheckIn()
        assertEquals("INACTIVE", prefs.advanceTimer(false, old, deadline, System.currentTimeMillis()))
        prefs.startSafetyCheckIn(5, System.currentTimeMillis() + 300_000, 2)
        assertEquals("INACTIVE", prefs.advanceTimer(false, old, deadline, System.currentTimeMillis()))
        assertTrue(prefs.preferencesFlow.first().safetyCheckInActive)
    }
    @Test fun delayedCallbackKeepsOriginalGraceAndExtensionInvalidatesOldDeadline() = runBlocking {
        val now = System.currentTimeMillis(); val deadline = now - 30_000
        prefs.startWomensSafetyJourney("Synthetic destination", 1, deadline, 2)
        val session = prefs.preferencesFlow.first().journeySessionId
        repeat(10) { assertEquals("GRACE", prefs.advanceTimer(true, session, deadline, now)) }
        assertEquals(deadline + 120_000, prefs.preferencesFlow.first().womensSafetyGracePeriodTargetTimestamp)
        prefs.extendWomensSafetyJourney(now + 600_000)
        assertEquals("INACTIVE", prefs.advanceTimer(true, session, deadline, now + 200_000))
        prefs.cancelOrCompleteWomensSafetyJourney()
        assertEquals("INACTIVE", prefs.advanceTimer(true, session, now + 600_000, now + 900_000))
    }
    @Test fun notificationFromOldJourneyCannotConfirmReplacement() = runBlocking {
        val now = System.currentTimeMillis()
        prefs.startWomensSafetyJourney("Synthetic A", 1, now + 60_000, 2)
        val old = prefs.preferencesFlow.first().journeySessionId
        prefs.cancelOrCompleteWomensSafetyJourney()
        prefs.startWomensSafetyJourney("Synthetic B", 1, now + 90_000, 2)
        prefs.confirmTimer(true, old, now + 60_000)
        assertTrue(prefs.preferencesFlow.first().womensSafetyActive)
    }
    @Test fun concurrentOutboxInstancesReserveRecipientOnceAndPreserveCallbacks() = runBlocking {
        val id = UUID.randomUUID().toString()
        EmergencyOutbox(context).saveIncident(EmergencyIncident(incidentId = id, userId = FirebaseAuth.getInstance().currentUser!!.uid))
        val reservations = (1..30).map { async(Dispatchers.IO) { EmergencyOutbox(context).reserveRecipient(id, "synthetic-recipient") } }.awaitAll()
        assertEquals(1, reservations.count { it })
        val outbox = EmergencyOutbox(context)
        outbox.recordSmsPart(id, "synthetic-recipient", 0, 2, false, true)
        assertNotEquals("SENT_TO_CARRIER", outbox.getIncident(id)!!.recipientStatuses["synthetic-recipient"])
        outbox.recordSmsPart(id, "synthetic-recipient", 1, 2, false, true)
        assertEquals("SENT_TO_CARRIER", outbox.getIncident(id)!!.recipientStatuses["synthetic-recipient"])
        outbox.recordSmsPart(id, "synthetic-recipient", 0, 2, true, true)
        outbox.recordSmsPart(id, "synthetic-recipient", 1, 2, true, true)
        assertEquals("DELIVERED", EmergencyOutbox(context).getIncident(id)!!.recipientStatuses["synthetic-recipient"])
        outbox.updateState(id, "RESOLVED")
        outbox.saveIncident(EmergencyIncident(incidentId = id, userId = FirebaseAuth.getInstance().currentUser!!.uid))
        assertEquals("RESOLVED", outbox.getIncident(id)!!.state)
    }
    @Test fun cancellationBeforeIncidentPersistenceCreatesTerminalTombstone() {
        val id = UUID.randomUUID().toString(); val outbox = EmergencyOutbox(context)
        outbox.updateState(id, "CANCELLED")
        outbox.saveIncident(EmergencyIncident(incidentId = id))
        assertEquals("CANCELLED", EmergencyOutbox(context).getIncident(id)!!.state)
        assertFalse(outbox.reserveRecipient(id, "synthetic-recipient"))
    }
    @Test fun savedThreeFiveTenSecondWindowsDriveCoordinatorAndCancelDurably() = runBlocking {
        val manager = EmergencyManager.getInstance(context)
        val outbox = EmergencyOutbox(context)
        for (seconds in listOf(3, 5, 10)) {
            prefs.updateCountdownDuration(seconds)
            assertEquals(seconds, prefs.preferencesFlow.first().emergencyCountdownSeconds)
            manager.startCountdown("BUTTON", "QA CANCELLATION")
            val id = manager.currentIncidentId!!
            withTimeout(3000) { while (outbox.getIncident(id)?.state != EmergencyIncident.STATE_COUNTDOWN) delay(20) }
            val incident = outbox.getIncident(id)!!
            assertEquals(seconds, incident.countdownSeconds)
            assertTrue(EmergencyCountdownWork.remaining(context, incident) in 1..seconds * 1000L)
            manager.startCountdown("BUTTON", "QA DUPLICATE")
            assertEquals(id, manager.currentIncidentId)
            manager.cancelEmergency()
            assertEquals(EmergencyIncident.STATE_CANCELLED, EmergencyOutbox(context).getIncident(id)!!.state)
            assertTrue(manager.expireCountdown(id, sendNow = true))
            assertEquals(EmergencyIncident.STATE_CANCELLED, outbox.getIncident(id)!!.state)
            assertTrue(outbox.getIncident(id)!!.recipientStatuses.isEmpty())
        }
    }

    @Test fun countdownExpiresWithoutUiAndDuplicateCallbacksDoNotRedispatch() = runBlocking {
        prefs.updateCountdownDuration(3)
        val manager = EmergencyManager.getInstance(context)
        manager.startCountdown("BUTTON", "QA EXPIRY")
        val id = manager.currentIncidentId!!
        val outbox = EmergencyOutbox(context)
        withTimeout(20_000) { while (outbox.getIncident(id)?.state != EmergencyIncident.STATE_ACTIVE) delay(100) }
        repeat(5) { manager.expireCountdown(id) }
        assertEquals(1, outbox.getAllIncidents().count { it.incidentId == id })
        assertEquals(EmergencyIncident.STATE_ACTIVE, outbox.getIncident(id)!!.state)
        assertTrue(outbox.getIncident(id)!!.recipientStatuses.isEmpty())
        manager.resolveEmergency(id)
    }
    @Test fun profileBirthDateSurvivesLogoutLoginAndInvalidDateIsRejected() = runBlocking {
        val auth = FirebaseAuth.getInstance(); val email = auth.currentUser!!.email!!
        val repo = UserRepository.getInstance()
        withTimeout(8_000) { repo.userProfile.first { it?.uid == auth.currentUser!!.uid } }
        assertTrue(repo.updateProfile("Synthetic QA", "", "2000-10-18", "Synthetic address") is Resource.Success)
        assertTrue(repo.updateProfile("Synthetic QA", "", "3000-10-18", "") is Resource.Error)
        auth.signOut(); auth.signInWithEmailAndPassword(email, "Synthetic-Test-1234!").await()
        val profile = withTimeout(8_000) { repo.userProfile.first { it?.dateOfBirth == "2000-10-18" } }
        assertEquals("Synthetic address", profile!!.homeAddress)
    }
    @Test fun signedOutAccountCannotReserveAnOldIncidentRecipient() {
        val id = UUID.randomUUID().toString()
        val outbox = EmergencyOutbox(context)
        outbox.saveIncident(EmergencyIncident(incidentId = id, userId = FirebaseAuth.getInstance().currentUser!!.uid))
        FirebaseAuth.getInstance().signOut()
        assertFalse(outbox.reserveRecipient(id, "synthetic-recipient"))
    }

    @Test fun androidKeystoreIdentityAndEncryptedQueueSurviveNewInstances(): Unit = runBlocking {
        val security = com.siddesh.smsalerts.mesh.MeshSecurity
        val previous = security.groupCode(context)
        val code = security.createGroup(context)
        try {
            val signed = security.signPacket(context, com.siddesh.smsalerts.mesh.MeshPacket(
                senderDeviceId = "", messageText = "Synthetic secret payload", senderName = "Synthetic QA"))
            assertTrue(security.verifyPacketIntegrity(signed))
            assertFalse(security.verifyPacketIntegrity(signed.copy(messageText = "tampered")))
            val queue = com.siddesh.smsalerts.mesh.MeshStoreAndForwardQueue(context, "qa")
            queue.enqueue(signed)
            assertEquals(signed.messageId, com.siddesh.smsalerts.mesh.MeshStoreAndForwardQueue(context, "qa").getPendingPackets().single().messageId)
            val file = java.io.File(context.filesDir, "mesh_${security.sha256Hex(code).take(16)}_qa.enc")
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("Synthetic secret payload"))
            security.createGroup(context)
            assertTrue(com.siddesh.smsalerts.mesh.MeshStoreAndForwardQueue(context, "qa").getPendingPackets().isEmpty())
            security.configureGroup(context, code)
            assertEquals(signed.messageId, queue.getPendingPackets().single().messageId)
            queue.removePacket(signed.messageId)
        } finally {
            if (previous != null) security.configureGroup(context, previous)
            else context.getSharedPreferences("mesh_v2", 0).edit().remove("group").commit()
        }
    }

    @Test fun removingMissingNearbyPacketWithoutGroupDoesNotRequireAnEncryptionKey() = runBlocking {
        val storage = context.getSharedPreferences("mesh_v2", 0)
        val previous = storage.getString("group", null)
        try {
            assertTrue(storage.edit().remove("group").commit())
            val queue = com.siddesh.smsalerts.mesh.MeshStoreAndForwardQueue(context, "qa-no-group")
            queue.removePacket(UUID.randomUUID().toString())
            assertTrue(queue.getPendingPackets().isEmpty())
        } finally {
            if (previous != null) storage.edit().putString("group", previous).commit()
        }
    }

}
