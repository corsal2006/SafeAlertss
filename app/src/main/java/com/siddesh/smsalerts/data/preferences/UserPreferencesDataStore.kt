package com.siddesh.smsalerts.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "safealert_preferences")

data class AppUserPreferences(
    val defaultEmergencyMessage: String = "Emergency alert: I may need assistance. Please contact me as soon as possible.",
<<<<<<< HEAD
    val locationSharingEnabled: Boolean = false,
=======
    val locationSharingEnabled: Boolean = true,
>>>>>>> origin/main
    val countdownDurationSeconds: Int = 5,
    val requireHoldToConfirm: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val themeMode: String = "dark", // "dark", "light", "system"
    val directSmsEnabled: Boolean = true,
    val safetyCheckInDurationMinutes: Int = 30,
    val safetyCheckInActive: Boolean = false,
    val safetyCheckInTargetTimestamp: Long = 0L,
<<<<<<< HEAD
    val safetyCheckInGracePeriodMinutes: Int = 2,
    val safetyCheckInGracePeriodActive: Boolean = false,
    val safetyCheckInGracePeriodTargetTimestamp: Long = 0L,
    val safetyCheckInPaused: Boolean = false,
    val safetyCheckInRemainingSecondsOnPause: Long = 0L,
    val safetyCheckInEscalationPolicy: String = "AUTO_EMERGENCY",
    // Women's Safety Journey
    val womensSafetyActive: Boolean = false,
    val womensSafetyDestination: String = "",
    val journeyLatitude: String = "",
    val journeyLongitude: String = "",
    val womensSafetyDurationMinutes: Int = 30,
    val womensSafetyTargetTimestamp: Long = 0L,
    val womensSafetyGracePeriodMinutes: Int = 2,
    val womensSafetyGracePeriodActive: Boolean = false,
    val womensSafetyGracePeriodTargetTimestamp: Long = 0L,
    val womensSafetyShareLiveLocation: Boolean = true,
    val womensSafetyAlertHelpline1091: Boolean = true,
    val womensSafetyEscalationPolicy: String = "AUTO_EMERGENCY",
    // Trusted Circle
    val trustedCircleEnabled: Boolean = true,
    val trustedCircleShareMyLocation: Boolean = false,
    val trustedCircleShareDurationHours: Int = 8,
    val trustedCircleSharingStartTimestamp: Long = 0L,
=======
>>>>>>> origin/main
    val pendingEmailLinkEmail: String = "",
    val rememberEmail: Boolean = false,
    val savedEmail: String = "",
    // Advanced Emergency Trigger settings
    val emergencyButtonEnabled: Boolean = true,
<<<<<<< HEAD
    val shakeToAlertEnabled: Boolean = false,
    val requiredShakes: Int = 5,
=======
    val shakeToAlertEnabled: Boolean = true,
    val requiredShakes: Int = 3,
>>>>>>> origin/main
    val shakeCooldownSeconds: Int = 30,
    val voiceAlertEnabled: Boolean = false,
    val emergencyCountdownSeconds: Int = 5,
    val requireConfirmation: Boolean = true,
<<<<<<< HEAD
    val allowSmsWithoutLocation: Boolean = true,
    // Offline Bluetooth Mesh settings
    val bluetoothMeshEnabled: Boolean = false,
    val pseudonymousDeviceId: String = "",
    // Custom Voice Emergency Trigger phrases (comma separated or raw)
    val customEmergencyPhrases: String = "",
    val phraseStorageError: String = "",
    // Emergency Services configuration (India verified numbers)
    val policeNumber: String = "112",
    val ambulanceNumber: String = "108",
    val fireNumber: String = "101",
    val womensHelplineNumber: String = "181",
    val womensCrisisNumber: String = "1091",
    val policeEnabled: Boolean = true,
    val ambulanceEnabled: Boolean = true,
    val fireEnabled: Boolean = true,
    val womensHelplineEnabled: Boolean = true,
    val womensCrisisEnabled: Boolean = true,
    // Emergency Profile & Medical details
    val bloodGroup: String = "",
    val medicalNotes: String = "",
    val allergies: String = "",
    // Live Location Tracking during active emergency
    val liveLocationSharingActive: Boolean = false,
    val activeEmergencyIncidentId: String = "",
    // Emergency Escalation tracking (persisted across restarts)
    val safetyCheckInEscalated: Boolean = false,
    val safetyCheckInIncidentId: String = "",
    val womensSafetyEscalated: Boolean = false,
    val womensSafetyIncidentId: String = "",
    val checkInSessionId: String = "",
    val journeySessionId: String = "",
    val checkInOwnerUid: String = "",
    val journeyOwnerUid: String = ""
=======
    val allowSmsWithoutLocation: Boolean = true
>>>>>>> origin/main
)

class UserPreferencesDataStore(private val context: Context) {

