package com.siddesh.smsalerts

import com.siddesh.smsalerts.utils.ValidationUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun validEmails_returnTrue() {
        assertTrue(ValidationUtils.isValidEmail("user@example.com"))
        assertTrue(ValidationUtils.isValidEmail("siddesh.alert@domain.org"))
        assertTrue(ValidationUtils.isValidEmail("test+tag@company.co.in"))
    }

    @Test
    fun invalidEmails_returnFalse() {
        assertFalse(ValidationUtils.isValidEmail(null))
        assertFalse(ValidationUtils.isValidEmail(""))
        assertFalse(ValidationUtils.isValidEmail("   "))
        assertFalse(ValidationUtils.isValidEmail("plainaddress"))
        assertFalse(ValidationUtils.isValidEmail("missingatsign.com"))
        assertFalse(ValidationUtils.isValidEmail("@domain.com"))
    }

    @Test
    fun passwordValidation_checksMinimumLength() {
        assertFalse(ValidationUtils.isValidPassword(null))
        assertFalse(ValidationUtils.isValidPassword(""))
        assertFalse(ValidationUtils.isValidPassword("12345"))
        assertTrue(ValidationUtils.isValidPassword("123456"))
        assertTrue(ValidationUtils.isValidPassword("StrongSecureP@ss1"))
    }

    @Test
    fun passwordConfirmation_matchesCorrectly() {
        assertTrue(ValidationUtils.doPasswordsMatch("Secret123", "Secret123"))
        assertFalse(ValidationUtils.doPasswordsMatch("Secret123", "Secret124"))
        assertFalse(ValidationUtils.doPasswordsMatch("Secret123", null))
        assertFalse(ValidationUtils.doPasswordsMatch(null, "Secret123"))
    }

    @Test
    fun fullNameValidation_enforcesNonEmptyAndLength() {
        assertFalse(ValidationUtils.isValidFullName(null))
        assertFalse(ValidationUtils.isValidFullName(""))
        assertFalse(ValidationUtils.isValidFullName(" "))
        assertFalse(ValidationUtils.isValidFullName("A"))
        assertTrue(ValidationUtils.isValidFullName("Al"))
        assertTrue(ValidationUtils.isValidFullName("Siddesh K"))
    }

    @Test
    fun phoneValidation_requiresValidDigits() {
        assertFalse(ValidationUtils.isValidPhoneNumber(null))
        assertFalse(ValidationUtils.isValidPhoneNumber("123"))
        assertTrue(ValidationUtils.isValidPhoneNumber("+1 (555) 234-5678"))
        assertTrue(ValidationUtils.isValidPhoneNumber("9876543210"))
    }
}
