package com.example.plsworkver3.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {
    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            // For Android 6.0 (API 23) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                // Check if network has internet capability
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                
                // Check if network is validated (has actual internet access)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                // Check if it's WiFi or Ethernet (usually more reliable)
                val isWifiOrEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                
                // Return true if has internet AND (validated OR WiFi/Ethernet)
                // This is less strict - allows WiFi even if not validated yet
                // This prevents false negatives when WiFi is connected but validation is pending
                hasInternet && (isValidated || isWifiOrEthernet)
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            // If check fails, assume network is available and let the API call handle it
            // This prevents false negatives
            println("⚠️ Network check exception: ${e.message}")
            true
        }
    }
}


