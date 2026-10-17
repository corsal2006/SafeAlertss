package com.siddesh.smsalerts.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {

    private val fullDateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatFullDateTime(timestamp: Long): String {
        return fullDateTimeFormat.format(Date(timestamp))
    }

    fun formatTimestamp(timestamp: Long): String {
        return fullDateTimeFormat.format(Date(timestamp))
    }

    fun formatTimeOnly(timestamp: Long): String {
        return timeOnlyFormat.format(Date(timestamp))
    }

    fun formatShortTime(timestamp: Long): String {
        return timeOnlyFormat.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long): String {
        return dateOnlyFormat.format(Date(timestamp))
    }
}
