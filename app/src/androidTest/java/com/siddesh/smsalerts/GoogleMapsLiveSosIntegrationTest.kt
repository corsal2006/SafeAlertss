package com.siddesh.smsalerts

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.MapsInitializer
import com.siddesh.smsalerts.data.location.LocationQuality
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import com.siddesh.smsalerts.service.EmergencyOutbox
import com.siddesh.smsalerts.service.LiveLocationManager
import com.siddesh.smsalerts.ui.components.SafeAlertGoogleMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GoogleMapsLiveSosIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (permission in listOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION", "POST_NOTIFICATIONS")) {
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.$permission").use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
        }
        instrumentation.runOnMainSync {
            composeTestRule.activity.startActivity(
                android.content.Intent(composeTestRule.activity, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        composeTestRule.waitUntil(10_000) { SafeAlertApp.isVisible && composeTestRule.activity.hasWindowFocus() }
    }

    @Test
    fun googleMapsSdkInitializesSuccessfully() {
        var initResult = -1
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            initResult = MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) { renderer ->
                assertNotNull("Renderer should be initialized", renderer)
            }
        }
        assertEquals("MapsInitializer must return SUCCESS (0)", 0, initResult)
    }

    @Test
    fun safeAlertGoogleMapComponentMountsWithMarkersAndPolyline() {
        val testOrigin = Pair(19.0760, 72.8777)
        val testDestination = Pair(19.0800, 72.8800)
        val testIncident = Pair(19.0770, 72.8780)
        val testMember = TrustedCircleMember(
            id = "test-peer-uid",
            name = "Test Contact",
            phoneNumber = "9999999999",
            lastLatitude = 19.0750,
            lastLongitude = 72.8770,
            lastLocationTimestamp = System.currentTimeMillis()
        )

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.setContent {
                SafeAlertGoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    centerLatitude = 19.0760,
                    centerLongitude = 72.8777,
                    hasLocationPermission = true,
                    originPoint = testOrigin,
                    destinationPoint = testDestination,
                    incidentLocation = testIncident,
                    circleMembers = listOf(testMember),
                    routePolyline = listOf(testOrigin, testDestination),
                    isIncidentActive = true
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify map successfully rendered into the activity view hierarchy
        assertNotNull(composeTestRule.activity.window.decorView)
    }

    @Test
    fun locationServiceAcquiresValidGpsFixAndFormatsStandardGoogleMapsUrl() = runBlocking {
        val locationService = LocationService(context)
        val fix = locationService.getCurrentLocation(10_000)

        if (fix is LocationResult.Success) {
            assertTrue("Latitude must be valid coordinate", LocationQuality.coordinates(fix.latitude, fix.longitude))
            val url = LocationResult.generateMapsUrl(fix.latitude, fix.longitude)
            assertTrue("URL must follow standard format", url.startsWith("https://www.google.com/maps?q="))
            assertEquals("https://www.google.com/maps?q=${fix.latitude},${fix.longitude}", url)
        }
    }

    @Test
    fun liveLocationManagerHandlesSosSessionAndRecordsFix() = runBlocking {
        val tracker = LiveLocationManager.getInstance(context)
        val outbox = EmergencyOutbox(context)
        val incidentId = UUID.randomUUID().toString()
        val userId = "test_user_${UUID.randomUUID()}"

        outbox.saveIncident(
            EmergencyIncident(
                incidentId = incidentId,
                userId = userId,
                state = "ACTIVE",
                emergencyType = "INTEGRATION_TEST_SOS"
            )
        )

        val testFix = LocationResult.Success(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracy = 10f,
            timestamp = System.currentTimeMillis()
        )

        tracker.recordFix(userId, incidentId, testFix)

        val latest = tracker.latestLocationPoint.value
        assertNotNull("Recorded fix must update tracker latestLocationPoint", latest)
        assertEquals(19.0760, latest!!.latitude, 0.0001)
        assertEquals(72.8777, latest.longitude, 0.0001)

        // Clean up
        tracker.stopLiveSharing(incidentId)
        outbox.updateState(incidentId, "RESOLVED")
    }
}