    private object PreferenceKeys {
<<<<<<< HEAD
        val CHECK_SESSION = stringPreferencesKey("check_session")
        val JOURNEY_LAT = stringPreferencesKey("journey_lat")
        val JOURNEY_LON = stringPreferencesKey("journey_lon")
        val JOURNEY_SESSION = stringPreferencesKey("journey_session")
        val CHECK_OWNER = stringPreferencesKey("check_owner")
        val JOURNEY_OWNER = stringPreferencesKey("journey_owner")
=======
>>>>>>> origin/main
        val EMERGENCY_MESSAGE = stringPreferencesKey("emergency_message")
        val LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        val COUNTDOWN_DURATION = intPreferencesKey("countdown_duration")
        val REQUIRE_HOLD = booleanPreferencesKey("require_hold")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DIRECT_SMS = booleanPreferencesKey("direct_sms")
        val CHECK_IN_DURATION = intPreferencesKey("check_in_duration")
        val CHECK_IN_ACTIVE = booleanPreferencesKey("check_in_active")
        val CHECK_IN_TARGET_TIME = longPreferencesKey("check_in_target_time")
        val PENDING_EMAIL_LINK_EMAIL = stringPreferencesKey("pending_email_link_email")
        val REMEMBER_EMAIL = booleanPreferencesKey("remember_email")
        val SAVED_EMAIL = stringPreferencesKey("saved_email")
        val EMERGENCY_BUTTON_ENABLED = booleanPreferencesKey("emergency_button_enabled")
        val SHAKE_TO_ALERT_ENABLED = booleanPreferencesKey("shake_to_alert_enabled")
        val REQUIRED_SHAKES = intPreferencesKey("required_shakes")
        val SHAKE_COOLDOWN_SECONDS = intPreferencesKey("shake_cooldown_seconds")
        val VOICE_ALERT_ENABLED = booleanPreferencesKey("voice_alert_enabled")
        val EMERGENCY_COUNTDOWN_SECONDS = intPreferencesKey("emergency_countdown_seconds")
        val REQUIRE_CONFIRMATION = booleanPreferencesKey("require_confirmation")
        val ALLOW_SMS_WITHOUT_LOCATION = booleanPreferencesKey("allow_sms_without_location")
<<<<<<< HEAD
        val BLUETOOTH_MESH_ENABLED = booleanPreferencesKey("bluetooth_mesh_enabled")
        val PSEUDONYMOUS_DEVICE_ID = stringPreferencesKey("pseudonymous_device_id")
        val CUSTOM_EMERGENCY_PHRASES = stringPreferencesKey("custom_emergency_phrases")
        val ENCRYPTED_EMERGENCY_PHRASES = stringPreferencesKey("custom_emergency_phrases_encrypted")
        val POLICE_NUMBER = stringPreferencesKey("police_number")
        val AMBULANCE_NUMBER = stringPreferencesKey("ambulance_number")
        val FIRE_NUMBER = stringPreferencesKey("fire_number")
        val WOMENS_HELPLINE_NUMBER = stringPreferencesKey("womens_helpline_number")
        val WOMENS_CRISIS_NUMBER = stringPreferencesKey("womens_crisis_number")
        val POLICE_ENABLED = booleanPreferencesKey("police_enabled")
        val AMBULANCE_ENABLED = booleanPreferencesKey("ambulance_enabled")
        val FIRE_ENABLED = booleanPreferencesKey("fire_enabled")
        val WOMENS_HELPLINE_ENABLED = booleanPreferencesKey("womens_helpline_enabled")
        val WOMENS_CRISIS_ENABLED = booleanPreferencesKey("womens_crisis_enabled")
        val BLOOD_GROUP = stringPreferencesKey("blood_group")
        val MEDICAL_NOTES = stringPreferencesKey("medical_notes")
        val ALLERGIES = stringPreferencesKey("allergies")
        val LIVE_LOCATION_SHARING_ACTIVE = booleanPreferencesKey("live_location_sharing_active")
        val ACTIVE_EMERGENCY_INCIDENT_ID = stringPreferencesKey("active_emergency_incident_id")
        val CHECK_IN_GRACE_PERIOD_MINS = intPreferencesKey("check_in_grace_period_mins")
        val CHECK_IN_GRACE_ACTIVE = booleanPreferencesKey("check_in_grace_active")
        val CHECK_IN_GRACE_TARGET_TIME = longPreferencesKey("check_in_grace_target_time")
        val CHECK_IN_PAUSED = booleanPreferencesKey("check_in_paused")
        val CHECK_IN_REMAINING_SECS_ON_PAUSE = longPreferencesKey("check_in_remaining_secs_on_pause")
        val CHECK_IN_ESCALATION_POLICY = stringPreferencesKey("check_in_escalation_policy")
        val WOMENS_SAFETY_ACTIVE = booleanPreferencesKey("womens_safety_active")
        val WOMENS_SAFETY_DESTINATION = stringPreferencesKey("womens_safety_destination")
        val WOMENS_SAFETY_DURATION_MINS = intPreferencesKey("womens_safety_duration_mins")
        val WOMENS_SAFETY_TARGET_TIME = longPreferencesKey("womens_safety_target_time")
        val WOMENS_SAFETY_GRACE_MINS = intPreferencesKey("womens_safety_grace_mins")
        val WOMENS_SAFETY_GRACE_ACTIVE = booleanPreferencesKey("womens_safety_grace_active")
        val WOMENS_SAFETY_GRACE_TARGET_TIME = longPreferencesKey("womens_safety_grace_target_time")
        val WOMENS_SAFETY_SHARE_LIVE_LOC = booleanPreferencesKey("womens_safety_share_live_loc")
        val WOMENS_SAFETY_ALERT_1091 = booleanPreferencesKey("womens_safety_alert_1091")
        val WOMENS_SAFETY_ESCALATION_POLICY = stringPreferencesKey("womens_safety_escalation_policy")
        val TRUSTED_CIRCLE_ENABLED = booleanPreferencesKey("trusted_circle_enabled")
        val TRUSTED_CIRCLE_SHARE_MY_LOCATION = booleanPreferencesKey("trusted_circle_share_my_location")
        val TRUSTED_CIRCLE_SHARE_DURATION_HOURS = intPreferencesKey("trusted_circle_share_duration_hours")
        val TRUSTED_CIRCLE_SHARING_START_TIME = longPreferencesKey("trusted_circle_sharing_start_time")
        val CHECK_IN_ESCALATED = booleanPreferencesKey("check_in_escalated")
        val CHECK_IN_INCIDENT_ID = stringPreferencesKey("check_in_incident_id")
        val WOMENS_SAFETY_ESCALATED = booleanPreferencesKey("womens_safety_escalated")
        val WOMENS_SAFETY_INCIDENT_ID = stringPreferencesKey("womens_safety_incident_id")
=======
>>>>>>> origin/main
    }

