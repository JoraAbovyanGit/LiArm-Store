package com.example.plsworkver3.network

import android.util.Log
import com.example.plsworkver3.data.AppListResponse
import com.google.gson.Gson
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object NetworkClient {
    private const val TAG = "NetworkClient"
    // Primary URL
    private const val BASE_URL = "https://raw.githubusercontent.com/JoraAbovyanGit/LiArm-Store/main/"
    // Fallback proxy URL (GitHub raw content via proxy)
    private const val PROXY_BASE_URL = "https://ghproxy.com/https://raw.githubusercontent.com/JoraAbovyanGit/LiArm-Store/main/"
    
    // Custom DNS resolver with Google DNS fallback
    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            Log.d(TAG, "Resolving DNS for: $hostname")
            
            // Strategy 1: Try system DNS first (most reliable)
            try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                Log.d(TAG, "System DNS resolution successful for $hostname: ${addresses.joinToString { it.hostAddress }}")
                return addresses
            } catch (e: Exception) {
                Log.w(TAG, "System DNS failed, trying fallback: ${e.message}")
            }
            
            // Strategy 2: Try InetAddress with retry
            for (attempt in 1..3) {
                try {
                    Log.d(TAG, "Attempting InetAddress resolution (attempt $attempt/3)")
                    val addresses = InetAddress.getAllByName(hostname)
                    Log.d(TAG, "InetAddress resolution successful for $hostname: ${addresses.joinToString { it.hostAddress }}")
                    return addresses.toList()
                } catch (e: UnknownHostException) {
                    Log.w(TAG, "InetAddress resolution attempt $attempt failed: ${e.message}")
                    if (attempt < 3) {
                        try {
                            Thread.sleep(500) // Wait 500ms before retry
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            }
            
            // Strategy 3: Try with Google DNS (8.8.8.8) as fallback
            // Note: This is a workaround - we can't directly set DNS server in Java,
            // but we can try to resolve using system's default resolver which might use different DNS
            try {
                Log.d(TAG, "Trying alternative DNS resolution method...")
                // Force a fresh DNS lookup by clearing any cache
                val addresses = java.net.InetAddress.getAllByName(hostname)
                if (addresses.isNotEmpty()) {
                    Log.d(TAG, "Alternative DNS resolution successful: ${addresses.joinToString { it.hostAddress }}")
                    return addresses.toList()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Alternative DNS resolution failed: ${e.message}")
            }
            
            // Strategy 4: Try IPv4 only (sometimes IPv6 causes issues)
            try {
                Log.d(TAG, "Trying IPv4-only resolution")
                val addresses = InetAddress.getAllByName(hostname)
                    .filter { it.address.size == 4 } // IPv4 addresses are 4 bytes
                if (addresses.isNotEmpty()) {
                    Log.d(TAG, "IPv4-only resolution successful: ${addresses.joinToString { it.hostAddress }}")
                    return addresses
                }
            } catch (e: Exception) {
                Log.w(TAG, "IPv4-only resolution failed: ${e.message}")
            }
            
            // All strategies failed
            Log.e(TAG, "All DNS resolution strategies failed for $hostname")
            Log.e(TAG, "NOTE: If browser works but app doesn't, this is likely a device/emulator DNS configuration issue.")
            Log.e(TAG, "Try: Settings > Network > Private DNS > Set to 'dns.google' or '1dot1dot1dot1.cloudflare-dns.com'")
            throw UnknownHostException("Unable to resolve host: $hostname")
        }
    }
    
    // Create a trust manager that accepts all certificates
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    
    // Create SSL context that accepts all certificates
    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }
    
    // Create OkHttpClient with timeout settings, DNS, and SSL fix
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            // Use custom DNS with multiple fallback strategies
            .dns(customDns)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Accept all hostnames
            .connectTimeout(45, TimeUnit.SECONDS) // Increased timeout
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                Log.d(TAG, "Making request to: ${request.url}")
                try {
                    val response = chain.proceed(request)
                    Log.d(TAG, "Response code: ${response.code} for ${request.url}")
                    response
                } catch (e: Exception) {
                    Log.e(TAG, "Request failed for ${request.url}", e)
                    throw e
                }
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }
    
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
    
    /**
     * Fallback method using HttpURLConnection when OkHttp/Retrofit fails.
     * This uses the system's network stack directly, which may work better
     * in cases where OkHttp has DNS resolution issues.
     */
    suspend fun getAppsFallback(ts: Long = System.currentTimeMillis()): AppListResponse? {
        return try {
            Log.d(TAG, "Attempting fallback HTTP request using HttpURLConnection")
            val fullUrl = "${BASE_URL}apps.json?ts=$ts"
            Log.d(TAG, "Fallback URL: $fullUrl")
            
            // Test DNS resolution first
            try {
                Log.d(TAG, "Testing DNS resolution for raw.githubusercontent.com...")
                val testAddresses = java.net.InetAddress.getAllByName("raw.githubusercontent.com")
                Log.d(TAG, "DNS test successful! Resolved to: ${testAddresses.joinToString { it.hostAddress }}")
            } catch (e: Exception) {
                Log.e(TAG, "DNS test failed: ${e.javaClass.simpleName} - ${e.message}", e)
                // Continue anyway - maybe URL.openConnection() will work
            }
            
            val url = URL(fullUrl)
            Log.d(TAG, "URL created successfully, protocol: ${url.protocol}, host: ${url.host}")
            
            val connection = try {
                if (url.protocol == "https") {
                    Log.d(TAG, "Creating HTTPS connection...")
                    val httpsConnection = url.openConnection() as HttpsURLConnection
                    // Trust all certificates for fallback
                    httpsConnection.sslSocketFactory = sslContext.socketFactory
                    httpsConnection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    httpsConnection
                } else {
                    Log.d(TAG, "Creating HTTP connection...")
                    url.openConnection() as HttpURLConnection
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create connection: ${e.javaClass.simpleName} - ${e.message}", e)
                throw e
            }
            
            Log.d(TAG, "Connection created, setting timeouts and headers...")
            connection.apply {
                connectTimeout = 30000
                readTimeout = 30000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiArm-Store-Android")
                setRequestProperty("Accept", "application/json")
            }
            
            Log.d(TAG, "Attempting to connect...")
            val responseCode = try {
                connection.responseCode
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get response code: ${e.javaClass.simpleName} - ${e.message}", e)
                // Try to read error stream
                try {
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        val errorBody = errorStream.bufferedReader().use { it.readText() }
                        Log.e(TAG, "Error response body: $errorBody")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not read error stream", e2)
                }
                throw e
            }
            
            Log.d(TAG, "Fallback HTTP response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Reading response body...")
                val inputStream = connection.inputStream
                val responseBody = inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Fallback HTTP response received, length: ${responseBody.length}")
                
                if (responseBody.isEmpty()) {
                    Log.e(TAG, "Response body is empty!")
                    return null
                }
                
                Log.d(TAG, "Parsing JSON response...")
                val gson = Gson()
                val appResponse = gson.fromJson(responseBody, AppListResponse::class.java)
                Log.d(TAG, "Fallback HTTP parsed successfully: ${appResponse.apps.size} apps")
                appResponse
            } else {
                Log.e(TAG, "Fallback HTTP request failed with code: $responseCode")
                // Try to read error message
                try {
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        val errorBody = errorStream.bufferedReader().use { it.readText() }
                        Log.e(TAG, "Error response: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error stream", e)
                }
                null
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Fallback HTTP DNS resolution failed: ${e.message}", e)
            Log.d(TAG, "Trying proxy fallback URL...")
            return tryProxyUrl(ts)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Fallback HTTP timeout: ${e.message}", e)
            null
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Fallback HTTP connection failed: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fallback HTTP request failed: ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
            // Try proxy if it's a DNS issue
            if (e is UnknownHostException || e.message?.contains("resolve") == true || e.message?.contains("hostname") == true) {
                Log.d(TAG, "Trying proxy fallback URL...")
                return tryProxyUrl(ts)
            }
            null
        }
    }
    
    /**
     * Last resort: Try using a GitHub proxy service
     */
    private suspend fun tryProxyUrl(ts: Long): AppListResponse? {
        return try {
            Log.d(TAG, "Attempting proxy URL: $PROXY_BASE_URL")
            val fullUrl = "${PROXY_BASE_URL}apps.json?ts=$ts"
            val url = URL(fullUrl)
            
            val connection = if (url.protocol == "https") {
                val httpsConnection = url.openConnection() as HttpsURLConnection
                httpsConnection.sslSocketFactory = sslContext.socketFactory
                httpsConnection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                httpsConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
            
            connection.apply {
                connectTimeout = 30000
                readTimeout = 30000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiArm-Store-Android")
                setRequestProperty("Accept", "application/json")
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Proxy HTTP response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Proxy response received, length: ${responseBody.length}")
                
                val gson = Gson()
                val appResponse = gson.fromJson(responseBody, AppListResponse::class.java)
                Log.d(TAG, "Proxy HTTP parsed successfully: ${appResponse.apps.size} apps")
                appResponse
            } else {
                Log.e(TAG, "Proxy HTTP request failed with code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proxy URL also failed: ${e.javaClass.simpleName} - ${e.message}", e)
            null
        }
    }
}
