package com.siddesh.smsalerts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
    private val alertsRepository: AlertsRepository = AlertsRepository()
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts.asStateFlow()

    private val _selectedAlert = MutableStateFlow<EmergencyAlert?>(null)
    val selectedAlert: StateFlow<EmergencyAlert?> = _selectedAlert.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                    _isLoading.value = false
                }
            }
        }
    }

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
        viewModelScope.launch {
            when (val result = alertsRepository.getAlertById(userId, alertId)) {
                is Resource.Success -> _selectedAlert.value = result.data
                else -> {
                    _selectedAlert.value = _alerts.value.find { it.alertId == alertId }
                }
            }
        }
    }
}
