package com.siddesh.smsalerts.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
<<<<<<< HEAD
import com.siddesh.smsalerts.utils.PrivacyLog as Log
=======
import android.util.Log
>>>>>>> origin/main
import androidx.core.app.NotificationCompat
import com.siddesh.smsalerts.MainActivity
import com.siddesh.smsalerts.R
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.sensor.ShakeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
<<<<<<< HEAD
import kotlinx.coroutines.flow.first
=======
>>>>>>> origin/main
import kotlinx.coroutines.launch

private const val TAG = "SafeAlerts/Protection"

/**
 * Android Foreground Service for Real Background Emergency Shake Detection.
 *
 * Runs independently from the Compose UI. Continues monitoring the device accelerometer
 * when the app is minimized, when the user switches to other apps, and when the phone screen is locked.
 *
 * Fully adheres to Android 14+ (SDK 34) specialUse Foreground Service requirements.
 */
class EmergencyProtectionService : Service() {

    companion object {
        const val CHANNEL_ID = "emergency_protection_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_PROTECTION = "com.siddesh.smsalerts.ACTION_START_PROTECTION"
        const val ACTION_STOP_PROTECTION = "com.siddesh.smsalerts.ACTION_STOP_PROTECTION"

        private val _serviceRunningFlow = MutableStateFlow(false)
        val serviceRunningFlow: StateFlow<Boolean> = _serviceRunningFlow.asStateFlow()

        val isServiceRunning: Boolean
            get() = _serviceRunningFlow.value

<<<<<<< HEAD
        @Volatile var locationSessionActive = false
        fun startLocationSession(context: Context, incidentId: String) {
            try {
                val intent = Intent(context, EmergencyProtectionService::class.java).setAction("LOCATION_SESSION").putExtra("incident", incidentId)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (e: Exception) {
                locationSessionActive = false
                LiveLocationManager.getInstance(context).reportUnavailable(incidentId, "Location service could not start (${e.javaClass.simpleName}). Reopen the app and retry.")
                Log.w(TAG, "Location foreground service startup failed", e)
            }
        }
        fun refreshSessions(context: Context) {
            if (isServiceRunning) try {
                context.startService(Intent(context, EmergencyProtectionService::class.java).setAction("REFRESH_SESSIONS"))
            } catch (e: Exception) { Log.w(TAG, "Foreground service refresh unavailable", e) }
        }
=======
>>>>>>> origin/main
        fun startService(context: Context) {
            val intent = Intent(context, EmergencyProtectionService::class.java).apply {
                action = ACTION_START_PROTECTION
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start EmergencyProtectionService: ${e.message}", e)
            }
        }

        fun stopService(context: Context) {
<<<<<<< HEAD
            if (locationSessionActive) return
=======
>>>>>>> origin/main
            val intent = Intent(context, EmergencyProtectionService::class.java).apply {
                action = ACTION_STOP_PROTECTION
            }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping EmergencyProtectionService: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var preferences: UserPreferencesDataStore
<<<<<<< HEAD
    private var hasStarted = false
=======
>>>>>>> origin/main
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EmergencyProtectionService onCreate")
<<<<<<< HEAD
        // Promote promptly to comply with Android foreground service startup timing.
        promoteToForeground()

=======
>>>>>>> origin/main
        preferences = UserPreferencesDataStore(this)

        // Acquire partial WakeLock so accelerometer sensor events are delivered while screen is locked
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SafeAlerts:EmergencyShakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // Safe initial acquire with auto-renew
            }
            Log.d(TAG, "Acquired partial WakeLock for background/lockscreen sensor delivery")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }

        shakeDetector = ShakeDetector(this) {
            Log.i(TAG, "BACKGROUND SHAKE TRIGGER DETECTED! Delegating to EmergencyManager...")
            // Ensure WakeLock is refreshed during emergency dispatch
            try {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(30_000L)
                }
            } catch (_: Exception) {}

            val emergencyManager = EmergencyManager.getInstance(applicationContext)
            emergencyManager.triggerAutomaticEmergency("SHAKE")
        }

