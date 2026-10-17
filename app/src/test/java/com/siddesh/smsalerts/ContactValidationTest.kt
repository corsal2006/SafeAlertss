package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactValidationTest {

    @Test
    fun emergencyContact_creationAndDefaultValues() {
        val contact = EmergencyContact(
            id = "c1",
            name = "Sarah Connor",
            phoneNumber = "+1-555-0199",
            relationship = "Family",
            isActive = true
        )

        assertEquals("Sarah Connor", contact.name)
        assertEquals("+1-555-0199", contact.phoneNumber)
        assertEquals("Family", contact.relationship)
        assertTrue(contact.isActive)
    }

    @Test
    fun emergencyContact_toggleAlertStatus() {
        val contact = EmergencyContact(
            id = "c1",
            name = "John Doe",
            phoneNumber = "555-1234",
            isActive = true
        )

        val disabledContact = contact.copy(isActive = false)
        assertFalse(disabledContact.isActive)

        val reEnabledContact = disabledContact.copy(isActive = true)
        assertTrue(reEnabledContact.isActive)
    }

    @Test
    fun filterActiveContacts_onlyIncludesAlertEnabled() {
        val contacts = listOf(
            EmergencyContact(id = "1", name = "Mom", phoneNumber = "111", isActive = true),
            EmergencyContact(id = "2", name = "Doctor", phoneNumber = "222", isActive = false),
            EmergencyContact(id = "3", name = "Sibling", phoneNumber = "333", isActive = true)
        )

        val active = contacts.filter { it.isActive }
        assertEquals(2, active.size)
        assertEquals(listOf("1", "3"), active.map { it.id })
    }
}
