package com.siddesh.smsalerts.utils

import java.util.regex.Pattern

object ValidationUtils {

    private val EMAIL_PATTERN: Pattern = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+"
    )

    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    fun isValidPassword(password: String?): Boolean {
        if (password.isNullOrBlank()) return false
        return password.length >= 6
    }

    fun doPasswordsMatch(password: String?, confirm: String?): Boolean {
        if (password.isNullOrBlank() || confirm.isNullOrBlank()) return false
        return password == confirm
    }

    fun isValidFullName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.trim().length >= 2
    }

    fun isValidPhoneNumber(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        val digits = phone.filter { it.isDigit() }
        return digits.length in 7..15
    }

    /**
     * Normalizes phone number by removing spaces, brackets, hyphens.
     * Preserves leading '+' for country codes, removes other formatting artifacts.
     */
    fun normalizePhoneNumber(phone: String): String {
        val trimmed = phone.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Compares two phone numbers for equality ignoring international prefix variation
     * (e.g. comparing last 10 digits when both have at least 10 digits).
     */
    fun arePhoneNumbersMatching(phoneA: String, phoneB: String): Boolean {
        val normA = normalizePhoneNumber(phoneA)
        val normB = normalizePhoneNumber(phoneB)
        if (normA == normB) return true

        val digitsA = normA.filter { it.isDigit() }
        val digitsB = normB.filter { it.isDigit() }
        if (digitsA == digitsB) return true

        if (digitsA.length >= 10 && digitsB.length >= 10) {
            val suffixA = digitsA.takeLast(10)
            val suffixB = digitsB.takeLast(10)
            return suffixA == suffixB
        }

        return false
    }
}
