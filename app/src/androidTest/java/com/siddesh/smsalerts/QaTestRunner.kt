package com.siddesh.smsalerts

import androidx.test.runner.AndroidJUnitRunner

/** The entire .qa APK uses local Firebase even when Android starts it without instrumentation. */
class QaTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        check(targetContext.packageName == "com.siddesh.smsalerts.qa") { "Only the isolated QA package may run these tests" }
        check(com.google.firebase.FirebaseApp.getInstance().options.projectId == "demo-safealerts")
        uiAutomation.executeShellCommand("appops set ${targetContext.packageName} SEND_SMS deny").use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() }
        }
        super.onStart()
    }
}
