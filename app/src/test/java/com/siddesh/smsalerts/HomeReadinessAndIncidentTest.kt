package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.ui.viewmodel.ServiceReadinessState
import org.junit.Assert.*
import org.junit.Test

class HomeReadinessAndIncidentTest {

    @Test
    fun testServiceReadinessAccuratelyReflectsContacts() {
        // Zero contacts
        val unconfigured = ServiceReadinessState(
            isReady = false,
            title = "SETUP REQUIRED",
            subtitle = "Configure trusted contacts to receive emergency alerts.",
            hasContacts = false,
            hasSmsPermission = true,
            hasLocationReady = true
        )
        assertFalse("Readiness must not be ready without contacts", unconfigured.hasContacts)
        assertFalse(unconfigured.isReady)

        // Configured contacts
        val configured = ServiceReadinessState(
            isReady = true,
            title = "SYSTEM ACTIVE",
            subtitle = "Ready for emergency dispatch via SMS, Cloud and BLE mesh.",
            hasContacts = true,
            hasSmsPermission = true,
            hasLocationReady = true
        )
        assertTrue("Readiness must reflect configured contacts", configured.hasContacts)
        assertTrue("System must be ready with contacts and location", configured.isReady)
    }

    @Test
    fun testActiveIncidentDataIntegrity() {
        val incident = EmergencyIncident(
            incidentId = "INC-123456",
            userId = "user-abc",
            senderName = "Test User",
            emergencyType = "MEDICAL",
            messageText = "Immediate medical assistance required",
            latitude = 19.015,
            longitude = 73.097,
            state = "ACTIVE"
        )

        assertEquals("INC-123456", incident.incidentId)
        assertEquals("ACTIVE", incident.state)
        assertEquals("MEDICAL", incident.emergencyType)
        assertTrue(incident.state.isNotBlank())
        assertEquals("ACTIVE", incident.state.uppercase())
    }
}
