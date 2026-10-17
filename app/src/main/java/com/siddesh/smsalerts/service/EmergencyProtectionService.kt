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
import android.util.Log
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
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EmergencyProtectionService onCreate")
        // CRITICAL ROOT-CAUSE FIX:
        // Transition to foreground immediately in onCreate() to satisfy Android's strict FGS start window
        // and eliminate ForegroundServiceDidNotStartInTimeException.
        promoteToForeground()

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
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                if (prefs.bluetoothMeshEnabled) {
                    com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).startMesh()
                } else {
                    com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).stopMesh()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_PROTECTION) {
            Log.d(TAG, "Stopping EmergencyProtectionService per user stop action")
            serviceScope.launch {
                preferences.updateShakeToAlertEnabled(false)
            }
            com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).stopMesh()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _serviceRunningFlow.value = false
            return START_NOT_STICKY
        }

        // Ensure foreground status and persistent notification remain active
        promoteToForeground()

        shakeDetector.startListening()
        if (com.siddesh.smsalerts.utils.PermissionManager.hasBluetoothPermissions(this)) {
            com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).startMesh()
        }
        Log.i(TAG, "EmergencyProtectionService active in foreground (persistent notification posted)")

        return START_STICKY
    }

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
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                // On API 26..33 (Android 8 to 13), use standard startForeground without API 34-only flags
                startForeground(NOTIFICATION_ID, notification)
            }
            _serviceRunningFlow.value = true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground with types failed (${e.message}); attempting universal fallback", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
                _serviceRunningFlow.value = true
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fatal: startForeground fallback also failed: ${fallbackError.message}", fallbackError)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _serviceRunningFlow.value = false
        shakeDetector.stopListening()
        com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(applicationContext).stopMesh()

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
                "Safe Alerts Emergency Protection",
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
            .setContentTitle("Safe Alerts")
            .setContentText("Emergency protection is active. Shake detection is running.")
            .setSubText("Emergency Protection")
            .setSmallIcon(R.drawable.ic_notification_shield)
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
