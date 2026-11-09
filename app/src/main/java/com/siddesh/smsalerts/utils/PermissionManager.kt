package com.siddesh.smsalerts.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Feature-level descriptor for centralized permission handling.
 */
enum class AppPermissionFeature(
    val title: String,
    val rationale: String,
    val limitation: String
) {
    LOCATION(
        title = "Location Permission Required",
        rationale = "SafeAlerts needs location access to share your live location with your trusted contacts during an emergency and calculate safety routes.",
        limitation = "Without location permission, emergency alerts cannot include your live GPS coordinates or map link."
    ),
    BACKGROUND_LOCATION(
        title = "Background Location Required",
        rationale = "SafeAlerts needs \"Allow all the time\" location access so your journey or safety timer can continuously track and protect you even when the app is closed or the screen is locked.",
        limitation = "Without background location, live tracking will pause when the application is minimized."
    ),
    SMS(
        title = "SMS Permission Required",
        rationale = "SafeAlerts requires SMS permission to send emergency text messages with your location link directly to your emergency contacts.",
        limitation = "Without SMS permission, emergency SOS messages cannot be sent over cellular networks."
    ),
    CONTACTS(
        title = "Contacts Permission Required",
        rationale = "SafeAlerts needs access to your contacts so you can quickly choose and import your trusted emergency contacts from your address book.",
        limitation = "Without contacts access, you will need to type contact names and phone numbers manually."
    ),
    NOTIFICATIONS(
        title = "Notification Permission Required",
        rationale = "SafeAlerts needs notification permission to show emergency countdowns, active journey statuses, and safety check-in reminders.",
        limitation = "Without notification permission, background safety alerts and countdown warnings will not be visible."
    ),
    MICROPHONE(
        title = "Microphone Permission Required",
        rationale = "SafeAlerts needs microphone access to listen for emergency trigger phrases like \"help me\" when voice alerts are enabled.",
        limitation = "Without microphone access, voice-triggered emergency alerts cannot be detected."
    ),
    BLUETOOTH(
        title = "Bluetooth Permission Required",
        rationale = "SafeAlerts needs Bluetooth access to discover nearby devices and transmit offline SOS alerts and walkie-talkie messages without cellular or internet connectivity.",
        limitation = "Without Bluetooth permission, offline nearby SOS and mesh communication cannot function."
    )
}

/**
 * Centralized Permission Manager.
 * Checks, verifies, and manages runtime permissions contextually.
 */
object PermissionManager {

