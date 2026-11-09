package com.siddesh.smsalerts.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import com.siddesh.smsalerts.data.sms.SmsDispatchResult
import com.siddesh.smsalerts.data.sms.SmsSender
import com.siddesh.smsalerts.service.EmergencyManager
import kotlinx.coroutines.flow.StateFlow

sealed class SosStage {
    object Idle : SosStage()
    data class TriggerDetected(val source: String, val emergencyType: String) : SosStage()
    data class Countdown(val secondsLeft: Int) : SosStage()
    object AcquiringLocation : SosStage()
    object TransmittingSms : SosStage()
    object SavingCloudAudit : SosStage()
    object Dispatching : SosStage()
    object EmergencyActive : SosStage()
    data class Completed(
        val alert: EmergencyAlert,
        val smsResult: SmsDispatchResult
    ) : SosStage()
    data class Error(
        val reason: String,
        val canRetry: Boolean = true,
        val fallbackIntent: Intent? = null
    ) : SosStage()
    object Cancelled : SosStage()
    object Resolved : SosStage()
}

class SosViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val contactsRepository: ContactsRepository = ContactsRepository(),
    private val alertsRepository: AlertsRepository = AlertsRepository(),
    private val locationService: LocationService = LocationService(application),
    private val smsSender: SmsSender = SmsSender(application),
    private val emergencyManager: EmergencyManager = EmergencyManager.getInstance(application)
) : AndroidViewModel(application) {

    val stage: StateFlow<SosStage> = emergencyManager.stage
    val incidentId: String? get() = emergencyManager.currentIncidentId
    fun resumeIncident(id: String) = emergencyManager.resumeIncident(id)

    fun startCountdown(
        emergencyType: String,
        customMessage: String = "",
        triggerSource: String = "BUTTON",
        durationSeconds: Int? = null
    ) {
        emergencyManager.startCountdown(
            triggerSource = triggerSource,
            emergencyType = emergencyType,
            customMessage = customMessage,
            countdownSeconds = durationSeconds
        )
    }

    fun dispatchNow(
        emergencyType: String,
        customMessage: String = "",
        triggerSource: String = "BUTTON"
    ) {
        emergencyManager.triggerEmergency(
            triggerSource = triggerSource,
            emergencyType = emergencyType,
            customMessage = customMessage,
            bypassCountdown = true
        )
    }

    fun cancelEmergency() {
        emergencyManager.cancelEmergency()
    }
}
