package com.siddesh.smsalerts.data.location

import com.siddesh.smsalerts.utils.PrivacyLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

data class GeocodedLocation(val displayName: String, val latitude: Double, val longitude: Double)
data class RouteResult(val points: List<Pair<Double, Double>>, val distanceMeters: Double, val durationSeconds: Double, val summary: String)

/**
 * Routing and distance calculation service for Women's Safety Journey.
 * Leverages OSRM driving route service with resilient offline direct-line estimation.
 */
object OsmRoutingService {

    private const val TAG = "SafeAlerts/Routing"

    suspend fun searchDestination(query: String): List<GeocodedLocation> =
        GooglePlacesSearchService.searchDestinations(query)

    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (!LocationQuality.coordinates(startLat, startLon) || !LocationQuality.coordinates(destLat, destLon)) {
            return@withContext null
        }

        // 1. Try real road network route via OSRM
        try {
            val urlStr = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                startLon, startLat, destLon, destLat
            )
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "SafeAlerts-Android/1.0.1 (com.siddesh.smsalerts)")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val code = json.optString("code")
                if (code.equals("Ok", ignoreCase = true)) {
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val distanceMeters = route.optDouble("distance", 0.0)
                        val durationSeconds = route.optDouble("duration", 0.0)
                        val geometry = route.optJSONObject("geometry")
                        val coordinates = geometry?.optJSONArray("coordinates")

                        val points = mutableListOf<Pair<Double, Double>>()
                        if (coordinates != null) {
                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                val lon = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                points.add(Pair(lat, lon))
                            }
                        }

                        if (points.isNotEmpty()) {
                            val summary = formatSummary(distanceMeters, durationSeconds, isRoad = true)
                            return@withContext RouteResult(
                                points = points,
                                distanceMeters = distanceMeters,
                                durationSeconds = durationSeconds,
                                summary = summary
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OSRM online route calculation unavailable: ${e.message}")
        }

        // 2. Offline / Direct fallback calculation
        calculateDirectRoute(startLat, startLon, destLat, destLon)
    }

    /**
     * Computes direct geodesic line and estimated road travel duration as an offline fallback.
     */
    private fun calculateDirectRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ): RouteResult {
        val distanceMeters = haversineDistanceMeters(startLat, startLon, destLat, destLon)
        // Assume urban driving speed ~25 km/h with 1.3 road curvature factor
        val roadFactor = 1.3
        val estimatedRoadDistance = distanceMeters * roadFactor
        val speedMps = 25.0 * 1000.0 / 3600.0
        val durationSeconds = estimatedRoadDistance / speedMps

        // Generate interpolated points for polyline
        val steps = 10
        val points = mutableListOf<Pair<Double, Double>>()
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val lat = startLat + (destLat - startLat) * fraction
            val lon = startLon + (destLon - startLon) * fraction
            points.add(Pair(lat, lon))
        }

        val summary = formatSummary(estimatedRoadDistance, durationSeconds, isRoad = false)
        return RouteResult(
            points = points,
            distanceMeters = estimatedRoadDistance,
            durationSeconds = durationSeconds,
            summary = summary
        )
    }

    private fun formatSummary(distanceMeters: Double, durationSeconds: Double, isRoad: Boolean): String {
        val km = distanceMeters / 1000.0
        val totalMins = max(1, (durationSeconds / 60.0).roundToInt())
        val hours = totalMins / 60
        val mins = totalMins % 60
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "$mins mins"
        val distStr = String.format(Locale.US, "%.1f km", km)
        return if (isRoad) "$distStr · ~$timeStr" else "$distStr · ~$timeStr (Est.)"
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
