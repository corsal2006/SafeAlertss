package com.siddesh.smsalerts.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import com.siddesh.smsalerts.data.sms.SmsSender

class AppViewModelFactory(
    private val application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val contactsRepository: ContactsRepository = ContactsRepository(),
    private val alertsRepository: AlertsRepository = AlertsRepository(),
    private val locationService: LocationService = LocationService(application),
    private val smsSender: SmsSender = SmsSender(application)
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    application = application,
                    authRepository = authRepository,
                    contactsRepository = contactsRepository,
                    alertsRepository = alertsRepository,
                    locationService = locationService
                ) as T
            }
            modelClass.isAssignableFrom(SosViewModel::class.java) -> {
                SosViewModel(
                    application = application,
                    authRepository = authRepository,
                    contactsRepository = contactsRepository,
                    alertsRepository = alertsRepository,
                    locationService = locationService,
                    smsSender = smsSender
                ) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(authRepository = authRepository) as T
            }
            modelClass.isAssignableFrom(ContactsViewModel::class.java) -> {
                ContactsViewModel(
                    authRepository = authRepository,
                    contactsRepository = contactsRepository
                ) as T
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(
                    authRepository = authRepository,
                    alertsRepository = alertsRepository
                ) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(authRepository = authRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
