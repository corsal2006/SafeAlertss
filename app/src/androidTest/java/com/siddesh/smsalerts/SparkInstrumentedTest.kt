package com.siddesh.smsalerts

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.siddesh.smsalerts.data.repository.SparkCircleRepository
import com.siddesh.smsalerts.ui.components.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Synthetic local accounts only. QA never loads public map tiles or sends carrier SMS. */
@RunWith(AndroidJUnit4::class)
class SparkInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun foregroundFixture() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            compose.activity.startActivity(android.content.Intent(compose.activity, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        compose.waitUntil(5_000) { SafeAlertApp.isVisible && compose.activity.hasWindowFocus() }
    }
    @Test fun twoLocalAccountsRedeemAcceptAndRevokeUsingAndroidTransactions() = runBlocking {
        Assert.assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val password = "Synthetic-Test-1234!"
        auth.signOut()
        val a = auth.createUserWithEmailAndPassword("spark-a-${java.util.UUID.randomUUID()}@example.invalid", password).await().user!!.uid
        val repo = SparkCircleRepository()
        val code = repo.manage("generate")
        Assert.assertEquals(8, code.code.length)
        Assert.assertEquals(code, repo.manage("get"))
        auth.signOut()
        val b = auth.createUserWithEmailAndPassword("spark-b-${java.util.UUID.randomUUID()}@example.invalid", password).await().user!!.uid
        repo.join(code.code)
        val pending = db.collection("circle_relationships").whereArrayContains("participants", b).get(Source.SERVER).await().documents.single()
        Assert.assertEquals(a, pending.getString("senderUid"))
        Assert.assertEquals("PENDING", pending.getString("status"))
        Assert.assertFalse(db.document("users/$a/circle_members/$b").get(Source.SERVER).await().exists())
        repeat(2) { repo.respond(pending.id, "ACCEPTED") }
        Assert.assertEquals("ACCEPTED", pending.reference.get(Source.SERVER).await().getString("status"))
        repeat(2) { repo.respond(pending.id, "REVOKED") }
        Assert.assertEquals("REVOKED", pending.reference.get(Source.SERVER).await().getString("status"))
    }
    @Test fun authorizedOnlineCircleIncidentPostsOneLocalNotification() = runBlocking {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Assert.assertEquals("demo-safealerts", com.google.firebase.FirebaseApp.getInstance().options.projectId)
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        ).use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val password = "Synthetic-Test-1234!"
        val emailA = "notify-a-${java.util.UUID.randomUUID()}@example.invalid"
        val emailB = "notify-b-${java.util.UUID.randomUUID()}@example.invalid"
        auth.signOut()
        val a = auth.createUserWithEmailAndPassword(emailA, password).await().user!!.uid
        val repo = SparkCircleRepository()
        val code = repo.manage("generate")
        auth.signOut()
        val b = auth.createUserWithEmailAndPassword(emailB, password).await().user!!.uid
        repo.join(code.code)
        val relation = db.collection("circle_relationships").whereArrayContains("participants", b).get(Source.SERVER).await().documents.single()
        repo.respond(relation.id, "ACCEPTED")
        auth.signInWithEmailAndPassword(emailA, password).await()
        db.document("users/$a/circle_members/$b").set(mapOf("relationId" to relation.id,
            "relationshipCreatedAt" to relation.getTimestamp("createdAt")!!, "shareEnabled" to true,
            "expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + 3600_000)))).await()
        val id = "synthetic-notification-${java.util.UUID.randomUUID()}"
        db.document("incidents/$id").set(mapOf("ownerUid" to a, "senderName" to "Synthetic QA",
            "senderPhone" to "", "policeNumber" to "", "helplineNumber" to "", "category" to "LOCAL TEST",
            "status" to "ACTIVE", "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
        auth.signInWithEmailAndPassword(emailB, password).await()
        val seen = context.getSharedPreferences("circle_notified", 0)
        try {
            withTimeout(25_000) { while (!seen.contains("$b:$id")) delay(200) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val manager = com.siddesh.smsalerts.service.TrustedCircleManager.getInstance(context)
            throw AssertionError("Notification not posted: status=${manager.status.value}; members=${manager.members.value}", e)
        }
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        Assert.assertEquals(1, nm.activeNotifications.count { it.id == id.hashCode() })
        val first = seen.getLong("$b:$id", 0)
        com.siddesh.smsalerts.service.CircleNotificationWorker.enqueue(context, b, id)
        delay(500)
        Assert.assertEquals(first, seen.getLong("$b:$id", 0))
        repo.respond(relation.id, "REVOKED")
        nm.cancel(id.hashCode())
    }
    @Test fun freeMapRendersRealPointsEscapesLabelsAndSelectsDestinationLocally() {
        val picked = AtomicReference<MapPoint?>(null)
        compose.runOnUiThread { compose.activity.setContent {
            FreeMap(listOf(FreeMapMarker(MapPoint(12.0, 77.0), "<img onerror=alert(1)>", "Synthetic test"),
                FreeMapMarker(MapPoint(13.0, 78.0), "Test B", incidentId = "synthetic-incident", danger = true)),
                Modifier.fillMaxSize(), onPick = { picked.set(it) })
        } }
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val result = AtomicReference("")
        compose.waitUntil(15_000) {
            compose.activity.runOnUiThread { find(compose.activity.window.decorView)?.evaluateJavascript(
                "typeof layer==='undefined'?'loading':JSON.stringify({count:layer.getLayers().length,tiles:!!tiles,label:layer.getLayers()[0]?.getPopup().getContent().textContent})"
            ) { result.set(it) } }
            result.get().contains("\\\"count\\\":2")
        }
        Assert.assertTrue(result.get().contains("\\\"tiles\\\":false"))
        val rendered = org.json.JSONObject(org.json.JSONArray("[${result.get()}]").getString(0))
        Assert.assertTrue(rendered.getString("label").contains("<img onerror=alert(1)>"))
        compose.activity.runOnUiThread { find(compose.activity.window.decorView)!!.evaluateJavascript(
            "map.fire('contextmenu',{latlng:L.latLng(12.97,77.59)})", null) }
        compose.waitUntil(5000) { picked.get() != null }
        Assert.assertEquals(MapPoint(12.97, 77.59), picked.get())
    }
    @Test fun freeMapLoadsPublicBasemapTilesOnPhone() {
        compose.runOnUiThread { compose.activity.setContent {
            FreeMap(emptyList(), Modifier.fillMaxSize(), allowNetworkTiles = true)
        } }
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val count = AtomicReference("0")
        compose.waitUntil(30_000) {
            compose.activity.runOnUiThread { find(compose.activity.window.decorView)?.evaluateJavascript(
                "tiles && !tiles.isLoading() ? document.querySelectorAll('img.leaflet-tile-loaded').length : 0"
            ) { count.set(it) } }
            (count.get().toIntOrNull() ?: 0) > 0
        }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val diagnostics = AtomicReference("")
        compose.runOnUiThread {
            find(compose.activity.window.decorView)!!.evaluateJavascript("""
                JSON.stringify({viewport:[innerWidth,innerHeight],size:map.getSize(),
                  elements:['#map','.leaflet-map-pane','.leaflet-tile-pane','.leaflet-tile','.leaflet-control-zoom'].map(s=>{
                    const e=document.querySelector(s), c=e&&getComputedStyle(e),r=e&&e.getBoundingClientRect();
                    return {selector:s,rect:r&&[r.x,r.y,r.width,r.height],display:c&&c.display,visibility:c&&c.visibility,
                      opacity:c&&c.opacity,transform:c&&c.transform,z:c&&c.zIndex,complete:e&&e.complete,naturalWidth:e&&e.naturalWidth};})})
            """.trimIndent()) { diagnostics.set(it) }
        }
        compose.waitUntil(5_000) { diagnostics.get().isNotEmpty() }
        java.io.File(instrumentation.targetContext.filesDir, "qa-map-diagnostics.json").writeText(
            org.json.JSONArray("[${diagnostics.get()}]").getString(0))
        compose.runOnUiThread {
            find(compose.activity.window.decorView)!!.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.set(true) }
            })
        }
        compose.waitUntil(5_000) { ready.get() }
        compose.waitForIdle()
        android.os.SystemClock.sleep(300) // Allow the compositor to present the acknowledged WebView frame.
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.filesDir, "qa-free-basemap.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        var visiblePixels = 0
        for (y in 100 until screenshot.height - 100 step 20) for (x in 20 until screenshot.width - 20 step 20) {
            val pixel = screenshot.getPixel(x, y)
            if (android.graphics.Color.red(pixel) > 80 && android.graphics.Color.green(pixel) > 80) visiblePixels++
        }
        screenshot.recycle()
        Assert.assertTrue("Loaded tiles must also appear in the displayed frame", visiblePixels > 500)
    }
}
