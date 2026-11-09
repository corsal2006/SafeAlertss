package com.siddesh.smsalerts.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.view.MotionEvent
import android.webkit.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.siddesh.smsalerts.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

data class MapPoint(val latitude: Double, val longitude: Double)
data class FreeMapMarker(
    val point: MapPoint,
    val label: String,
    val detail: String = "",
    val incidentId: String = "",
    val danger: Boolean = false,
    val isOrigin: Boolean = false,
    val isDest: Boolean = false
)

/** Only bundled scripts run. Tiles are HTTPS images, cached by WebView using HTTP cache headers. */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun FreeMap(
    markers: List<FreeMapMarker>,
    modifier: Modifier = Modifier,
    route: List<MapPoint> = emptyList(),
    routeSummary: String = "",
    onPick: ((MapPoint) -> Unit)? = null,
    onIncident: (String) -> Unit = {},
    allowNetworkTiles: Boolean = !BuildConfig.IS_LOCAL_QA
) {
    val pickCallback by rememberUpdatedState(onPick)
    val incidentCallback by rememberUpdatedState(onIncident)
    var failed by remember { mutableStateOf(false) }

    val payload = JSONObject().apply {
        put("tiles", allowNetworkTiles)
        put("pick", onPick != null)
        put("routeSummary", routeSummary)

        // Route polyline coordinates
        val routeArr = JSONArray()
        route.filter { it.latitude.isFinite() && it.longitude.isFinite() }.forEach {
            routeArr.put(JSONArray().put(it.latitude).put(it.longitude))
        }
        put("route", routeArr)

        // Markers
        val markersArr = JSONArray()
        markers.filter {
            it.point.latitude.isFinite() && it.point.longitude.isFinite() &&
                it.point.latitude in -90.0..90.0 && it.point.longitude in -180.0..180.0
        }.forEach {
            markersArr.put(
                JSONObject().apply {
                    put("lat", it.point.latitude)
                    put("lon", it.point.longitude)
                    put("label", it.label)
                    put("detail", it.detail)
                    put("incident", it.incidentId)
                    put("danger", it.danger)
                    put("isOrigin", it.isOrigin)
                    put("isDest", it.isDest)
                }
            )
        }
        put("markers", markersArr)
    }.toString()

    val currentPayload by rememberUpdatedState(payload)

    Column {
        if (failed) {
            Text("Map renderer unavailable. Your timer and saved coordinates remain available.")
        } else {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setGeolocationEnabled(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = "SafeAlerts/1.0 (Android; com.siddesh.smsalerts) ${settings.userAgentString}"

                        // Allow interactive map touch gestures inside scrolling parents
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            false
                        }

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun pick(lat: Double, lon: Double) {
                                if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                                    post { pickCallback?.invoke(MapPoint(lat, lon)) }
                                }
                            }

                            @JavascriptInterface
                            fun incident(id: String) {
                                if (id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) {
                                    post { incidentCallback(id) }
                                }
                            }
                        }, "SafeMap")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url
                                if (url.scheme == "https" && url.host == "appassets.androidplatform.net") {
                                    val name = url.path.orEmpty().removePrefix("/map/")
                                    val mime = when (name) {
                                        "index.html" -> "text/html"
                                        "map.js", "leaflet.js" -> "application/javascript"
                                        "map.css", "leaflet.css" -> "text/css"
                                        else -> return blocked()
                                    }
                                    return WebResourceResponse(mime, "UTF-8", context.assets.open("map/$name"))
                                }
                                if (allowNetworkTiles && url.scheme == "https" && url.host == "tile.openstreetmap.org" &&
                                    url.path.orEmpty().matches(Regex("/[0-9]+/[0-9]+/[0-9]+\\.png"))) {
                                    return null
                                }
                                return blocked()
                            }

                            private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.isForMainFrame && request.hasGesture() && request.url.toString() == "https://www.openstreetmap.org/copyright") {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) { }
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (url == "https://appassets.androidplatform.net/map/index.html") {
                                    view.evaluateJavascript("if(window.render)window.render($currentPayload)", null)
                                }
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                failed = true
                                return true
                            }
                        }

                        loadUrl("https://appassets.androidplatform.net/map/index.html")
                    }
                },
                update = { webView ->
                    webView.evaluateJavascript("if(window.render)window.render($payload)", null)
                },
                onRelease = { webView ->
                    webView.removeJavascriptInterface("SafeMap")
                    webView.stopLoading()
                    webView.destroy()
                }
            )
        }
    }
}