    val preferencesFlow: Flow<AppUserPreferences> = context.userDataStore.data
        .catch { exception ->
<<<<<<< HEAD
            android.util.Log.e("DataStore", "DataStore read error, using defaults: ${exception.message}", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            val encryptedPhrases = preferences[PreferenceKeys.ENCRYPTED_EMERGENCY_PHRASES]
            var phraseError = ""
            val phrases = try { if (encryptedPhrases != null) {
                com.siddesh.smsalerts.mesh.MeshSecurity.decryptLocal(
                    android.util.Base64.decode(encryptedPhrases, android.util.Base64.NO_WRAP)
                ).toString(Charsets.UTF_8)
            } else {
                val legacy = preferences[PreferenceKeys.CUSTOM_EMERGENCY_PHRASES].orEmpty()
                if (legacy.isNotEmpty()) saveEncryptedPhrases(legacy)
                legacy
            } } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                phraseError = "Saved custom phrases could not be opened. Re-enter them in Emergency Triggers."
                com.siddesh.smsalerts.utils.PrivacyLog.e("VoicePhrases", "Secure phrase storage unavailable", e)
                ""
            }
            AppUserPreferences(
                defaultEmergencyMessage = preferences[PreferenceKeys.EMERGENCY_MESSAGE]
                    ?: "Emergency alert: I may need assistance. Please contact me as soon as possible.",
                locationSharingEnabled = preferences[PreferenceKeys.LOCATION_SHARING] ?: false,
                countdownDurationSeconds = com.siddesh.smsalerts.service.CountdownPolicy.seconds(preferences[PreferenceKeys.COUNTDOWN_DURATION] ?: 5),
=======
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppUserPreferences(
                defaultEmergencyMessage = preferences[PreferenceKeys.EMERGENCY_MESSAGE]
                    ?: "Emergency alert: I may need assistance. Please contact me as soon as possible.",
                locationSharingEnabled = preferences[PreferenceKeys.LOCATION_SHARING] ?: true,
                countdownDurationSeconds = preferences[PreferenceKeys.COUNTDOWN_DURATION] ?: 5,
>>>>>>> origin/main
                requireHoldToConfirm = preferences[PreferenceKeys.REQUIRE_HOLD] ?: true,
                hapticFeedbackEnabled = preferences[PreferenceKeys.HAPTIC_FEEDBACK] ?: true,
                themeMode = preferences[PreferenceKeys.THEME_MODE] ?: "dark",
                directSmsEnabled = preferences[PreferenceKeys.DIRECT_SMS] ?: true,
                safetyCheckInDurationMinutes = preferences[PreferenceKeys.CHECK_IN_DURATION] ?: 30,
                safetyCheckInActive = preferences[PreferenceKeys.CHECK_IN_ACTIVE] ?: false,
                safetyCheckInTargetTimestamp = preferences[PreferenceKeys.CHECK_IN_TARGET_TIME] ?: 0L,
<<<<<<< HEAD
                safetyCheckInGracePeriodMinutes = preferences[PreferenceKeys.CHECK_IN_GRACE_PERIOD_MINS] ?: 2,
                safetyCheckInGracePeriodActive = preferences[PreferenceKeys.CHECK_IN_GRACE_ACTIVE] ?: false,
                safetyCheckInGracePeriodTargetTimestamp = preferences[PreferenceKeys.CHECK_IN_GRACE_TARGET_TIME] ?: 0L,
                safetyCheckInPaused = preferences[PreferenceKeys.CHECK_IN_PAUSED] ?: false,
                safetyCheckInRemainingSecondsOnPause = preferences[PreferenceKeys.CHECK_IN_REMAINING_SECS_ON_PAUSE] ?: 0L,
                safetyCheckInEscalationPolicy = preferences[PreferenceKeys.CHECK_IN_ESCALATION_POLICY] ?: "AUTO_EMERGENCY",
                womensSafetyActive = preferences[PreferenceKeys.WOMENS_SAFETY_ACTIVE] ?: false,
                womensSafetyDestination = preferences[PreferenceKeys.WOMENS_SAFETY_DESTINATION] ?: "",
                journeyLatitude = preferences[PreferenceKeys.JOURNEY_LAT] ?: "",
                journeyLongitude = preferences[PreferenceKeys.JOURNEY_LON] ?: "",
                womensSafetyDurationMinutes = preferences[PreferenceKeys.WOMENS_SAFETY_DURATION_MINS] ?: 30,
                womensSafetyTargetTimestamp = preferences[PreferenceKeys.WOMENS_SAFETY_TARGET_TIME] ?: 0L,
                womensSafetyGracePeriodMinutes = preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_MINS] ?: 2,
                womensSafetyGracePeriodActive = preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE] ?: false,
                womensSafetyGracePeriodTargetTimestamp = preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME] ?: 0L,
                womensSafetyShareLiveLocation = preferences[PreferenceKeys.WOMENS_SAFETY_SHARE_LIVE_LOC] ?: true,
                womensSafetyAlertHelpline1091 = preferences[PreferenceKeys.WOMENS_SAFETY_ALERT_1091] ?: true,
                womensSafetyEscalationPolicy = preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATION_POLICY] ?: "AUTO_EMERGENCY",
                trustedCircleEnabled = preferences[PreferenceKeys.TRUSTED_CIRCLE_ENABLED] ?: true,
                trustedCircleShareMyLocation = preferences[PreferenceKeys.TRUSTED_CIRCLE_SHARE_MY_LOCATION] ?: false,
                trustedCircleShareDurationHours = preferences[PreferenceKeys.TRUSTED_CIRCLE_SHARE_DURATION_HOURS] ?: 8,
                trustedCircleSharingStartTimestamp = preferences[PreferenceKeys.TRUSTED_CIRCLE_SHARING_START_TIME] ?: 0L,
