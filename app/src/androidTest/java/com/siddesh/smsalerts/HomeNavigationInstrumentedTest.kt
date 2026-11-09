package com.siddesh.smsalerts

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationInstrumentedTest {
    companion object {
        @JvmStatic @BeforeClass fun seedLocalAccount(): Unit = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            Assert.assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
            val uid = auth.createUserWithEmailAndPassword("ui-${java.util.UUID.randomUUID()}@example.invalid", "Synthetic-Test-1234!").await().user!!.uid
            FirebaseFirestore.getInstance().collection("users").document(uid).set(mapOf("uid" to uid, "fullName" to "Synthetic UI", "dateOfBirth" to "2000-01-01")).await()
            UserPreferencesDataStore(context).apply {
                updateDirectSms(false); updateBluetoothMeshEnabled(false); updateLocationSharing(false)
                updateVoiceAlert(false); updateShakeToAlert(false)
                updateRequireHold(false); updateCountdownDuration(10)
                cancelOrCompleteSafetyCheckIn(); cancelOrCompleteWomensSafetyJourney()
            }
        }
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun awaitHome() {
        compose.waitUntil(20_000) {
            try { compose.onAllNodesWithContentDescription("SafeAlerts Logo").fetchSemanticsNodes().isNotEmpty() }
            catch (e: IllegalStateException) {
                // During Activity recreation, the old Compose root is gone before the new one mounts.
                if (e.message?.startsWith("No compose hierarchies found") == true) false else throw e
            }
        }
        compose.waitForIdle()
        if (compose.onAllNodesWithText("CONTINUE").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("CONTINUE").performClick()
    }
    @Test fun repeatedLogoNavigationAndActivityRecreation() {
        awaitHome()
        repeat(12) {
            compose.onNodeWithContentDescription("SafeAlerts Logo").performClick()
            compose.onNodeWithText("About SafeAlerts").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            awaitHome()
        }
        compose.activityRule.scenario.recreate()
        awaitHome()
        compose.onNodeWithContentDescription("SafeAlerts Logo").performClick()
        compose.onNodeWithText("About SafeAlerts").assertIsDisplayed()
    }
    @Test fun profileAndHomeRemainUsableAtLargeFont() {
        fun shell(command: String) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
        }
        val originalScale = android.provider.Settings.System.getFloat(
            compose.activity.contentResolver, android.provider.Settings.System.FONT_SCALE, 1f)
        try {
            shell("settings put system font_scale 2.0")
            compose.activityRule.scenario.recreate()
            awaitHome()
            compose.onNodeWithContentDescription("Open profile").performClick()
            compose.onNodeWithText("User Profile").assertIsDisplayed()
            compose.onNodeWithText("Full Name").performScrollTo().assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.filesDir, "qa-profile-large.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            compose.onNodeWithText("Verify email (free)").performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("Phone not verified").performScrollTo().assertIsDisplayed()
            val details = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.filesDir, "qa-profile-details-large.png").outputStream().use {
                details.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            details.recycle()
        } finally { shell("settings put system font_scale $originalScale") }
    }

    @Test fun customJourneyRejectsZeroAndAcceptsChosenDurationWithKeyboardVisible() {
        awaitHome()
        compose.onNodeWithText("Women's Safety Journey").performScrollTo().performClick()
        compose.onNodeWithText("Destination label or latitude,longitude").performScrollTo().performTextInput("Synthetic destination")
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Duration in minutes").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Start Safety Journey").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Duration in minutes").performScrollTo().performTextReplacement("45")
        compose.onNodeWithText("Start Safety Journey").performScrollTo().assertIsEnabled()
    }

    @Test fun compactJoinEntryRejectsIncompleteCodeWithKeyboardVisible() {
        awaitHome()
        compose.onNodeWithText("Trusted Circle & Live Map").performScrollTo().performClick()
        compose.onNodeWithText("Add Member").performClick()
        compose.onNodeWithText("Enter Code", useUnmergedTree = true).performClick()
        compose.onNodeWithText("8-Character Code").performTextInput(" ab23 ")
        compose.onNodeWithText("Connect with Code").assertIsNotEnabled()
        compose.onNodeWithText("8-Character Code").performTextReplacement("ab23cd45")
        compose.onNodeWithText("Connect with Code").assertIsEnabled()
        // No real invite is sent and no account relationship is created by this layout test.
    }

    private fun startSosFromHome() {
        awaitHome()
        compose.onNodeWithContentDescription("Emergency SOS Button. Press and hold for two seconds to initiate emergency alert.")
            .performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("Continue with available channels").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("CANCEL EMERGENCY").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun cancelSosThenDispatchAndBackCannotPopPastHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = com.siddesh.smsalerts.service.EmergencyManager.getInstance(context)
        repeat(3) {
            startSosFromHome()
            val id = manager.currentIncidentId!!
            compose.onNodeWithText("CANCEL EMERGENCY").performClick()
            awaitHome()
            Assert.assertEquals("CANCELLED", com.siddesh.smsalerts.service.EmergencyOutbox(context).getIncident(id)?.state)
            // Verify Home is interactive after the terminal-state navigation callback.
            compose.onNodeWithContentDescription("SafeAlerts Logo").performClick()
            compose.onNodeWithText("About SafeAlerts").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
        }
        startSosFromHome()
        val active = manager.currentIncidentId!!
        try {
            compose.onNodeWithText("CONFIRM & DISPATCH NOW").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Emergency active").fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Emergency active").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Back").performClick()
            awaitHome()
            compose.onNodeWithContentDescription("SafeAlerts Logo").performClick()
            compose.onNodeWithText("About SafeAlerts").assertIsDisplayed()
        } finally { manager.resolveEmergency(active) }
    }
}
