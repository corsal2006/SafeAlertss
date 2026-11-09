package com.siddesh.smsalerts

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Host revokes QA fine/coarse permissions before running this class, without changing the production app. */
@RunWith(AndroidJUnit4::class)
class DeniedLocationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun deniedLocationStillPersistsSosWithAnExplicitReason(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.siddesh.smsalerts.qa", context.packageName)
        val service = LocationService(context)
        assertFalse("Host must revoke QA fine/coarse permission first", service.hasLocationPermission())
        assertEquals(LocationResult.PermissionDenied, service.getCurrentLocation())
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        auth.createUserWithEmailAndPassword("denied-${java.util.UUID.randomUUID()}@example.invalid", "Synthetic-Test-1234!").await()
        val prefs = UserPreferencesDataStore(context)
        prefs.updateDirectSms(false); prefs.updateBluetoothMeshEnabled(false); prefs.updateLocationSharing(true)
        val manager = EmergencyManager.getInstance(context)
        val id = manager.triggerEmergencySuspending("BUTTON", "SYNTHETIC DENIED LOCATION")
        try {
            val saved = EmergencyOutbox(context).getIncident(id)!!
            assertEquals("ACTIVE", saved.state)
            assertNull(saved.latitude); assertNull(saved.longitude)
            assertTrue(saved.transportStatuses["LOCATION"].orEmpty().contains("permission denied", ignoreCase = true))
            assertTrue(saved.recipientStatuses.isEmpty())
        } finally { manager.resolveEmergency(id); prefs.updateLocationSharing(false) }
    }
}
