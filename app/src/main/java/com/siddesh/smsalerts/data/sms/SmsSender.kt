package com.siddesh.smsalerts.data.sms

import android.Manifest
<<<<<<< HEAD
import android.app.PendingIntent
=======
>>>>>>> origin/main
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
<<<<<<< HEAD
import android.provider.Settings
=======
>>>>>>> origin/main
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.siddesh.smsalerts.data.model.EmergencyContact
<<<<<<< HEAD
import com.siddesh.smsalerts.service.EmergencyOutbox
import com.siddesh.smsalerts.utils.DateUtils
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.utils.ValidationUtils

private const val TAG = "SafeAlerts/SmsSender"
=======
import com.siddesh.smsalerts.utils.DateUtils
>>>>>>> origin/main

class SmsSender(private val context: Context) {

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

<<<<<<< HEAD
    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }
    }

=======
>>>>>>> origin/main
    fun getSimStateDescription(): Pair<Boolean, String> {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return Pair(false, "Telephony service unavailable on this device")

<<<<<<< HEAD
        if (isAirplaneModeOn()) {
            return Pair(false, "Airplane mode is enabled")
        }

=======
>>>>>>> origin/main
        return when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> Pair(true, "SIM Ready")
            TelephonyManager.SIM_STATE_ABSENT -> Pair(false, "No SIM card detected")
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> Pair(false, "SIM locked (PIN required)")
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> Pair(false, "SIM locked (PUK required)")
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> Pair(false, "Network locked")
<<<<<<< HEAD
            else -> Pair(false, "SIM state unknown or unavailable")
        }
    }

    @Suppress("DEPRECATION")
    fun getSmsManager(): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            try {
                SmsManager.getDefault()
            } catch (_: Exception) {
                Log.e(TAG, "Failed to initialize SmsManager: ${e.message}")
                null
            }
=======
            else -> Pair(true, "SIM Ready")
>>>>>>> origin/main
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

