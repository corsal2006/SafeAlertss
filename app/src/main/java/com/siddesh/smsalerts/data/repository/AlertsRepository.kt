package com.siddesh.smsalerts.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AlertsRepository {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun getAlertsFlow(userId: String): Flow<List<EmergencyAlert>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val collectionRef = firestore.collection("users")
            .document(userId)
            .collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Try fallback to ordering by timestamp for older entries
                val fallbackRef = firestore.collection("users")
                    .document(userId)
                    .collection("alerts")

                fallbackRef.get().addOnSuccessListener { s ->
                    val list = s.documents.mapNotNull { parseAlert(it) }
                        .sortedByDescending { it.createdAt }
                    trySend(list)
                }.addOnFailureListener {
                    trySend(emptyList())
                }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val alerts = snapshot.documents.mapNotNull { parseAlert(it) }
                trySend(alerts)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    private fun parseAlert(doc: com.google.firebase.firestore.DocumentSnapshot): EmergencyAlert? {
        if (!doc.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val recipientPhoneNumbers = (doc.get("recipientPhoneNumbers") as? List<String>)
            ?: (doc.get("recipients") as? List<String>)
            ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val recipientIds = (doc.get("recipientIds") as? List<String>) ?: emptyList()

        val lat = doc.getDouble("latitude")
        val lng = doc.getDouble("longitude")
        val maps = doc.getString("mapsUrl") ?: doc.getString("locationUrl")
        val created = doc.getLong("createdAt") ?: doc.getLong("timestamp") ?: System.currentTimeMillis()
        val sendMeth = doc.getString("sendMethod") ?: doc.getString("deliveryMethod") ?: "Direct SMS"
        val locAvail = doc.getBoolean("locationAvailable")
            ?: doc.getBoolean("locationIncluded")
            ?: (lat != null && lng != null && !maps.isNullOrBlank())
        val triggerSrc = doc.getString("triggerSource") ?: "BUTTON"

        return EmergencyAlert(
            alertId = doc.id,
            uid = doc.getString("uid") ?: "",
            emergencyType = doc.getString("emergencyType") ?: "Other Emergency",
            triggerSource = triggerSrc,
            message = doc.getString("message") ?: "",
            recipientIds = recipientIds,
            recipientPhoneNumbers = recipientPhoneNumbers,
            latitude = lat,
            longitude = lng,
            mapsUrl = maps,
            locationAvailable = locAvail,
            createdAt = created,
            status = doc.getString("status") ?: "SMS Sent",
            sendMethod = sendMeth,
            recipients = recipientPhoneNumbers,
            recipientCount = doc.getLong("recipientCount")?.toInt() ?: recipientPhoneNumbers.size,
            locationIncluded = locAvail,
            deliveryMethod = sendMeth,
            timestamp = created,
            failureReason = doc.getString("failureReason")
        )
    }

    suspend fun saveEmergencyAlert(userId: String, alert: EmergencyAlert): Resource<String> {
        return try {
            val collectionRef = firestore.collection("users")
                .document(userId)
                .collection("alerts")

            val docRef = if (alert.alertId.isNotBlank()) {
                collectionRef.document(alert.alertId)
            } else {
                collectionRef.document()
            }

            val finalAlert = alert.copy(
                alertId = docRef.id,
                uid = userId,
                createdAt = if (alert.createdAt > 0) alert.createdAt else System.currentTimeMillis()
            )
            val alertData = finalAlert.toMap()
            docRef.set(alertData).await()

            // Also mirror to emergencyAlerts for full backwards compatibility
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("emergencyAlerts")
                    .document(docRef.id)
                    .set(alertData)
            } catch (_: Exception) {}

            Resource.Success(docRef.id)
        } catch (e: Exception) {
            // Fallback attempt to emergencyAlerts if alerts collection write is denied
            try {
                val docRef = firestore.collection("users")
                    .document(userId)
                    .collection("emergencyAlerts")
                    .document(if (alert.alertId.isNotBlank()) alert.alertId else firestore.collection("users").document(userId).collection("emergencyAlerts").document().id)

                val finalAlert = alert.copy(
                    alertId = docRef.id,
                    uid = userId,
                    createdAt = if (alert.createdAt > 0) alert.createdAt else System.currentTimeMillis()
                )
                docRef.set(finalAlert.toMap()).await()
                return Resource.Success(docRef.id)
            } catch (_: Exception) {}

            Resource.Error(e.localizedMessage ?: "Failed to log emergency alert to Firestore.", e)
        }
    }

    suspend fun updateAlertStatus(userId: String, alertId: String, newStatus: String): Resource<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("alerts")
                .document(alertId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update alert status.", e)
        }
    }

    suspend fun getAlertById(userId: String, alertId: String): Resource<EmergencyAlert> {
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .collection("alerts")
                .document(alertId)
                .get()
                .await()

            if (doc.exists()) {
                val parsed = parseAlert(doc)
                if (parsed != null) {
                    Resource.Success(parsed)
                } else {
                    Resource.Error("Could not parse emergency alert.")
                }
            } else {
                Resource.Error("Emergency alert record not found.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to retrieve alert details.", e)
        }
    }
}