=======
>>>>>>> origin/main
                pendingEmailLinkEmail = preferences[PreferenceKeys.PENDING_EMAIL_LINK_EMAIL] ?: "",
                rememberEmail = preferences[PreferenceKeys.REMEMBER_EMAIL] ?: false,
                savedEmail = preferences[PreferenceKeys.SAVED_EMAIL] ?: "",
                emergencyButtonEnabled = preferences[PreferenceKeys.EMERGENCY_BUTTON_ENABLED] ?: true,
<<<<<<< HEAD
                shakeToAlertEnabled = preferences[PreferenceKeys.SHAKE_TO_ALERT_ENABLED] ?: false,
                requiredShakes = 5,
                shakeCooldownSeconds = preferences[PreferenceKeys.SHAKE_COOLDOWN_SECONDS] ?: 30,
                voiceAlertEnabled = preferences[PreferenceKeys.VOICE_ALERT_ENABLED] ?: false,
                emergencyCountdownSeconds = com.siddesh.smsalerts.service.CountdownPolicy.seconds(preferences[PreferenceKeys.COUNTDOWN_DURATION] ?: preferences[PreferenceKeys.EMERGENCY_COUNTDOWN_SECONDS] ?: 5),
                requireConfirmation = preferences[PreferenceKeys.REQUIRE_CONFIRMATION] ?: true,
                allowSmsWithoutLocation = preferences[PreferenceKeys.ALLOW_SMS_WITHOUT_LOCATION] ?: true,
                bluetoothMeshEnabled = preferences[PreferenceKeys.BLUETOOTH_MESH_ENABLED] ?: false,
                pseudonymousDeviceId = preferences[PreferenceKeys.PSEUDONYMOUS_DEVICE_ID] ?: "",
                customEmergencyPhrases = phrases,
                phraseStorageError = phraseError,
                policeNumber = preferences[PreferenceKeys.POLICE_NUMBER]?.ifBlank { "112" } ?: "112",
                ambulanceNumber = preferences[PreferenceKeys.AMBULANCE_NUMBER]?.ifBlank { "108" } ?: "108",
                fireNumber = preferences[PreferenceKeys.FIRE_NUMBER]?.ifBlank { "101" } ?: "101",
                womensHelplineNumber = preferences[PreferenceKeys.WOMENS_HELPLINE_NUMBER]?.ifBlank { "181" } ?: "181",
                womensCrisisNumber = preferences[PreferenceKeys.WOMENS_CRISIS_NUMBER]?.ifBlank { "1091" } ?: "1091",
                policeEnabled = preferences[PreferenceKeys.POLICE_ENABLED] ?: true,
                ambulanceEnabled = preferences[PreferenceKeys.AMBULANCE_ENABLED] ?: true,
                fireEnabled = preferences[PreferenceKeys.FIRE_ENABLED] ?: true,
                womensHelplineEnabled = preferences[PreferenceKeys.WOMENS_HELPLINE_ENABLED] ?: true,
                womensCrisisEnabled = preferences[PreferenceKeys.WOMENS_CRISIS_ENABLED] ?: true,
                bloodGroup = preferences[PreferenceKeys.BLOOD_GROUP] ?: "",
                medicalNotes = preferences[PreferenceKeys.MEDICAL_NOTES] ?: "",
                allergies = preferences[PreferenceKeys.ALLERGIES] ?: "",
                liveLocationSharingActive = preferences[PreferenceKeys.LIVE_LOCATION_SHARING_ACTIVE] ?: false,
                activeEmergencyIncidentId = preferences[PreferenceKeys.ACTIVE_EMERGENCY_INCIDENT_ID] ?: "",
                safetyCheckInEscalated = preferences[PreferenceKeys.CHECK_IN_ESCALATED] ?: false,
                safetyCheckInIncidentId = preferences[PreferenceKeys.CHECK_IN_INCIDENT_ID] ?: "",
                womensSafetyEscalated = preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATED] ?: false,
                womensSafetyIncidentId = preferences[PreferenceKeys.WOMENS_SAFETY_INCIDENT_ID] ?: "",
                checkInSessionId = preferences[PreferenceKeys.CHECK_SESSION] ?: "",
                journeySessionId = preferences[PreferenceKeys.JOURNEY_SESSION] ?: "",
                checkInOwnerUid = preferences[PreferenceKeys.CHECK_OWNER] ?: "",
                journeyOwnerUid = preferences[PreferenceKeys.JOURNEY_OWNER] ?: ""