<<<<<<< HEAD
    fun sendEmergencySms(
        contacts: List<EmergencyContact>,
        messageText: String,
        incidentId: String? = null
    ): SmsDispatchResult {
        val outbox = EmergencyOutbox(context)
        val id = incidentId ?: "unknown"

        if (!hasSmsPermission()) {
            if (incidentId != null) {
                outbox.updateTransportStatus(incidentId, "SMS", "PERMISSION_DENIED")
                for (contact in contacts) {
                    val cleanPhone = ValidationUtils.normalizePhoneNumber(contact.phoneNumber)
                    if (cleanPhone.isNotBlank()) {
                        outbox.updateRecipientStatus(incidentId, cleanPhone, "PERMISSION_DENIED")
                    }
                }
            }
            Log.w(TAG, "SEND_SMS permission not granted. Native SMS dispatch aborted.")
=======
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
>>>>>>> origin/main
            return SmsDispatchResult(
                overallStatus = "Permission Denied",
                successfulCount = 0,
                totalCount = contacts.size,
                contactStatuses = contacts.map {
<<<<<<< HEAD
                    ContactSmsStatus(it.name, it.phoneNumber, "SEND_SMS permission not granted", false, "Permission Denied")
                },
                failureReason = "SEND_SMS permission is not granted on this device."
=======
                    ContactSmsStatus(it.name, it.phoneNumber, "SEND_SMS permission not granted", false)
                },
                failureReason = "SEND_SMS permission is not granted. You can use the SMS app composer instead."
>>>>>>> origin/main
            )
        }

        val (simReady, simMessage) = getSimStateDescription()
        if (!simReady) {
<<<<<<< HEAD
            if (incidentId != null) {
                outbox.updateTransportStatus(incidentId, "SMS", "SIM_ERROR")
                for (contact in contacts) {
                    val cleanPhone = ValidationUtils.normalizePhoneNumber(contact.phoneNumber)
                    if (cleanPhone.isNotBlank()) {
                        outbox.updateRecipientStatus(incidentId, cleanPhone, "SIM_ERROR")
                    }
                }
            }
            Log.w(TAG, "SIM/Telephony error: $simMessage. Native SMS dispatch aborted.")
=======
>>>>>>> origin/main
            return SmsDispatchResult(
                overallStatus = "SIM Error",
                successfulCount = 0,
                totalCount = contacts.size,
                contactStatuses = contacts.map {
<<<<<<< HEAD
                    ContactSmsStatus(it.name, it.phoneNumber, simMessage, false, simMessage)
=======
                    ContactSmsStatus(it.name, it.phoneNumber, simMessage, false)
>>>>>>> origin/main
                },
                failureReason = simMessage
            )
        }

        val smsManager = getSmsManager()
        if (smsManager == null) {
<<<<<<< HEAD
            if (incidentId != null) {
                outbox.updateTransportStatus(incidentId, "SMS", "SERVICE_UNAVAILABLE")
            }
            Log.e(TAG, "SmsManager service could not be acquired.")
=======
>>>>>>> origin/main
            return SmsDispatchResult(
                overallStatus = "Service Unavailable",
                successfulCount = 0,
                totalCount = contacts.size,
                contactStatuses = contacts.map {
<<<<<<< HEAD
                    ContactSmsStatus(it.name, it.phoneNumber, "SmsManager unavailable", false, "Service Unavailable")
=======
                    ContactSmsStatus(it.name, it.phoneNumber, "SmsManager service unavailable", false)
>>>>>>> origin/main
                },
                failureReason = "Android telephony SMS service could not be initialized."
            )
        }

        val contactStatuses = mutableListOf<ContactSmsStatus>()
        var successCount = 0

        for (contact in contacts) {
<<<<<<< HEAD
            val cleanPhone = ValidationUtils.normalizePhoneNumber(contact.phoneNumber)
            if (!ValidationUtils.isValidPhoneNumber(cleanPhone)) {
                if (incidentId != null) {
                    outbox.updateRecipientStatus(incidentId, contact.phoneNumber, "INVALID_PHONE")
                }
                contactStatuses.add(
                    ContactSmsStatus(contact.name, contact.phoneNumber, "Invalid Phone Number", false, "Invalid Phone Number")
=======
            val cleanPhone = contact.phoneNumber.trim().replace(" ", "").replace("-", "")
            if (cleanPhone.isEmpty()) {
                contactStatuses.add(
                    ContactSmsStatus(contact.name, contact.phoneNumber, "Invalid Phone Number", false)
>>>>>>> origin/main
                )
                continue
            }

<<<<<<< HEAD
            if (incidentId != null) {
                val reserved = outbox.reserveRecipient(incidentId, cleanPhone)
                if (!reserved) {
                    Log.d(TAG, "Recipient already reserved or sent for incident $incidentId. Skipping duplicate send.")
                    val existingStatus = outbox.getIncident(incidentId)?.recipientStatuses?.get(cleanPhone) ?: "ALREADY_SUBMITTED"
                    contactStatuses.add(
                        ContactSmsStatus(contact.name, contact.phoneNumber, existingStatus, true)
                    )
                    successCount++
                    continue
                }
            }

            try {
                val dividedParts = smsManager.divideMessage(messageText)
                val totalParts = dividedParts.size

                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()

                for (partIndex in 0 until totalParts) {
                    val reqCodeSent = ((id.hashCode() * 31 + cleanPhone.hashCode()) * 31 + partIndex) and 0x7FFFFFFF
                    val reqCodeDelivered = (reqCodeSent + 100_000) and 0x7FFFFFFF

                    val sentIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                        action = "com.siddesh.smsalerts.SMS_SENT"
                        putExtra("incident", id)
                        putExtra("phone", cleanPhone)
                        putExtra("part", partIndex)
                        putExtra("total", totalParts)
                    }

                    val deliveryIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                        action = "com.siddesh.smsalerts.SMS_DELIVERED"
                        putExtra("incident", id)
                        putExtra("phone", cleanPhone)
                        putExtra("part", partIndex)
                        putExtra("total", totalParts)
                    }

                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    sentIntents.add(PendingIntent.getBroadcast(context, reqCodeSent, sentIntent, flags))
                    deliveryIntents.add(PendingIntent.getBroadcast(context, reqCodeDelivered, deliveryIntent, flags))
                }

                if (totalParts > 1) {
=======
            try {
                val dividedParts = smsManager.divideMessage(messageText)
                if (dividedParts.size > 1) {
>>>>>>> origin/main
                    smsManager.sendMultipartTextMessage(
                        cleanPhone,
                        null,
                        dividedParts,
<<<<<<< HEAD
                        sentIntents,
                        deliveryIntents
=======
                        null,
                        null
>>>>>>> origin/main
                    )
                } else {
                    smsManager.sendTextMessage(
                        cleanPhone,
                        null,
                        messageText,
<<<<<<< HEAD
                        sentIntents.firstOrNull(),
                        deliveryIntents.firstOrNull()
                    )
                }

                if (incidentId != null) {
                    outbox.submittedRecipient(incidentId, cleanPhone)
                }

=======
                        null,
                        null
                    )
                }

>>>>>>> origin/main
                contactStatuses.add(
                    ContactSmsStatus(
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
<<<<<<< HEAD
                        status = "SUBMITTED",
=======
                        status = "SMS Transmitted",
>>>>>>> origin/main
                        isSuccess = true
                    )
                )
                successCount++
<<<<<<< HEAD
                Log.d(TAG, "Native SMS submitted to SmsManager (parts=$totalParts)")
            } catch (e: SecurityException) {
                if (incidentId != null) {
                    outbox.updateRecipientStatus(incidentId, cleanPhone, "SECURITY_EXCEPTION")
                }
                Log.e(TAG, "SecurityException sending SMS: ${e.message}")
=======
            } catch (e: SecurityException) {
>>>>>>> origin/main
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
<<<<<<< HEAD
                if (incidentId != null) {
                    outbox.updateRecipientStatus(incidentId, cleanPhone, "SEND_FAILED")
                }
                Log.e(TAG, "Exception sending SMS: ${e.message}")
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
            successCount == contacts.size && contacts.isNotEmpty() -> "SENT_TO_CARRIER"
            successCount > 0 -> "PARTIALLY_SENT"
            contacts.isEmpty() -> "NO_RECIPIENTS"
            else -> "FAILED"
        }

        if (incidentId != null) {
            outbox.updateTransportStatus(incidentId, "SMS", overallStatus)
=======
            successCount == contacts.size && contacts.isNotEmpty() -> "Sent"
            successCount > 0 -> "Partially Sent"
            else -> "Failed"
>>>>>>> origin/main
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
