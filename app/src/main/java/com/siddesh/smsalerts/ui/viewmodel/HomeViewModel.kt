package com.siddesh.smsalerts.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.EmergencyType
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.model.UserProfile
import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import com.siddesh.smsalerts.data.sms.SmsSender
import com.siddesh.smsalerts.service.SafetyCheckInManager
import com.siddesh.smsalerts.service.SafetyCheckInState
import com.siddesh.smsalerts.utils.NetworkStatusInfo
import com.siddesh.smsalerts.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class ServiceReadinessState(
    val isReady: Boolean = false,
    val title: String = "Checking...",
    val subtitle: String = "",
    val hasContacts: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasLocationReady: Boolean = false
)

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: com.siddesh.smsalerts.data.repository.UserRepository = com.siddesh.smsalerts.data.repository.UserRepository.getInstance(),
    private val contactsRepository: ContactsRepository = ContactsRepository(),
    private val alertsRepository: AlertsRepository = AlertsRepository(),
    private val locationService: LocationService = LocationService(application)
) : AndroidViewModel(application) {

    private val smsSender = SmsSender(application)
    private val preferencesDataStore = UserPreferencesDataStore(application)
    val safetyCheckInManager = SafetyCheckInManager(application)

    val userProfile: StateFlow<UserProfile?> = userRepository.userProfile

    private val _contactsCount = MutableStateFlow(0)
    val contactsCount: StateFlow<Int> = _contactsCount.asStateFlow()

    private val _latestAlert = MutableStateFlow<EmergencyAlert?>(null)
    val latestAlert: StateFlow<EmergencyAlert?> = _latestAlert.asStateFlow()

    private val _networkStatus = MutableStateFlow(NetworkUtils.getNetworkStatus(application))
    val networkStatus: StateFlow<NetworkStatusInfo> = _networkStatus.asStateFlow()

    private val _isLocationReady = MutableStateFlow(false)
    val isLocationReady: StateFlow<Boolean> = _isLocationReady.asStateFlow()

    private val _selectedEmergencyType = MutableStateFlow(EmergencyType.MEDICAL)
    val selectedEmergencyType: StateFlow<EmergencyType> = _selectedEmergencyType.asStateFlow()

    private val _customNote = MutableStateFlow("")
    val customNote: StateFlow<String> = _customNote.asStateFlow()

    private val _requireHold = MutableStateFlow(true)
    val requireHold: StateFlow<Boolean> = _requireHold.asStateFlow()

    private val _userPreferences = MutableStateFlow(AppUserPreferences())
    val userPreferences: StateFlow<AppUserPreferences> = _userPreferences.asStateFlow()

    val safetyCheckInState: StateFlow<SafetyCheckInState> = safetyCheckInManager.checkInState

    private val _serviceReadiness = MutableStateFlow(ServiceReadinessState())
    val serviceReadiness: StateFlow<ServiceReadinessState> = _serviceReadiness.asStateFlow()

    private var contactsJob: kotlinx.coroutines.Job? = null
    private var alertsJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            preferencesDataStore.preferencesFlow.collectLatest { prefs ->
                _userPreferences.value = prefs
                _requireHold.value = prefs.requireHoldToConfirm
                if (_customNote.value.isBlank()) {
                    _customNote.value = prefs.defaultEmergencyMessage
                }
            }
        }

        // Ticking loop for safety check-in countdown
        viewModelScope.launch {
            while (isActive) {
                safetyCheckInManager.updateRemainingTime()
                delay(1000L)
            }
        }

        refreshDashboard()
    }

    fun refreshDashboard() {
        val app = getApplication<Application>()
        _networkStatus.value = NetworkUtils.getNetworkStatus(app)
        val hasLoc = locationService.hasLocationPermission() && locationService.isGpsEnabled()
        _isLocationReady.value = hasLoc
        val hasSms = smsSender.hasSmsPermission()

        val userId = authRepository.currentUserId
        if (userId != null) {
            contactsJob?.cancel()
            contactsJob = viewModelScope.launch {
                contactsRepository.getContactsFlow(userId).collectLatest { contacts ->
                    val activeCount = contacts.count { it.isActive }
                    _contactsCount.value = activeCount
                    recomputeReadiness(activeCount, hasSms, hasLoc)
                }
            }

            alertsJob?.cancel()
            alertsJob = viewModelScope.launch {
                alertsRepository.getAlertsFlow(userId).collectLatest { alerts ->
                    _latestAlert.value = alerts.firstOrNull()
                }
            }
        } else {
            contactsJob?.cancel()
            alertsJob?.cancel()
            recomputeReadiness(0, hasSms, hasLoc)
        }
    }

    private fun recomputeReadiness(contactsCount: Int, hasSms: Boolean, hasLoc: Boolean) {
        val isReady = contactsCount > 0 && hasSms
        val title = when {
            contactsCount == 0 -> "Setup Required: No Contacts"
            !hasSms -> "Setup Required: SMS Permission"
            else -> "SYSTEM READY"
        }
        val subtitle = when {
            contactsCount == 0 -> "Add at least 1 trusted contact to broadcast emergency alerts"
            !hasSms -> "Grant SEND_SMS permission for automatic direct dispatch"
            !hasLoc -> "Active (Location disabled - alerts will send without coordinates)"
            else -> "Your emergency contacts are available."
        }

        _serviceReadiness.value = ServiceReadinessState(
            isReady = isReady,
            title = title,
            subtitle = subtitle,
            hasContacts = contactsCount > 0,
            hasSmsPermission = hasSms,
            hasLocationReady = hasLoc
        )
    }

    fun getDynamicGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        val profile = userProfile.value
        val name = when {
            !profile?.fullName.isNullOrBlank() -> profile!!.fullName.trim().split(" ").first()
            !profile?.name.isNullOrBlank() -> profile!!.name.trim().split(" ").first()
            authRepository.currentUser?.displayName?.isNotBlank() == true -> {
                authRepository.currentUser!!.displayName!!.trim().split(" ").first()
            }
            else -> authRepository.currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"
        }

        return "$timeGreeting, $name"
    }

    fun setEmergencyType(type: EmergencyType) {
        _selectedEmergencyType.value = type
    }

    fun setCustomNote(note: String) {
        _customNote.value = note
    }

    fun toggleRequireHold() {
        val newVal = !_requireHold.value
        _requireHold.value = newVal
        viewModelScope.launch {
            preferencesDataStore.updateRequireHold(newVal)
        }
    }

    fun startSafetyCheckIn(durationMinutes: Int, graceMinutes: Int = 2) {
        safetyCheckInManager.startCheckIn(durationMinutes, graceMinutes)
    }

    fun pauseSafetyCheckIn() {
        safetyCheckInManager.pauseCheckIn()
    }

    fun resumeSafetyCheckIn() {
        safetyCheckInManager.resumeCheckIn()
    }

    fun markSafeOrCancelCheckIn() {
        safetyCheckInManager.markSafeOrCancel()
    }

    fun refreshLocation() {
        val hasLoc = locationService.hasLocationPermission() && locationService.isGpsEnabled()
        _isLocationReady.value = hasLoc
    }

    fun refreshUser() {
        refreshDashboard()
    }
}
