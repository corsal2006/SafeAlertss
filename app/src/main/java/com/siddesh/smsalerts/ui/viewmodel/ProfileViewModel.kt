package com.siddesh.smsalerts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.model.UserProfile
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: com.siddesh.smsalerts.data.repository.UserRepository = com.siddesh.smsalerts.data.repository.UserRepository.getInstance(),
    private val contactsRepository: ContactsRepository = ContactsRepository(),
    private val alertsRepository: AlertsRepository = AlertsRepository()
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userRepository.userProfile

    private val _contactsCount = MutableStateFlow(0)
    val contactsCount: StateFlow<Int> = _contactsCount.asStateFlow()

    private val _alertsCount = MutableStateFlow(0)
    val alertsCount: StateFlow<Int> = _alertsCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private var contactsJob: kotlinx.coroutines.Job? = null
    private var alertsJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            authRepository.authState.collectLatest { state ->
                if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) {
                    val uid = state.user.uid
                    contactsJob?.cancel()
                    contactsJob = launch {
                        contactsRepository.getContactsFlow(uid).collectLatest { list ->
                            _contactsCount.value = list.size
                        }
                    }

                    alertsJob?.cancel()
                    alertsJob = launch {
                        alertsRepository.getAlertsFlow(uid).collectLatest { list ->
                            _alertsCount.value = list.size
                        }
                    }
                } else if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedOut) {
                    contactsJob?.cancel()
                    alertsJob?.cancel()
                    _contactsCount.value = 0
                    _alertsCount.value = 0
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadProfile() {
        // Automatically observed via UserRepository.userProfile
    }

    fun updateProfile(name: String, phoneNumber: String, onSuccess: (() -> Unit)? = null) {
        if (name.isBlank()) {
            _updateMessage.value = "Name cannot be empty."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            when (val result = userRepository.updateProfile(name, phoneNumber)) {
                is Resource.Success -> {
                    _updateMessage.value = "Profile updated successfully."
                    onSuccess?.invoke()
                }
                is Resource.Error -> _updateMessage.value = result.message
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _updateMessage.value = null
    }

    fun logout() {
        authRepository.logout()
    }
}
