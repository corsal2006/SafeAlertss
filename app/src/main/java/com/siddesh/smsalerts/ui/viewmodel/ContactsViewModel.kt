package com.siddesh.smsalerts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import com.siddesh.smsalerts.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContactsViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
    private val contactsRepository: ContactsRepository = ContactsRepository()
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collectLatest { state ->
                if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) {
                    _isLoading.value = true
                    contactsRepository.getContactsFlow(state.user.uid).collectLatest { list ->
                        _contacts.value = list
                        _isLoading.value = false
                    }
                } else if (state is com.siddesh.smsalerts.data.repository.AuthState.SignedOut) {
                    _contacts.value = emptyList()
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadContacts() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            contactsRepository.getContactsFlow(userId).collectLatest { list ->
                _contacts.value = list
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun addContact(
        name: String,
        phoneNumber: String,
        relationship: String,
        enabled: Boolean = true,
        onSuccess: (() -> Unit)? = null
    ) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null || user.uid.isBlank()) {
            _errorMessage.value = "AUTH_REQUIRED: Please sign in before adding trusted contacts."
            return
        }
        val userId = user.uid

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = "Contact name cannot be empty."
            return
        }

        if (!ValidationUtils.isValidPhoneNumber(phoneNumber)) {
            _errorMessage.value = "Please enter a valid phone number (at least 7 digits)."
            return
        }

        val normalizedPhone = ValidationUtils.normalizePhoneNumber(phoneNumber)

        // Duplicate check against existing contacts
        val duplicate = _contacts.value.find { existing ->
            ValidationUtils.arePhoneNumbersMatching(existing.phoneNumber, normalizedPhone)
        }
        if (duplicate != null) {
            _errorMessage.value = "A contact with this number already exists: ${duplicate.name} (${duplicate.phoneNumber})."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val contact = EmergencyContact(
                name = trimmedName,
                phoneNumber = normalizedPhone,
                relationship = relationship.trim().ifBlank { "Emergency Contact" },
                enabled = enabled,
                isActive = enabled,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            when (val result = contactsRepository.addContact(userId, contact)) {
                is Resource.Success -> {
                    _errorMessage.value = null
                    _successMessage.value = "Trusted contact '$trimmedName' added successfully."
                    loadContacts()
                    onSuccess?.invoke()
                }
                is Resource.Error -> {
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun toggleContactActive(contact: EmergencyContact) {
        val userId = authRepository.currentUserId ?: return
        val newEnabled = !contact.enabled
        viewModelScope.launch {
            val updated = contact.copy(
                enabled = newEnabled,
                isActive = newEnabled,
                updatedAt = System.currentTimeMillis()
            )
            contactsRepository.updateContact(userId, updated)
        }
    }

    fun updateContact(
        contact: EmergencyContact,
        onSuccess: (() -> Unit)? = null
    ) {
        val userId = authRepository.currentUserId ?: return
        val trimmedName = contact.name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = "Contact name cannot be empty."
            return
        }
        if (!ValidationUtils.isValidPhoneNumber(contact.phoneNumber)) {
            _errorMessage.value = "Please enter a valid phone number."
            return
        }

        val normalizedPhone = ValidationUtils.normalizePhoneNumber(contact.phoneNumber)

        // Check duplicates excluding the current contact being edited
        val duplicate = _contacts.value.find { existing ->
            existing.id != contact.id && ValidationUtils.arePhoneNumbersMatching(existing.phoneNumber, normalizedPhone)
        }
        if (duplicate != null) {
            _errorMessage.value = "Another contact (${duplicate.name}) already has this phone number."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val updated = contact.copy(
                name = trimmedName,
                phoneNumber = normalizedPhone,
                relationship = contact.relationship.trim().ifBlank { "Emergency Contact" },
                isActive = contact.enabled,
                updatedAt = System.currentTimeMillis()
            )

            when (val result = contactsRepository.updateContact(userId, updated)) {
                is Resource.Success -> {
                    _errorMessage.value = null
                    _successMessage.value = "Contact updated."
                    onSuccess?.invoke()
                }
                is Resource.Error -> {
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun deleteContact(contactId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = contactsRepository.deleteContact(userId, contactId)) {
                is Resource.Success -> {
                    _errorMessage.value = null
                    _successMessage.value = "Contact removed."
                }
                is Resource.Error -> {
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }
}
