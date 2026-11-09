package com.siddesh.smsalerts.data.location

import com.siddesh.smsalerts.BuildConfig
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class PlaceAutocompleteSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String
)

/**
 * Google Places API (New) search service with resilient offline and Nominatim fallback.
 * Resolves destination queries to accurate geographic coordinates with deduplication and caching.
 */
object GooglePlacesSearchService {

    private const val TAG = "SafeAlerts/Places"
    private const val PLACES_AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete"
    private const val PLACES_SEARCH_TEXT_URL = "https://places.googleapis.com/v1/places:searchText"
    private const val PLACES_DETAILS_URL_PREFIX = "https://places.googleapis.com/v1/places/"

    private val searchCache = ConcurrentHashMap<String, List<GeocodedLocation>>()
    private val detailsCache = ConcurrentHashMap<String, GeocodedLocation>()

    /**
     * Searches for destinations matching [query] using Google Places API (New).
     * If user entered explicit coordinates (lat, lon), parses directly.
     * Falls back seamlessly to Nominatim OpenStreetMap if Google API fails or is offline.
     */
    suspend fun searchDestinations(query: String): List<GeocodedLocation> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // 1. Check if user typed coordinates (latitude, longitude)
        parseCoordinates(trimmed)?.let { return@withContext listOf(it) }

        val cacheKey = trimmed.lowercase()
        searchCache[cacheKey]?.let { return@withContext it }

        // 2. Query Google Places API (New) searchText
        val googleResults = searchGooglePlacesText(trimmed)
        if (googleResults.isNotEmpty()) {
            searchCache[cacheKey] = googleResults
            return@withContext googleResults
        }

        // 3. Fallback: Query Nominatim OpenStreetMap
        val osmResults = searchNominatim(trimmed)
        if (osmResults.isNotEmpty()) {
            searchCache[cacheKey] = osmResults
            return@withContext osmResults
        }