=======
                shakeToAlertEnabled = preferences[PreferenceKeys.SHAKE_TO_ALERT_ENABLED] ?: true,
                requiredShakes = preferences[PreferenceKeys.REQUIRED_SHAKES] ?: 3,
                shakeCooldownSeconds = preferences[PreferenceKeys.SHAKE_COOLDOWN_SECONDS] ?: 30,
                voiceAlertEnabled = preferences[PreferenceKeys.VOICE_ALERT_ENABLED] ?: false,
                emergencyCountdownSeconds = preferences[PreferenceKeys.EMERGENCY_COUNTDOWN_SECONDS] ?: 5,
                requireConfirmation = preferences[PreferenceKeys.REQUIRE_CONFIRMATION] ?: true,
                allowSmsWithoutLocation = preferences[PreferenceKeys.ALLOW_SMS_WITHOUT_LOCATION] ?: true
>>>>>>> origin/main
            )
        }

    suspend fun updateRememberEmail(remember: Boolean, email: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.REMEMBER_EMAIL] = remember
            if (remember) {
                preferences[PreferenceKeys.SAVED_EMAIL] = email.trim()
            } else {
                preferences.remove(PreferenceKeys.SAVED_EMAIL)
            }
        }
    }

    suspend fun updateEmergencyButtonEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.EMERGENCY_BUTTON_ENABLED] = enabled
        }
    }

    suspend fun updateShakeToAlert(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.SHAKE_TO_ALERT_ENABLED] = enabled
        }
    }

    suspend fun updateShakeToAlertEnabled(enabled: Boolean) = updateShakeToAlert(enabled)

    suspend fun updateRequiredShakes(shakes: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.REQUIRED_SHAKES] = shakes
        }
    }

    suspend fun updateShakeCooldown(seconds: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.SHAKE_COOLDOWN_SECONDS] = seconds
        }
    }

    suspend fun updateVoiceAlert(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.VOICE_ALERT_ENABLED] = enabled
        }
    }

    suspend fun updateRequireConfirmation(require: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.REQUIRE_CONFIRMATION] = require
        }
    }

    suspend fun updateAllowSmsWithoutLocation(allow: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.ALLOW_SMS_WITHOUT_LOCATION] = allow
        }
    }

    suspend fun updateEmergencyMessage(message: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.EMERGENCY_MESSAGE] = message
        }
    }

    suspend fun updateLocationSharing(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.LOCATION_SHARING] = enabled
        }
    }

    suspend fun updateCountdownDuration(seconds: Int) {
        context.userDataStore.edit { preferences ->
<<<<<<< HEAD
            require(seconds in setOf(3, 5, 10))
            preferences[PreferenceKeys.COUNTDOWN_DURATION] = seconds
            preferences[PreferenceKeys.EMERGENCY_COUNTDOWN_SECONDS] = seconds
=======
            preferences[PreferenceKeys.COUNTDOWN_DURATION] = seconds
>>>>>>> origin/main
        }
    }

    suspend fun updateRequireHold(requireHold: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.REQUIRE_HOLD] = requireHold
        }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun updateThemeMode(themeMode: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_MODE] = themeMode
        }
    }

    suspend fun updateDirectSms(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.DIRECT_SMS] = enabled
        }
    }

