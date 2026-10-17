package com.siddesh.smsalerts.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {

    val REQUIRED_PERMISSIONS: Array<String>
        get() = PermissionManager.getAllRequiredPermissions().toTypedArray()

    fun hasPermission(context: Context, permission: String): Boolean =
        PermissionManager.hasPermission(context, permission)

    fun hasAllRequiredPermissions(context: Context): Boolean =
        PermissionManager.hasAllRequiredPermissions(context)

    fun isSmsGranted(context: Context): Boolean =
        PermissionManager.hasSmsPermission(context)

    fun hasSmsPermission(context: Context): Boolean =
        PermissionManager.hasSmsPermission(context)

    fun isLocationGranted(context: Context): Boolean =
        PermissionManager.hasLocationPermission(context)

    fun hasLocationPermission(context: Context): Boolean =
        PermissionManager.hasLocationPermission(context)
}