    const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
    const val PERMISSION_SEND_SMS = Manifest.permission.SEND_SMS
    const val PERMISSION_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    const val PERMISSION_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    val PERMISSION_POST_NOTIFICATIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        ""
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        if (permission.isBlank()) return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(context, Manifest.permission.BLUETOOTH) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN) &&
            hasLocationPermission(context)
        }
    }

    fun getBluetoothRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                PERMISSION_FINE_LOCATION
            )
        }
    }

    fun hasContactsPermission(context: Context): Boolean =
        hasPermission(context, PERMISSION_READ_CONTACTS)

    fun hasSmsPermission(context: Context): Boolean =
        hasPermission(context, PERMISSION_SEND_SMS)

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, PERMISSION_FINE_LOCATION) || hasPermission(context, PERMISSION_COARSE_LOCATION)

    fun hasFineLocationPermission(context: Context): Boolean =
        hasPermission(context, PERMISSION_FINE_LOCATION)

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasLocationPermission(context)
        }
    }

    fun hasRecordAudioPermission(context: Context): Boolean =
        hasPermission(context, PERMISSION_RECORD_AUDIO)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun isFeatureGranted(context: Context, feature: AppPermissionFeature): Boolean {
        return when (feature) {
            AppPermissionFeature.LOCATION -> hasLocationPermission(context)
            AppPermissionFeature.BACKGROUND_LOCATION -> hasBackgroundLocationPermission(context)
            AppPermissionFeature.SMS -> hasSmsPermission(context)
            AppPermissionFeature.CONTACTS -> hasContactsPermission(context)
            AppPermissionFeature.NOTIFICATIONS -> hasNotificationPermission(context)
            AppPermissionFeature.MICROPHONE -> hasRecordAudioPermission(context)
            AppPermissionFeature.BLUETOOTH -> hasBluetoothPermissions(context)
        }
    }

    fun getPermissionsForFeature(feature: AppPermissionFeature): List<String> {
        return when (feature) {
            AppPermissionFeature.LOCATION -> listOf(PERMISSION_FINE_LOCATION, PERMISSION_COARSE_LOCATION)
            AppPermissionFeature.BACKGROUND_LOCATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else emptyList()
            AppPermissionFeature.SMS -> listOf(PERMISSION_SEND_SMS)
            AppPermissionFeature.CONTACTS -> listOf(PERMISSION_READ_CONTACTS)
            AppPermissionFeature.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else emptyList()
            AppPermissionFeature.MICROPHONE -> listOf(PERMISSION_RECORD_AUDIO)
            AppPermissionFeature.BLUETOOTH -> getBluetoothRequiredPermissions()
        }
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        if (permission.isBlank()) return false
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun isPermanentlyDenied(activity: Activity, permissions: List<String>, hasAttempted: Boolean): Boolean {
        if (!hasAttempted) return false
        return permissions.any { perm ->
            !hasPermission(activity, perm) && !shouldShowRationale(activity, perm)
        }
    }

    fun getAllRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            PERMISSION_SEND_SMS,
            PERMISSION_FINE_LOCATION,
            PERMISSION_COARSE_LOCATION,
            PERMISSION_READ_CONTACTS,
            PERMISSION_RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    fun hasCoreEmergencyPermissions(context: Context): Boolean {
        return hasLocationPermission(context) && hasSmsPermission(context)
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        val hasLoc = hasLocationPermission(context)
        val hasSms = hasSmsPermission(context)
        val hasContacts = hasContactsPermission(context)
        val hasAudio = hasRecordAudioPermission(context)
        val hasNotifications = hasNotificationPermission(context)
        return hasLoc && hasSms && hasContacts && hasAudio && hasNotifications
    }

    fun getMissingPermissions(context: Context): List<String> {
        return getAllRequiredPermissions().filter { !hasPermission(context, it) }
    }

    data class AppPermissionInfo(
        val permission: String,
        val title: String,
        val description: String,
        val isGranted: Boolean,
        val isEssential: Boolean
    )

    fun getPermissionDescriptors(context: Context): List<AppPermissionInfo> {
        val list = mutableListOf(
            AppPermissionInfo(
                permission = PERMISSION_SEND_SMS,
                title = "Emergency SMS (SEND_SMS)",
                description = "Sends emergency SMS from your SIM to your saved contacts for SOS or the automatic triggers you enable. Carrier charges may apply. Does not read your SMS inbox.",
                isGranted = hasSmsPermission(context),
                isEssential = true
            ),
            AppPermissionInfo(
                permission = PERMISSION_FINE_LOCATION,
                title = "GPS Location (Precise & Coarse)",
                description = "Adds your location to emergency alerts and updates authorized recipients during an active sharing session, including in the background with a visible notification.",
                isGranted = hasLocationPermission(context),
                isEssential = true
            ),
            AppPermissionInfo(
                permission = PERMISSION_READ_CONTACTS,
                title = "Read Contacts",
                description = "Allows selecting and importing trusted emergency contacts from your phone address book.",
                isGranted = hasContactsPermission(context),
                isEssential = false
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                AppPermissionInfo(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Safety Notifications",
                    description = "Alerts you regarding safety check-in status, background protection, and emergency delivery.",
                    isGranted = hasNotificationPermission(context),
                    isEssential = false
                )
            )
        }

        list.add(
            AppPermissionInfo(
                permission = PERMISSION_RECORD_AUDIO,
                title = "Microphone (Voice Emergency)",
                description = "Listens for emergency phrases on Home or during a speech test. On-device recognition is preferred; your phone's speech provider may process audio online if it is unavailable.",
                isGranted = hasRecordAudioPermission(context),
                isEssential = false
            )
        )

        return list
    }

    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createLocationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openAppSettings(context: Context) {
        context.startActivity(createAppSettingsIntent(context))
    }

    fun openLocationSettings(context: Context) {
        context.startActivity(createLocationSettingsIntent())
    }
}