<<<<<<< HEAD
    suspend fun startSafetyCheckIn(
        durationMinutes: Int,
        targetTimestamp: Long,
        graceMinutes: Int = 2,
        escalationPolicy: String = "AUTO_EMERGENCY"
    ) {
        context.userDataStore.edit { preferences ->
            if (preferences[PreferenceKeys.CHECK_IN_ACTIVE] == true) return@edit
            require(durationMinutes in 1..1440 && graceMinutes in 0..60)
            preferences[PreferenceKeys.CHECK_SESSION] = java.util.UUID.randomUUID().toString()
            preferences[PreferenceKeys.CHECK_OWNER] = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            preferences[PreferenceKeys.CHECK_IN_ESCALATED] = false
            preferences[PreferenceKeys.CHECK_IN_INCIDENT_ID] = ""
            preferences[PreferenceKeys.CHECK_IN_DURATION] = durationMinutes
            preferences[PreferenceKeys.CHECK_IN_ACTIVE] = true
            preferences[PreferenceKeys.CHECK_IN_TARGET_TIME] = targetTimestamp
            preferences[PreferenceKeys.CHECK_IN_GRACE_PERIOD_MINS] = graceMinutes
            preferences[PreferenceKeys.CHECK_IN_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.CHECK_IN_GRACE_TARGET_TIME] = 0L
            preferences[PreferenceKeys.CHECK_IN_PAUSED] = false
            preferences[PreferenceKeys.CHECK_IN_REMAINING_SECS_ON_PAUSE] = 0L
            preferences[PreferenceKeys.CHECK_IN_ESCALATION_POLICY] = escalationPolicy
        }
    }

    suspend fun pauseSafetyCheckIn(remainingSeconds: Long) {
        context.userDataStore.edit { preferences ->
            if (preferences[PreferenceKeys.CHECK_IN_ACTIVE] != true || preferences[PreferenceKeys.CHECK_IN_GRACE_ACTIVE] == true) return@edit
            preferences[PreferenceKeys.CHECK_IN_PAUSED] = true
            preferences[PreferenceKeys.CHECK_IN_REMAINING_SECS_ON_PAUSE] = remainingSeconds
        }
    }

    suspend fun resumeSafetyCheckIn(newTargetTimestamp: Long) {
        context.userDataStore.edit { preferences ->
            if (preferences[PreferenceKeys.CHECK_IN_ACTIVE] != true || preferences[PreferenceKeys.CHECK_IN_PAUSED] != true) return@edit
            preferences[PreferenceKeys.CHECK_IN_PAUSED] = false
            preferences[PreferenceKeys.CHECK_IN_TARGET_TIME] = newTargetTimestamp
            preferences[PreferenceKeys.CHECK_IN_REMAINING_SECS_ON_PAUSE] = 0L
        }
    }

    suspend fun activateSafetyCheckInGracePeriod(graceTargetTimestamp: Long) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.CHECK_IN_GRACE_ACTIVE] = true
            preferences[PreferenceKeys.CHECK_IN_GRACE_TARGET_TIME] = graceTargetTimestamp
=======
    suspend fun startSafetyCheckIn(durationMinutes: Int, targetTimestamp: Long) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.CHECK_IN_DURATION] = durationMinutes
            preferences[PreferenceKeys.CHECK_IN_ACTIVE] = true
            preferences[PreferenceKeys.CHECK_IN_TARGET_TIME] = targetTimestamp
>>>>>>> origin/main
        }
    }

    suspend fun cancelOrCompleteSafetyCheckIn() {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.CHECK_IN_ACTIVE] = false
            preferences[PreferenceKeys.CHECK_IN_TARGET_TIME] = 0L
<<<<<<< HEAD
            preferences[PreferenceKeys.CHECK_IN_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.CHECK_IN_GRACE_TARGET_TIME] = 0L
            preferences[PreferenceKeys.CHECK_IN_PAUSED] = false
            preferences[PreferenceKeys.CHECK_IN_REMAINING_SECS_ON_PAUSE] = 0L
            preferences[PreferenceKeys.CHECK_IN_ESCALATED] = false
            preferences[PreferenceKeys.CHECK_IN_INCIDENT_ID] = ""
        }
    }

    suspend fun escalateSafetyCheckIn(incidentId: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.CHECK_IN_ACTIVE] = false
            preferences[PreferenceKeys.CHECK_IN_TARGET_TIME] = 0L
            preferences[PreferenceKeys.CHECK_IN_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.CHECK_IN_GRACE_TARGET_TIME] = 0L
            preferences[PreferenceKeys.CHECK_IN_PAUSED] = false
            preferences[PreferenceKeys.CHECK_IN_REMAINING_SECS_ON_PAUSE] = 0L
            preferences[PreferenceKeys.CHECK_IN_ESCALATED] = true
            preferences[PreferenceKeys.CHECK_IN_INCIDENT_ID] = incidentId
        }
    }

    suspend fun clearSafetyCheckInEscalation() {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.CHECK_IN_ESCALATED] = false
            preferences[PreferenceKeys.CHECK_IN_INCIDENT_ID] = ""
        }
    }

    suspend fun startWomensSafetyJourney(
        destination: String,
        durationMinutes: Int = 30,
        targetTimestamp: Long,
        gracePeriodMinutes: Int = 2,
        shareLiveLocation: Boolean = true,
        alertHelpline1091: Boolean = true,
        escalationPolicy: String = "AUTO_EMERGENCY",
        latitude: Double? = null, longitude: Double? = null
    ) {
        context.userDataStore.edit { preferences ->
            if (preferences[PreferenceKeys.WOMENS_SAFETY_ACTIVE] == true) return@edit
            require(durationMinutes in 1..1440 && gracePeriodMinutes in 0..60)
            preferences[PreferenceKeys.JOURNEY_SESSION] = java.util.UUID.randomUUID().toString()
            preferences[PreferenceKeys.JOURNEY_OWNER] = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            preferences[PreferenceKeys.WOMENS_SAFETY_ACTIVE] = true
            preferences[PreferenceKeys.WOMENS_SAFETY_DESTINATION] = destination
            preferences[PreferenceKeys.JOURNEY_LAT] = latitude?.toString().orEmpty()
            preferences[PreferenceKeys.JOURNEY_LON] = longitude?.toString().orEmpty()
            preferences[PreferenceKeys.WOMENS_SAFETY_DURATION_MINS] = durationMinutes
            preferences[PreferenceKeys.WOMENS_SAFETY_TARGET_TIME] = targetTimestamp
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_MINS] = gracePeriodMinutes
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME] = 0L
            preferences[PreferenceKeys.WOMENS_SAFETY_SHARE_LIVE_LOC] = shareLiveLocation
            preferences[PreferenceKeys.WOMENS_SAFETY_ALERT_1091] = alertHelpline1091
            preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATION_POLICY] = escalationPolicy
            preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATED] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_INCIDENT_ID] = ""
        }
    }

    suspend fun extendWomensSafetyJourney(newTargetTimestamp: Long) {
        context.userDataStore.edit { preferences ->
            if (preferences[PreferenceKeys.WOMENS_SAFETY_ACTIVE] != true) return@edit
            preferences[PreferenceKeys.WOMENS_SAFETY_TARGET_TIME] = newTargetTimestamp
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME] = 0L
        }
    }

    suspend fun activateWomensSafetyGracePeriod(graceTargetTimestamp: Long) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE] = true
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME] = graceTargetTimestamp
        }
    }

    suspend fun cancelOrCompleteWomensSafetyJourney() {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_SAFETY_ACTIVE] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_DESTINATION] = ""
            preferences[PreferenceKeys.WOMENS_SAFETY_TARGET_TIME] = 0L
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME] = 0L
            preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATED] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_INCIDENT_ID] = ""
        }
    }

    suspend fun escalateWomensSafety(incidentId: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_SAFETY_ACTIVE] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_DESTINATION] = ""
            preferences[PreferenceKeys.WOMENS_SAFETY_TARGET_TIME] = 0L
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME] = 0L
            preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATED] = true
            preferences[PreferenceKeys.WOMENS_SAFETY_INCIDENT_ID] = incidentId
        }
    }

    suspend fun clearWomensSafetyEscalation() {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_SAFETY_ESCALATED] = false
            preferences[PreferenceKeys.WOMENS_SAFETY_INCIDENT_ID] = ""
        }
    }

    suspend fun updateTrustedCircleSharing(
        enabled: Boolean,
        shareLocation: Boolean,
        durationHours: Int
    ) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.TRUSTED_CIRCLE_ENABLED] = enabled
            preferences[PreferenceKeys.TRUSTED_CIRCLE_SHARE_MY_LOCATION] = shareLocation
            preferences[PreferenceKeys.TRUSTED_CIRCLE_SHARE_DURATION_HOURS] = durationHours
            preferences[PreferenceKeys.TRUSTED_CIRCLE_SHARING_START_TIME] = System.currentTimeMillis()
