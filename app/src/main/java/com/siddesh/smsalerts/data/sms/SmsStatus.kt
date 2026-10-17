package com.siddesh.smsalerts.data.sms

data class ContactSmsStatus(
    val contactName: String,
    val phoneNumber: String,
    val status: String,
    val isSuccess: Boolean,
    val errorReason: String? = null
)

data class SmsDispatchResult(
    val overallStatus: String,
    val successfulCount: Int,
    val totalCount: Int,
    val contactStatuses: List<ContactSmsStatus>,
    val failureReason: String? = null
)