        emptyList()
    }

    /**
     * Retrieves autocomplete suggestions for [query] using Google Places Autocomplete API.
     */
    suspend fun autocomplete(query: String): List<PlaceAutocompleteSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        parseCoordinates(trimmed)?.let {
            return@withContext listOf(PlaceAutocompleteSuggestion(trimmed, trimmed, "Coordinates", trimmed))
        }

        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank() || apiKey == "DEFAULT_API_KEY") {
            return@withContext searchDestinations(trimmed).map {
                PlaceAutocompleteSuggestion(
                    placeId = "${it.latitude},${it.longitude}",
                    primaryText = it.displayName.substringBefore(","),
                    secondaryText = it.displayName.substringAfter(",", ""),
                    fullText = it.displayName
                )
            }
        }

        try {
            val url = URL(PLACES_AUTOCOMPLETE_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", apiKey)
            }

            val requestJson = JSONObject().apply {
                put("input", trimmed)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestJson.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val suggestions = json.optJSONArray("suggestions") ?: JSONArray()
                val list = mutableListOf<PlaceAutocompleteSuggestion>()

                for (i in 0 until suggestions.length()) {
                    val item = suggestions.getJSONObject(i)
                    val prediction = item.optJSONObject("placePrediction") ?: continue
                    val placeId = prediction.optString("placeId", "")
                    val textObj = prediction.optJSONObject("text")
                    val fullText = textObj?.optString("text", "") ?: ""
                    val structFormat = prediction.optJSONObject("structuredFormat")
                    val primary = structFormat?.optJSONObject("mainText")?.optString("text", "") ?: fullText.substringBefore(",")
                    val secondary = structFormat?.optJSONObject("secondaryText")?.optString("text", "") ?: fullText.substringAfter(",", "")

                    if (placeId.isNotBlank() && fullText.isNotBlank()) {
                        list.add(PlaceAutocompleteSuggestion(placeId, primary, secondary, fullText))
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Places autocomplete failed: ${e.message}")
        }

        // Fallback to text search suggestions
        searchDestinations(trimmed).map {
            PlaceAutocompleteSuggestion(
                placeId = "${it.latitude},${it.longitude}",
                primaryText = it.displayName.substringBefore(","),
                secondaryText = it.displayName.substringAfter(",", ""),
                fullText = it.displayName
            )
        }
    }

    /**
     * Resolves a placeId to its accurate geographic coordinates (lat, lon).
     */
    suspend fun resolvePlace(placeId: String): GeocodedLocation? = withContext(Dispatchers.IO) {
        val trimmed = placeId.trim()
        parseCoordinates(trimmed)?.let { return@withContext it }

        detailsCache[trimmed]?.let { return@withContext it }

        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank() || apiKey == "DEFAULT_API_KEY") return@withContext null

        try {
            val url = URL("$PLACES_DETAILS_URL_PREFIX$trimmed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", apiKey)
                setRequestProperty("X-Goog-FieldMask", "id,displayName,formattedAddress,location")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val location = json.optJSONObject("location")
                val lat = location?.optDouble("latitude")
                val lon = location?.optDouble("longitude")
                val displayName = json.optJSONObject("displayName")?.optString("text")
                    ?: json.optString("formattedAddress", "Destination")

                if (lat != null && lon != null && LocationQuality.coordinates(lat, lon)) {
                    val resolved = GeocodedLocation(displayName, lat, lon)
                    detailsCache[trimmed] = resolved
                    return@withContext resolved
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Places details resolution failed for $trimmed: ${e.message}")
        }

        null
    }

    /**
     * Executes Google Places API (New) Text Search (`places:searchText`).
     */
    private fun searchGooglePlacesText(query: String): List<GeocodedLocation> {
        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank() || apiKey == "DEFAULT_API_KEY") return emptyList()

        try {
            val url = URL(PLACES_SEARCH_TEXT_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", apiKey)
                setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
            }

            val requestJson = JSONObject().apply {
                put("textQuery", query)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestJson.toString()) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val places = json.optJSONArray("places") ?: JSONArray()
                val list = mutableListOf<GeocodedLocation>()

                for (i in 0 until places.length()) {
                    val item = places.getJSONObject(i)
                    val loc = item.optJSONObject("location") ?: continue
                    val lat = loc.optDouble("latitude", Double.NaN)
                    val lon = loc.optDouble("longitude", Double.NaN)
                    if (lat.isNaN() || lon.isNaN() || !LocationQuality.coordinates(lat, lon)) continue

                    val name = item.optJSONObject("displayName")?.optString("text")
                    val addr = item.optString("formattedAddress")
                    val label = when {
                        !name.isNullOrBlank() && !addr.isNullOrBlank() && !addr.startsWith(name) -> "$name, $addr"
                        !name.isNullOrBlank() -> name
                        !addr.isNullOrBlank() -> addr
                        else -> query
                    }

                    list.add(GeocodedLocation(displayName = label, latitude = lat, longitude = lon))
                }
                return list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Places searchText error: ${e.message}")
        }
        return emptyList()
    }

    /**
     * Fallback geocoding via OpenStreetMap Nominatim.
     */
    private fun searchNominatim(query: String): List<GeocodedLocation> {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&addressdetails=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "SafeAlerts-Android/1.0.1 (com.siddesh.smsalerts)")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()

                val arr = JSONArray(responseText)
                val list = mutableListOf<GeocodedLocation>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    val name = item.optString("display_name", "")
                    if (!lat.isNaN() && !lon.isNaN() && name.isNotBlank() && LocationQuality.coordinates(lat, lon)) {
                        list.add(GeocodedLocation(name, lat, lon))
                    }
                }
                return list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim fallback search error: ${e.message}")
        }
        return emptyList()
    }

    /**
     * Parses explicit coordinates formatted as `latitude, longitude`.
     */
    fun parseCoordinates(query: String): GeocodedLocation? {
        val values = query.trim().split(',').map { it.trim().toDoubleOrNull() }
        if (values.size != 2) return null
        val lat = values[0] ?: return null
        val lon = values[1] ?: return null
        return if (LocationQuality.coordinates(lat, lon)) {
            GeocodedLocation(displayName = "Location (${"%.4f".format(lat)}, ${"%.4f".format(lon)})", latitude = lat, longitude = lon)
        } else null
    }
}
