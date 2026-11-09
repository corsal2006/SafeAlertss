package com.siddesh.smsalerts.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ProfileDates {
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    fun age(dateOfBirth: String?, today: String = today()): Int? {
        if (dateOfBirth.isNullOrBlank() || !Regex("\\d{4}-\\d{2}-\\d{2}").matches(dateOfBirth)) return null
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        return try {
            val dob = Calendar.getInstance().apply { time = format.parse(dateOfBirth)!! }
            val current = Calendar.getInstance().apply { time = format.parse(today)!! }
            if (dob.after(current)) return null
            var years = current.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (current.get(Calendar.MONTH) < dob.get(Calendar.MONTH) ||
                (current.get(Calendar.MONTH) == dob.get(Calendar.MONTH) && current.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH))) years--
            years.takeIf { it >= 0 }
        } catch (_: Exception) { null }
    }
}