        serviceScope.launch {
            preferences.preferencesFlow.collectLatest { prefs ->
                shakeDetector.configure(
                    shakes = prefs.requiredShakes,
                    cooldown = prefs.shakeCooldownSeconds * 1000L
                )
                if (prefs.shakeToAlertEnabled) {
                    shakeDetector.startListening()
                } else {
                    shakeDetector.stopListening()
<<<<<<< HEAD
                    if (hasStarted && !locationSessionActive && !prefs.bluetoothMeshEnabled) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                }

                if (hasStarted && isServiceRunning) promoteToForeground()
                if (prefs.bluetoothMeshEnabled) {
                    com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).startMesh()
                } else {
                    com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).stopMesh()
=======
                    stopSelf()
>>>>>>> origin/main
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
<<<<<<< HEAD
        hasStarted = true
        if (intent?.action == "REFRESH_SESSIONS") {
            serviceScope.launch {
                val prefs = preferences.preferencesFlow.first()
                if (!locationSessionActive && !prefs.shakeToAlertEnabled && !prefs.bluetoothMeshEnabled) {
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                } else promoteToForeground()
            }
            return START_NOT_STICKY
        }
=======
>>>>>>> origin/main
        if (intent?.action == ACTION_STOP_PROTECTION) {
            Log.d(TAG, "Stopping EmergencyProtectionService per user stop action")
            serviceScope.launch {
                preferences.updateShakeToAlertEnabled(false)
            }
<<<<<<< HEAD
            com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).stopMesh()
=======
>>>>>>> origin/main
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _serviceRunningFlow.value = false
            return START_NOT_STICKY
        }

<<<<<<< HEAD
        if (intent?.action == "LOCATION_SESSION") {
            locationSessionActive = true
            promoteToForeground()
            if (locationSessionActive) LiveLocationManager.getInstance(this).beginTrackingFromService(intent.getStringExtra("incident").orEmpty())
            return START_NOT_STICKY
        }
        // Ensure foreground status and persistent notification remain active
        promoteToForeground()

        // Preference collection decides which optional features are actually enabled.
=======
        createNotificationChannel()
        val notification = buildPersistentNotification()
        startForeground(NOTIFICATION_ID, notification)
        _serviceRunningFlow.value = true

        shakeDetector.startListening()
>>>>>>> origin/main
        Log.i(TAG, "EmergencyProtectionService active in foreground (persistent notification posted)")

        return START_STICKY
    }

<<<<<<< HEAD
    /**
     * Promotes the service to the foreground in an Android lifecycle-safe and API-compatible manner.
     * Guards FOREGROUND_SERVICE_TYPE_SPECIAL_USE to API 34+ (UPSIDE_DOWN_CAKE) and provides a safe fallback.
     */
    private fun promoteToForeground() {
        createNotificationChannel()
        val notification = buildPersistentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (com.siddesh.smsalerts.utils.PermissionManager.hasBluetoothPermissions(this)) {
                    fgsType = fgsType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                if (locationSessionActive && com.siddesh.smsalerts.utils.PermissionManager.hasLocationPermission(this)) {
                    fgsType = fgsType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                try {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Type-specific startForeground failed; falling back to SPECIAL_USE", e)
                    locationSessionActive = false
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                }
            } else if (Build.VERSION.SDK_INT >= 29) {
                try {
                    startForeground(NOTIFICATION_ID, notification, if (locationSessionActive) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Location startForeground failed on API 29+; falling back to 0", e)
                    locationSessionActive = false
                    startForeground(NOTIFICATION_ID, notification, 0)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _serviceRunningFlow.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Foreground promotion failed completely", e)
            locationSessionActive = false
            _serviceRunningFlow.value = false
            try {
                // Ensure startForeground was called to satisfy Android foreground-service watchdog before stopping
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _serviceRunningFlow.value = false
        if (::shakeDetector.isInitialized) shakeDetector.stopListening()
        LiveLocationManager.getInstance(this).stopLiveSharing()
        com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).stopMesh()
=======
    override fun onDestroy() {
        super.onDestroy()
        _serviceRunningFlow.value = false
        shakeDetector.stopListening()
>>>>>>> origin/main

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released partial WakeLock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }

        serviceScope.cancel()
        Log.d(TAG, "EmergencyProtectionService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
<<<<<<< HEAD
                "SafeAlerts Emergency Protection",
=======
                "Safe Alerts Emergency Protection",
>>>>>>> origin/main
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while background emergency triggers are active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disableIntent = Intent(this, EmergencyProtectionService::class.java).apply {
            action = ACTION_STOP_PROTECTION
        }
        val pendingDisableIntent = PendingIntent.getService(
            this,
            1,
            disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
<<<<<<< HEAD
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (locationSessionActive) "Location sharing session active" else "Emergency protection service active")
            .setSubText("Emergency Protection")
            .setSmallIcon(R.drawable.ic_notification_shield)
=======
            .setContentTitle("Safe Alerts")
            .setContentText("Emergency protection is active. Shake detection is running.")
            .setSubText("Emergency Protection")
            .setSmallIcon(R.drawable.ic_app_logo)
>>>>>>> origin/main
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingOpenIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disable Protection",
                pendingDisableIntent
            )
            .build()
    }
}
