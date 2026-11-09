package com.siddesh.smsalerts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
<<<<<<< HEAD
import com.siddesh.smsalerts.SafeAlertApp
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.service.EmergencyOutbox
=======
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
>>>>>>> origin/main
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
<<<<<<< HEAD
    private val alertsRepository: AlertsRepository = AlertsRepository(),
    private val context: android.content.Context? = null
=======
    private val alertsRepository: AlertsRepository = AlertsRepository()
>>>>>>> origin/main
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts.asStateFlow()

    private val _selectedAlert = MutableStateFlow<EmergencyAlert?>(null)
    val selectedAlert: StateFlow<EmergencyAlert?> = _selectedAlert.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
<<<<<<< HEAD
    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()
    private var cloudAlerts: List<EmergencyAlert> = emptyList()
    private val deleting = mutableSetOf<String>()

    init {
        loadHistory()
        viewModelScope.launch {
            authRepository.authState.collectLatest { state ->
                cloudAlerts = emptyList(); _selectedAlert.value = null
                _alerts.value = mergeAlerts(emptyList())
                if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) {
                    alertsRepository.getAlertsFlow(state.user.uid).collectLatest history@ { list ->
                        if (authRepository.currentUserId != state.user.uid) return@history
                        cloudAlerts = list
                        _alerts.value = mergeAlerts(list)
                        _isLoading.value = false
                    }
                } else if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedOut) {
                    _alerts.value = mergeAlerts(emptyList())
=======

    init {
        viewModelScope.launch {
            authRepository.authState.collectLatest { state ->
                if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) {
                    alertsRepository.getAlertsFlow(state.user.uid).collectLatest { list ->
                        _alerts.value = list
                        _isLoading.value = false
                    }
                } else if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedOut) {
                    _alerts.value = emptyList()
>>>>>>> origin/main
                    _isLoading.value = false
                }
            }
        }
    }

<<<<<<< HEAD
    private fun loadLocalAlerts(): List<EmergencyAlert> {
        val ctx = context ?: SafeAlertApp.instance ?: return emptyList()
        return try {
            val owner = authRepository.currentUserId
            val incidents = EmergencyOutbox(ctx).getAllIncidents().filter { it.userId == owner || (owner == null && it.userId in setOf("", "local_user")) }
            incidents.map { inc ->
                EmergencyAlert(
                    alertId = inc.incidentId,
                    uid = inc.userId,
                    emergencyType = inc.emergencyType,
                    triggerSource = inc.triggerSource,
                    message = inc.messageText,
                    latitude = inc.latitude,
                    longitude = inc.longitude,
                    locationAvailable = inc.latitude != null && inc.longitude != null,
                    createdAt = inc.creationTimestamp,
                    status = when (inc.state) {
                        EmergencyIncident.STATE_COUNTDOWN -> "Countdown"
                        EmergencyIncident.STATE_ACTIVE -> "Active Dispatch"
                        EmergencyIncident.STATE_CANCELLED -> "Cancelled"
                        EmergencyIncident.STATE_RESOLVED -> "Resolved"
                        else -> inc.state
                    },
                    sendMethod = "Saved incident; open for channel outcomes",
                    recipientPhoneNumbers = inc.recipientPhoneNumbers,
                    recipients = inc.recipientPhoneNumbers,
                    recipientCount = inc.recipientPhoneNumbers.size,
                    timestamp = inc.creationTimestamp
                )
            }
        } catch (e: Exception) {
            _status.value = "Saved history could not be read. Check device storage."
            emptyList()
        }
    }

    private fun mergeAlerts(cloudList: List<EmergencyAlert>): List<EmergencyAlert> {
        val localList = loadLocalAlerts()
        return (localList + cloudList.filter { it.uid == authRepository.currentUserId }).distinctBy { it.alertId }.sortedByDescending { it.createdAt }
    }

    fun loadHistory() {
        _alerts.value = mergeAlerts(cloudAlerts)
        _isLoading.value = false
    }

    fun selectAlertById(alertId: String) {
        val userId = authRepository.currentUserId
        if (userId.isNullOrBlank()) {
            _selectedAlert.value = _alerts.value.find { it.alertId == alertId }
            return
        }
=======
    fun loadHistory() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            alertsRepository.getAlertsFlow(userId).collectLatest { list ->
                _alerts.value = list
                _isLoading.value = false
            }
        }
    }

    fun selectAlertById(alertId: String) {
        val userId = authRepository.currentUserId ?: return
>>>>>>> origin/main
        viewModelScope.launch {
            when (val result = alertsRepository.getAlertById(userId, alertId)) {
                is Resource.Success -> _selectedAlert.value = result.data
                else -> {
                    _selectedAlert.value = _alerts.value.find { it.alertId == alertId }
                }
            }
        }
    }
<<<<<<< HEAD

    fun deleteAlert(alertId: String) {
        viewModelScope.launch { deleteConfirmed(alertId) }
    }

    private suspend fun deleteConfirmed(alertId: String): Boolean {
        if (!deleting.add(alertId)) return false
        try {
            val userId = authRepository.currentUserId ?: error("Sign in to delete saved history.")
            val ctx = context ?: SafeAlertApp.instance ?: error("App storage unavailable.")
            _status.value = "Deleting ended incident and its saved locations…"
            com.siddesh.smsalerts.data.repository.HistoryDeletionRepository(ctx).delete(userId, alertId)
            _alerts.value = _alerts.value.filter { it.alertId != alertId }
            cloudAlerts = cloudAlerts.filterNot { it.alertId == alertId }
            _status.value = "Incident and saved locations deleted. Previously received copies cannot be recalled."
            if (_selectedAlert.value?.alertId == alertId) {
                _selectedAlert.value = null
            }
            return true
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            _status.value = "Deletion timed out and may be partial. Reconnect and retry this record to finish."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            com.siddesh.smsalerts.utils.PrivacyLog.w("History", "DELETE_INCOMPLETE", e)
            _status.value = "Deletion not completed; some records may already be removed. Check sign-in and connection, then retry. Only ended incidents can be deleted."
        } finally {
            deleting.remove(alertId)
        }
        return false
    }

    fun clearHistory() {
        val ids = _alerts.value.filter { it.status in setOf("Resolved", "Cancelled", "RESOLVED", "CANCELLED") }.map { it.alertId }
        viewModelScope.launch {
            for (id in ids) if (!deleteConfirmed(id)) return@launch
            if (ids.isEmpty()) _status.value = "No ended incidents are shown for this account."
        }
    }
=======
>>>>>>> origin/main
}
