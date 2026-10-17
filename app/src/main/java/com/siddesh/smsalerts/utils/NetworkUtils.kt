package com.siddesh.smsalerts.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

data class NetworkStatusInfo(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val carrierName: String,
    val simStateDescription: String,
    val networkDescription: String
)

object NetworkUtils {

    fun getNetworkStatus(context: Context): NetworkStatusInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        var isConnected = false
        var isWifi = false
        var isCellular = false

        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        }

        val carrierName = telephonyManager?.networkOperatorName?.ifBlank { "Mobile Cellular" } ?: "No Carrier"

        val simDesc = when (telephonyManager?.simState) {
            TelephonyManager.SIM_STATE_READY -> "SIM Ready ($carrierName)"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM Inserted"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM PUK Required"
            else -> if (isCellular) "Cellular Active" else "No Cellular SIM"
        }

        val networkDesc = when {
            isWifi -> "Wi-Fi Connected"
            isCellular -> "Cellular Data Active ($carrierName)"
            isConnected -> "Online (Other Interface)"
            else -> "Offline (SMS operates via GSM cellular)"
        }

        return NetworkStatusInfo(
            isConnected = isConnected,
            isWifi = isWifi,
            isCellular = isCellular,
            carrierName = carrierName,
            simStateDescription = simDesc,
            networkDescription = networkDesc
        )
    }
}
