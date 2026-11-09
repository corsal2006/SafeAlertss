package com.siddesh.smsalerts

import android.app.Application
import kotlinx.coroutines.launch
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeAlertApp : Application() {
    companion object {
        @Volatile var isVisible: Boolean = false
        @Volatile var instance: SafeAlertApp? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupUncaughtExceptionHandler()
        com.siddesh.smsalerts.data.repository.LocalContactsCache.initialize(this)
        Log.d("AUTH_DIAGNOSTIC", "Firebase initialization started")
        try {
            FirebaseApp.initializeApp(this)
            if (BuildConfig.IS_LOCAL_QA) {
                val pId = FirebaseApp.getInstance().options.projectId
                if (pId != "demo-safealerts" && pId != "smselerts") {
                    Log.w("AUTH_DIAGNOSTIC", "QA flavor running with custom project: $pId")
                }
                // Physical QA devices use explicitly configured adb reverse; production never uses these endpoints.
                val emulatorHost = if (android.os.Build.MODEL.startsWith("sdk_gphone") ||
                    android.os.Build.FINGERPRINT.startsWith("generic")) "10.0.2.2" else "127.0.0.1"
                try {
                    FirebaseAuth.getInstance().useEmulator(emulatorHost, 9099)
                    FirebaseFirestore.getInstance().useEmulator(emulatorHost, 8080)
                } catch (e: Exception) {
                    Log.w("AUTH_DIAGNOSTIC", "Emulator attach skipped: ${e.message}")
                }
                com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = false
            }
            try {
                com.google.android.gms.maps.MapsInitializer.initialize(this, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST) { renderer ->
                    Log.i("SafeAlerts/Maps", "Google Maps SDK initialized (Renderer: $renderer)")
                }
            } catch (e: Exception) {
                Log.w("SafeAlerts/Maps", "Maps early initialization skipped: ${e.message}")
            }
            val auth = FirebaseAuth.getInstance()
            com.siddesh.smsalerts.service.GroupSessionManager.start(this)
            auth.addAuthStateListener {
                if (it.currentUser != null) {
                    com.siddesh.smsalerts.service.TrustedCircleManager.getInstance(this@SafeAlertApp)
                    com.siddesh.smsalerts.service.EmergencyMessagingService.registerCurrentDevice()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                        try { com.siddesh.smsalerts.service.EmergencyRecovery.restore(this@SafeAlertApp) }
                        catch (_: Exception) { Log.w("SafeAlerts/Recovery", "Recovery could not finish") }
                    }
                }
            }
            Log.d("AUTH_DIAGNOSTIC", "FirebaseAuth instance created")
            Log.d("AUTH_DIAGNOSTIC", "Authentication initialized")
        } catch (e: Exception) {
            Log.e("AUTH_DIAGNOSTIC", "Firebase initialization error: ${e.message}", e)
        }

    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("SafeAlerts/Crash", "UNCAUGHT_EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
                val crashFile = File(filesDir, "safealerts_last_crash.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val crashLog = buildString {
                    appendLine("SafeAlerts Crash Report")
                    appendLine("Timestamp: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.name}")
                    appendLine("Exception messages omitted to protect personal data.")
                    appendLine("StackTrace:")
                    appendLine(Log.getStackTraceString(throwable))
                }
                FileOutputStream(crashFile).use { it.write(crashLog.toByteArray()) }
            } catch (e: Exception) {
                Log.e("SafeAlerts/Crash", "Failed to write crash log: ${e.message}")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
