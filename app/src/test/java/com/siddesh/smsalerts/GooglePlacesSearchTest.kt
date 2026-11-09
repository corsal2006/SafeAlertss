package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.location.GooglePlacesSearchService
import com.siddesh.smsalerts.data.location.OsmRoutingService
import com.siddesh.smsalerts.data.location.PlaceAutocompleteSuggestion
import com.siddesh.smsalerts.utils.AppPermissionFeature
import com.siddesh.smsalerts.utils.PermissionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GooglePlacesSearchTest {

    @Test
    fun testEmptyOrShortQueryReturnsEmpty() = runBlocking {
        val emptyResult = GooglePlacesSearchService.autocomplete("")
        assertTrue("Empty query must return empty list", emptyResult.isEmpty())

        val shortResult = GooglePlacesSearchService.autocomplete("a")
        assertTrue("Single-char query must return empty list", shortResult.isEmpty())
    }

    @Test
    fun testPlaceSuggestionDataModel() {
        val suggestion = PlaceAutocompleteSuggestion(
            placeId = "ChIJ12345",
            primaryText = "Mumbai Central",
            secondaryText = "Mumbai, Maharashtra, India",
            fullText = "Mumbai Central, Mumbai, Maharashtra, India"
        )
        assertEquals("ChIJ12345", suggestion.placeId)
        assertEquals("Mumbai Central", suggestion.primaryText)
        assertEquals("Mumbai, Maharashtra, India", suggestion.secondaryText)
        assertEquals("Mumbai Central, Mumbai, Maharashtra, India", suggestion.fullText)
    }

    @Test
    fun testEmptyPlaceIdResolveReturnsNull() = runBlocking {
        val resolved = GooglePlacesSearchService.resolvePlace("")
        assertNull("Blank placeId must resolve to null", resolved)
    }

    @Test
    fun testParseCoordinatesValidAndInvalid() {
        val valid = GooglePlacesSearchService.parseCoordinates("19.0864, 72.8890")
        assertNotNull("Valid lat,lon must parse", valid)
        assertEquals(19.0864, valid!!.latitude, 0.0001)
        assertEquals(72.8890, valid.longitude, 0.0001)

        val invalid = GooglePlacesSearchService.parseCoordinates("not_a_coordinate")
        assertNull("Invalid text must return null", invalid)

        val outOfBounds = GooglePlacesSearchService.parseCoordinates("105.0, 200.0")
        assertNull("Out of bounds latitude must return null", outOfBounds)
    }

    @Test
    fun testRoutingCalculationFallback() = runBlocking {
        // Test route calculation between Phoenix Marketcity (19.08638, 72.88898) and Kamothe (19.01525, 73.09776)
        val route = OsmRoutingService.calculateRoute(19.08638, 72.88898, 19.01525, 73.09776)
        assertNotNull("Route calculation must return a result", route)
        assertTrue("Route must contain waypoints", route!!.points.size >= 2)
        assertTrue("Distance must be greater than 10km", route.distanceMeters > 10_000.0)
        assertTrue("Duration must be greater than 0", route.durationSeconds > 0)
        assertTrue("Summary must contain km", route.summary.contains("km"))
    }

    @Test
    fun testRoutingCalculationInvalidCoordinates() = runBlocking {
        val route = OsmRoutingService.calculateRoute(999.0, 999.0, 0.0, 0.0)
        assertNull("Invalid coordinates must return null", route)
    }

    @Test
    fun testAppPermissionFeaturesConfigured() {
        val features = AppPermissionFeature.values()
        assertTrue("Must include core permission features", features.size >= 7)
        for (feature in features) {
            assertTrue("Title must not be empty", feature.title.isNotBlank())
            assertTrue("Rationale must not be empty", feature.rationale.isNotBlank())
            assertTrue("Limitation must not be empty", feature.limitation.isNotBlank())
            val perms = PermissionManager.getPermissionsForFeature(feature)
            assertNotNull(perms)
        }
    }
}
