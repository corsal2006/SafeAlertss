package com.siddesh.smsalerts.data.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.utils.DateUtils

class SmsSender(private val context: Context) {

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getSimStateDescription(): Pair<Boolean, String> {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return Pair(false, "Telephony service unavailable on this device")

        return when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> Pair(true, "SIM Ready")
            TelephonyManager.SIM_STATE_ABSENT -> Pair(false, "No SIM card detected")
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> Pair(false, "SIM locked (PIN required)")
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> Pair(false, "SIM locked (PUK required)")
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> Pair(false, "Network locked")
            else -> Pair(true, "SIM Ready")
        }
    }

    fun formatEmergencyMessage(
        emergencyType: String = "",
        customMessage: String? = null,
        baseMessage: String = "",
        mapsUrl: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val formattedTime = DateUtils.formatFullDateTime(timestamp)
        val chosenMessage = when {
            !customMessage.isNullOrBlank() -> customMessage
            baseMessage.isNotBlank() -> baseMessage
            else -> "Emergency alert: I may need assistance. Please contact me as soon as possible."
        }

        return buildString {
            if (emergencyType.isNotBlank() && emergencyType != "EMERGENCY") {
                append("[$emergencyType] ")
            }
            append(chosenMessage)
            if (!mapsUrl.isNullOrBlank()) {
                append("\n\nLocation: $mapsUrl")
            } else {
                append("\n\nLocation: Unavailable")
            }
            append("\nTime: $formattedTime")
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun sendEmergencySms(
        contacts: List<EmergencyContact>,
        messageText: String
    ): SmsDispatchResult {
        if (!hasSmsPermission()) {
            return SmsDispatchResult(
                overallStatus = "Permission Denied",
                successfulCount = 0,
                totalCount = contacts.size,
                contactStatuses = contacts.map {
                    ContactSmsStatus(it.name, it.phoneNumber, "SEND_SMS permission not granted", false)
                },
                failureReason = "SEND_SMS permission is not granted. You can use the SMS app composer instead."
            )
        }

        val (simReady, simMessage) = getSimStateDescription()
        if (!simReady) {
            return SmsDispatchResult(
                overallStatus = "SIM Error",
                successfulCount = 0,
                totalCount = contacts.size,
                contactStatuses = contacts.map {
                    ContactSmsStatus(it.name, it.phoneNumber, simMessage, false)
                },
                failureReason = simMessage
            )
        }

        val smsManager = getSmsManager()
        if (smsManager == null) {
            return SmsDispatchResult(
                overallStatus = "Service Unavailable",
                successfulCount = 0,
                totalCount = contacts.size,
                contactStatuses = contacts.map {
                    ContactSmsStatus(it.name, it.phoneNumber, "SmsManager service unavailable", false)
                },
                failureReason = "Android telephony SMS service could not be initialized."
            )
        }

        val contactStatuses = mutableListOf<ContactSmsStatus>()
        var successCount = 0

        for (contact in contacts) {
            val cleanPhone = contact.phoneNumber.trim().replace(" ", "").replace("-", "")
            if (cleanPhone.isEmpty()) {
                contactStatuses.add(
                    ContactSmsStatus(contact.name, contact.phoneNumber, "Invalid Phone Number", false)
                )
                continue
            }

            try {
                val dividedParts = smsManager.divideMessage(messageText)
                if (dividedParts.size > 1) {
                    smsManager.sendMultipartTextMessage(
                        cleanPhone,
                        null,
                        dividedParts,
                        null,
                        null
                    )
                } else {
                    smsManager.sendTextMessage(
                        cleanPhone,
                        null,
                        messageText,
                        null,
                        null
                    )
                }

                contactStatuses.add(
                    ContactSmsStatus(
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        status = "SMS Transmitted",
                        isSuccess = true
                    )
                )
                successCount++
            } catch (e: SecurityException) {
                contactStatuses.add(
                    ContactSmsStatus(
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        status = "Security Exception",
                        isSuccess = false,
                        errorReason = e.localizedMessage
                    )
                )
            } catch (e: Exception) {
                contactStatuses.add(
                    ContactSmsStatus(
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        status = "Send Failed",
                        isSuccess = false,
                        errorReason = e.localizedMessage ?: "Hardware or carrier dispatch error"
                    )
                )
            }
        }

        val overallStatus = when {
            successCount == contacts.size && contacts.isNotEmpty() -> "Sent"
            successCount > 0 -> "Partially Sent"
            else -> "Failed"
        }

        return SmsDispatchResult(
            overallStatus = overallStatus,
            successfulCount = successCount,
            totalCount = contacts.size,
            contactStatuses = contactStatuses,
            failureReason = if (successCount < contacts.size) "Some or all SMS transmissions encountered issues." else null
        )
    }

    fun createFallbackSmsIntent(recipients: List<String>, messageText: String): Intent {
        val joinedRecipients = recipients.joinToString(";")
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$joinedRecipients")
            putExtra("sms_body", messageText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
