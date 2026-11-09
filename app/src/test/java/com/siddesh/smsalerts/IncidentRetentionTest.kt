package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.service.EmergencyOutbox
import org.junit.Assert.*
import org.junit.Test

class IncidentRetentionTest {
    @Test fun recentlyEndedIncidentIsRetainedEvenWhenItPrecedesFiftyOldEntries() {
        val old = (1..50).map { EmergencyIncident(incidentId = "old-$it", state = "RESOLVED", creationTimestamp = it.toLong(), resolvedTimestamp = it.toLong()) }
        val ended = EmergencyIncident(incidentId = "just-cancelled", state = "CANCELLED", creationTimestamp = 0, resolvedTimestamp = 100)
        val active = EmergencyIncident(incidentId = "still-active", state = "ACTIVE", creationTimestamp = 0)
        val retained = EmergencyOutbox.retainRecentHistory(listOf(active, ended) + old)
        assertEquals(51, retained.size)
        assertTrue(retained.any { it.incidentId == "just-cancelled" && it.state == "CANCELLED" })
        assertTrue(retained.any { it.incidentId == "still-active" })
        assertFalse(retained.any { it.incidentId == "old-1" })
        assertEquals(retained, EmergencyOutbox.retainRecentHistory(retained))
    }
}
