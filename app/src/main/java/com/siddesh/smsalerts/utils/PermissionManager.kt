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

    fun hasRecordAudioPermission(context: Context): Boolean =
        hasPermission(context, PERMISSION_RECORD_AUDIO)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        if (permission.isBlank()) return false
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun getAllRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            PERMISSION_FINE_LOCATION,
            PERMISSION_COARSE_LOCATION,
            PERMISSION_SEND_SMS,
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
                permission = PERMISSION_FINE_LOCATION,
                title = "GPS Location (Precise & Coarse)",
                description = "Acquires real-time coordinates and generates a Google Maps link in your emergency alert SMS.",
                isGranted = hasLocationPermission(context),
                isEssential = true
            ),
            AppPermissionInfo(
                permission = PERMISSION_SEND_SMS,
                title = "Direct Emergency SMS",
                description = "Automatically sends emergency alerts directly via cellular network without requiring Internet.",
                isGranted = hasSmsPermission(context),
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
                description = "Enables hands-free emergency dispatch when you say 'HELP'. Audio is processed only on-device.",
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
