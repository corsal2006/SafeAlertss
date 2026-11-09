package com.siddesh.smsalerts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.data.location.*
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.repository.HistoryDeletionRepository
import com.siddesh.smsalerts.service.EmergencyOutbox
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryDeletionInstrumentedTest {
    @Test fun confirmedDeletionRemovesLocationsRejectsActiveRecordsAndResumesPartialCleanup(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val owner = auth.createUserWithEmailAndPassword("delete-${UUID.randomUUID()}@example.invalid", "Synthetic-Test-1234!").await().user!!.uid
        val db = FirebaseFirestore.getInstance()
        val outbox = EmergencyOutbox(context)
        val repo = HistoryDeletionRepository(context)
        val active = UUID.randomUUID().toString()
        outbox.saveIncident(EmergencyIncident(incidentId = active, userId = owner, state = "ACTIVE"))
        try { repo.delete(owner, active); fail("Active incident deleted") } catch (_: IllegalArgumentException) { }
        assertNotNull(outbox.getIncident(active))
        outbox.updateState(active, "RESOLVED")
        for (partial in listOf(false, true)) {
            val id = UUID.randomUUID().toString()
            outbox.saveIncident(EmergencyIncident(incidentId = id, userId = owner, state = "RESOLVED"))
            val summary = db.document("incidents/$id")
            summary.set(mapOf("ownerUid" to owner,"senderName" to "Synthetic","senderPhone" to "","policeNumber" to "","helplineNumber" to "","category" to "QA",
                "status" to if (partial) "RESOLVED" else "ACTIVE", "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
            val entry = db.document("users/$owner/alerts/$id")
            val mirror = db.document("users/$owner/emergencyAlerts/$id")
            entry.set(mapOf("status" to "EMERGENCY_ACTIVE")).await()
            mirror.set(mapOf("status" to "EMERGENCY_ACTIVE")).await()
            val now = System.currentTimeMillis()
            val fix = mapOf("latitude" to 12.0, "longitude" to 77.0, "timestamp" to now, "incidentId" to id, "receivedAt" to FieldValue.serverTimestamp())
            entry.collection("locations").document("minute_0").set(fix).await()
            db.document("users/$owner/live_location/current").set(fix).await()
            LocationStore(context).save(owner, id, LocationResult.Success(12.0, 77.0, 10f, timestamp = now, source = "synthetic-test"))
            if (partial) db.document("users/$owner/deleted_incidents/$id").set(mapOf("deletedAt" to FieldValue.serverTimestamp())).await()
            repo.delete(owner, id)
            assertFalse(summary.get(Source.SERVER).await().exists())
            assertFalse(entry.get(Source.SERVER).await().exists())
            assertFalse(mirror.get(Source.SERVER).await().exists())
            assertTrue(entry.collection("locations").get(Source.SERVER).await().isEmpty)
            assertFalse(db.document("users/$owner/live_location/current").get(Source.SERVER).await().exists())
            assertNull(outbox.getIncident(id))
            assertTrue(LocationStore(context).read(owner).none { it.session == id })
            assertTrue(db.document("users/$owner/deleted_incidents/$id").get(Source.SERVER).await().exists())
            repo.delete(owner, id) // Retry after an unknown network outcome must remain safe.
        }
    }
}
