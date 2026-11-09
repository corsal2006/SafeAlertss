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
<<<<<<< HEAD
    val phoneVerification = com.siddesh.smsalerts.data.repository.PhoneVerification(viewModelScope)
    private val _enabledContactsCount = MutableStateFlow(0)
    val enabledContactsCount = _enabledContactsCount.asStateFlow()
=======
>>>>>>> origin/main

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
<<<<<<< HEAD
                phoneVerification.refreshAccount()
=======
>>>>>>> origin/main
                if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) {
                    val uid = state.user.uid
                    contactsJob?.cancel()
                    contactsJob = launch {
                        contactsRepository.getContactsFlow(uid).collectLatest { list ->
                            _contactsCount.value = list.size
<<<<<<< HEAD
                            _enabledContactsCount.value = list.count { it.enabled && it.isActive }
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
                    _enabledContactsCount.value = 0
=======
>>>>>>> origin/main
                    _alertsCount.value = 0
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadProfile() {
        // Automatically observed via UserRepository.userProfile
    }

<<<<<<< HEAD
    fun updateProfile(name: String, phoneNumber: String, dateOfBirth: String? = null, homeAddress: String = "", onSuccess: (() -> Unit)? = null) {
        if (_isLoading.value) return
=======
    fun updateProfile(name: String, phoneNumber: String, onSuccess: (() -> Unit)? = null) {
>>>>>>> origin/main
        if (name.isBlank()) {
            _updateMessage.value = "Name cannot be empty."
            return
        }

<<<<<<< HEAD
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = userRepository.updateProfile(name, phoneNumber, dateOfBirth, homeAddress)) {
=======
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = userRepository.updateProfile(name, phoneNumber)) {
>>>>>>> origin/main
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