=======
>>>>>>> origin/main
        }
    }

    suspend fun savePendingEmailForLink(email: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.PENDING_EMAIL_LINK_EMAIL] = email
        }
    }

    suspend fun clearPendingEmailForLink() {
        context.userDataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.PENDING_EMAIL_LINK_EMAIL)
        }
    }
<<<<<<< HEAD

    suspend fun updateBluetoothMeshEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.BLUETOOTH_MESH_ENABLED] = enabled
        }
    }

    suspend fun updatePseudonymousDeviceId(id: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.PSEUDONYMOUS_DEVICE_ID] = id
        }
    }

    suspend fun updateCustomEmergencyPhrases(phrases: String) {
        val normalized = phrases.split(",").filter { it.isNotBlank() }
            .map(com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector::normalizePhrase).distinct()
        require(normalized.size <= 12 && normalized.all(com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector::validCustomPhrase)) {
            "Use up to 12 phrases, each with 2–8 words and 3–64 letters or numbers."
        }
        saveEncryptedPhrases(normalized.joinToString(","))
    }

    private suspend fun saveEncryptedPhrases(phrases: String) {
        val encrypted = android.util.Base64.encodeToString(
            com.siddesh.smsalerts.mesh.MeshSecurity.encryptLocal(phrases.toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP)
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.ENCRYPTED_EMERGENCY_PHRASES] = encrypted
            preferences.remove(PreferenceKeys.CUSTOM_EMERGENCY_PHRASES)
        }
    }

    suspend fun updateEmergencyServices(
        police: String,
        ambulance: String,
        fire: String,
        womensHelpline: String = "181",
        policeEnabled: Boolean = true,
        ambulanceEnabled: Boolean = true,
        fireEnabled: Boolean = true,
        womensHelplineEnabled: Boolean = true
    ) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.POLICE_NUMBER] = police.trim()
            preferences[PreferenceKeys.AMBULANCE_NUMBER] = ambulance.trim()
            preferences[PreferenceKeys.FIRE_NUMBER] = fire.trim()
            preferences[PreferenceKeys.WOMENS_HELPLINE_NUMBER] = womensHelpline.trim()
            preferences[PreferenceKeys.POLICE_ENABLED] = policeEnabled
            preferences[PreferenceKeys.AMBULANCE_ENABLED] = ambulanceEnabled
            preferences[PreferenceKeys.FIRE_ENABLED] = fireEnabled
            preferences[PreferenceKeys.WOMENS_HELPLINE_ENABLED] = womensHelplineEnabled
        }
    }

    suspend fun updatePoliceEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.POLICE_ENABLED] = enabled
        }
    }

    suspend fun updateAmbulanceEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.AMBULANCE_ENABLED] = enabled
        }
    }

    suspend fun updateFireEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.FIRE_ENABLED] = enabled
        }
    }

    suspend fun updateWomensHelplineEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_HELPLINE_ENABLED] = enabled
        }
    }

    suspend fun updatePoliceNumber(number: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.POLICE_NUMBER] = number.trim()
        }
    }

    suspend fun updateAmbulanceNumber(number: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.AMBULANCE_NUMBER] = number.trim()
        }
    }

    suspend fun updateFireNumber(number: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.FIRE_NUMBER] = number.trim()
        }
    }

    suspend fun updateWomensHelplineNumber(number: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_HELPLINE_NUMBER] = number.trim()
        }
    }

    suspend fun updateWomensCrisisNumber(number: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_CRISIS_NUMBER] = number.trim()
        }
    }

    suspend fun updateWomensCrisisEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.WOMENS_CRISIS_ENABLED] = enabled
        }
    }

    suspend fun updateEmergencyProfile(bloodGroup: String, medicalNotes: String, allergies: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.BLOOD_GROUP] = bloodGroup.trim()
            preferences[PreferenceKeys.MEDICAL_NOTES] = medicalNotes.trim()
            preferences[PreferenceKeys.ALLERGIES] = allergies.trim()
        }
    }

    suspend fun updateLiveLocationTracking(active: Boolean, incidentId: String = "") {
        context.userDataStore.edit { preferences ->
            preferences[PreferenceKeys.LIVE_LOCATION_SHARING_ACTIVE] = active
            preferences[PreferenceKeys.ACTIVE_EMERGENCY_INCIDENT_ID] = if (active) incidentId else ""
        }
    }

    suspend fun migrateTimerSessions() {
        context.userDataStore.edit { p ->
            val owner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (p[PreferenceKeys.CHECK_IN_ACTIVE] == true && p[PreferenceKeys.CHECK_SESSION].isNullOrBlank()) {
                p[PreferenceKeys.CHECK_SESSION] = java.util.UUID.randomUUID().toString()
                p[PreferenceKeys.CHECK_OWNER] = owner
            }
            if (p[PreferenceKeys.WOMENS_SAFETY_ACTIVE] == true && p[PreferenceKeys.JOURNEY_SESSION].isNullOrBlank()) {
                p[PreferenceKeys.JOURNEY_SESSION] = java.util.UUID.randomUUID().toString()
                p[PreferenceKeys.JOURNEY_OWNER] = owner
            }
        }
    }

    suspend fun advanceTimer(journey: Boolean, session: String, deadline: Long, now: Long): String {
        var result = "INACTIVE"
        context.userDataStore.edit { p ->
            val sessionKey = if (journey) PreferenceKeys.JOURNEY_SESSION else PreferenceKeys.CHECK_SESSION
            val targetKey = if (journey) PreferenceKeys.WOMENS_SAFETY_TARGET_TIME else PreferenceKeys.CHECK_IN_TARGET_TIME
            val activeKey = if (journey) PreferenceKeys.WOMENS_SAFETY_ACTIVE else PreferenceKeys.CHECK_IN_ACTIVE
            val graceKey = if (journey) PreferenceKeys.WOMENS_SAFETY_GRACE_MINS else PreferenceKeys.CHECK_IN_GRACE_PERIOD_MINS
            val graceActiveKey = if (journey) PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE else PreferenceKeys.CHECK_IN_GRACE_ACTIVE
            val escalatedKey = if (journey) PreferenceKeys.WOMENS_SAFETY_ESCALATED else PreferenceKeys.CHECK_IN_ESCALATED
            val incidentKey = if (journey) PreferenceKeys.WOMENS_SAFETY_INCIDENT_ID else PreferenceKeys.CHECK_IN_INCIDENT_ID
            if (session.isBlank() || p[sessionKey] != session || p[targetKey] != deadline) return@edit
            if (p[escalatedKey] == true && p[incidentKey] == session) { result = "EXPIRED"; return@edit }
            val phase = com.siddesh.smsalerts.service.TimerPolicy.phase(p[activeKey] == true,
                !journey && p[PreferenceKeys.CHECK_IN_PAUSED] == true, deadline, p[graceKey] ?: 2, now)
            result = phase.name
            if (phase == com.siddesh.smsalerts.service.TimerPolicy.Phase.GRACE) {
                p[graceActiveKey] = true
                p[if (journey) PreferenceKeys.WOMENS_SAFETY_GRACE_TARGET_TIME else PreferenceKeys.CHECK_IN_GRACE_TARGET_TIME] = deadline + (p[graceKey] ?: 2) * 60_000L
            }
            if (phase == com.siddesh.smsalerts.service.TimerPolicy.Phase.EXPIRED) {
                p[activeKey] = false
                p[graceActiveKey] = false
                p[escalatedKey] = true
                p[incidentKey] = session
            }
        }
        return result
    }

    suspend fun confirmTimer(journey: Boolean, session: String, deadline: Long) {
        context.userDataStore.edit { p ->
            val sk = if (journey) PreferenceKeys.JOURNEY_SESSION else PreferenceKeys.CHECK_SESSION
            val tk = if (journey) PreferenceKeys.WOMENS_SAFETY_TARGET_TIME else PreferenceKeys.CHECK_IN_TARGET_TIME
            if (session.isBlank() || p[sk] != session || p[tk] != deadline) return@edit
            p[if (journey) PreferenceKeys.WOMENS_SAFETY_ACTIVE else PreferenceKeys.CHECK_IN_ACTIVE] = false
            p[if (journey) PreferenceKeys.WOMENS_SAFETY_GRACE_ACTIVE else PreferenceKeys.CHECK_IN_GRACE_ACTIVE] = false
            p[tk] = 0L
        }
    }
=======
>>>>>>> origin/main
}
