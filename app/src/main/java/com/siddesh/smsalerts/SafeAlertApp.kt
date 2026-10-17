package com.siddesh.smsalerts

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeAlertApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()

        com.siddesh.smsalerts.data.repository.LocalContactsCache.initialize(this)
        Log.d("AUTH_DIAGNOSTIC", "Firebase initialization started")
        try {
            FirebaseApp.initializeApp(this)
            val settings = FirebaseFirestoreSettings.Builder().build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            val auth = FirebaseAuth.getInstance()
            Log.d("AUTH_DIAGNOSTIC", "FirebaseAuth instance created")
            Log.d("AUTH_DIAGNOSTIC", "Current user = ${auth.currentUser?.uid ?: "none"}")
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
                    appendLine("Message: ${throwable.message}")
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
