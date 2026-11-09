package com.siddesh.smsalerts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessRecoveryInstrumentedTest {
    @Test fun restoredExpiredTimerAndPendingIncidentUseSavedIds(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
        val phase = InstrumentationRegistry.getArguments().getString("recoveryPhase", "in_process")
        val marker = context.getSharedPreferences("qa_recovery", 0)
        val prefs = UserPreferencesDataStore(context)
        if (phase != "restore") {
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
            auth.createUserWithEmailAndPassword("recovery-${java.util.UUID.randomUUID()}@example.invalid", "Synthetic-Test-1234!").await()
            prefs.updateDirectSms(false); prefs.updateBluetoothMeshEnabled(false); prefs.updateLocationSharing(false)
            prefs.cancelOrCompleteSafetyCheckIn(); prefs.cancelOrCompleteWomensSafetyJourney()
            prefs.startSafetyCheckIn(1, System.currentTimeMillis() - 120_000, 0)
            val id = prefs.preferencesFlow.first().checkInSessionId
            val pending = java.util.UUID.randomUUID().toString()
            EmergencyOutbox(context).saveIncident(com.siddesh.smsalerts.data.model.EmergencyIncident(incidentId = pending, userId = auth.currentUser!!.uid, triggerSource = "BUTTON"))
            val countdown = java.util.UUID.randomUUID().toString()
            EmergencyOutbox(context).saveIncident(com.siddesh.smsalerts.data.model.EmergencyIncident(
                incidentId = countdown, userId = auth.currentUser!!.uid, triggerSource = "BUTTON",
                state = "COUNTDOWN", countdownSeconds = 3,
                countdownDeadlineWall = System.currentTimeMillis() + 3000,
                countdownDeadlineElapsed = android.os.SystemClock.elapsedRealtime() + 3000,
                countdownBootCount = EmergencyCountdownWork.boot(context)))
            val owner = auth.currentUser!!.uid
            assertTrue(marker.edit().putString("id", id).putString("pending", pending).putString("countdown", countdown)
                .putString("owner", owner).putInt("pid", android.os.Process.myPid()).commit())
            if (phase == "seed") {
                // Firebase writes sign-in preferences asynchronously. Establish a durable signed-in
                // fixture before the host abruptly stops this process; never reauthenticate on restore.
                withTimeout(5_000) {
                    val directory = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                    while (directory.listFiles().orEmpty().none { file ->
                        file.name.startsWith("com.google.firebase.auth.api.Store.") &&
                            runCatching { file.readText().contains(owner) }.getOrDefault(false)
                    }) delay(25)
                }
                return@runBlocking
            }
        } else {
            assertNotEquals(marker.getInt("pid", 0), android.os.Process.myPid())
            assertEquals("The same saved account must restore without signing in again",
                marker.getString("owner", null), FirebaseAuth.getInstance().currentUser?.uid)
        }
        assertFalse(prefs.preferencesFlow.first().directSmsEnabled)
        EmergencyRecovery.restore(context)
        val ids = listOf(marker.getString("id", "")!!, marker.getString("pending", "")!!, marker.getString("countdown", "")!!)
        withTimeout(45_000) {
            while (ids.any { EmergencyOutbox(context).getIncident(it)?.state != "ACTIVE" }) delay(250)
        }
        ids.forEach { id ->
            val incident = EmergencyOutbox(context).getIncident(id)!!
            assertTrue(incident.recipientStatuses.isEmpty())
            EmergencyManager.getInstance(context).resolveEmergency(id)
        }
        prefs.cancelOrCompleteSafetyCheckIn()
    }
}
