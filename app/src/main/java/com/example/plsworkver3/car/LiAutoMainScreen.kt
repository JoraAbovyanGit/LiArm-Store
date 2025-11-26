package com.example.plsworkver3.car

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.plsworkver3.MainActivity
import com.example.plsworkver3.R
import com.example.plsworkver3.data.AppInfo
import com.example.plsworkver3.network.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Template-driven grid that surfaces LiArm Store content inside Li Auto systems.
 */
class LiAutoMainScreen(
    carContext: CarContext
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apps: List<AppInfo> = emptyList()
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                Log.d(TAG, "Screen onCreate - starting app load")
                loadApps()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                Log.d(TAG, "Screen onDestroy - cancelling scope")
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate called - isLoading: $isLoading, apps count: ${apps.size}, error: $errorMessage")
        
        errorMessage?.let { error ->
            Log.e(TAG, "Showing error template: $error")
            return MessageTemplate.Builder(error)
                .setTitle(carContext.getString(R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        if (isLoading) {
            Log.d(TAG, "Showing loading template")
            return GridTemplate.Builder()
                .setTitle(carContext.getString(R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        if (apps.isEmpty()) {
            Log.w(TAG, "No apps available - showing empty message")
            return MessageTemplate.Builder(
                carContext.getString(R.string.no_apps_in_category)
            )
                .setTitle(carContext.getString(R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        Log.d(TAG, "Building grid template with ${apps.size} apps")
        val listBuilder = ItemList.Builder()
        val iconCompat = IconCompat.createWithResource(
            carContext,
            R.mipmap.ic_launcher_round
        )
        val carIcon = CarIcon.Builder(iconCompat).build()

        apps.take(MAX_GRID_ITEMS).forEach { app ->
            listBuilder.addItem(
                GridItem.Builder()
                    .setTitle(app.appName)
                    .setText(app.category?.replace('_', ' ') ?: "")
                    .setImage(carIcon)
                    .setOnClickListener { onAppSelected(app) }
                    .build()
            )
        }

        return GridTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun onAppSelected(app: AppInfo) {
        CarToast.makeText(
            carContext,
            carContext.getString(R.string.car_app_selection_message, app.appName),
            CarToast.LENGTH_SHORT
        ).show()

        try {
            val intent = Intent(carContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            carContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity", e)
        }
    }

    private fun loadApps() {
        Log.d(TAG, "loadApps() called")
        scope.launch {
            isLoading = true
            errorMessage = null
            withContext(Dispatchers.Main) { 
                Log.d(TAG, "Invalidating template - showing loading state")
                invalidate() 
            }

            try {
                Log.d(TAG, "Making API call to fetch apps")
                
                var appResponse: com.example.plsworkver3.data.AppListResponse? = null
                var useFallback = false
                
                // Try Retrofit/OkHttp first
                try {
                    val response = NetworkClient.apiService.getApps(System.currentTimeMillis())
                    if (response.isSuccessful) {
                        appResponse = response.body()
                        Log.d(TAG, "Retrofit request successful")
                    } else {
                        Log.w(TAG, "Retrofit request failed, trying fallback...")
                        useFallback = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Retrofit error, trying fallback: ${e.message}")
                    useFallback = true
                }
                
                // Use fallback if Retrofit failed
                if (useFallback || appResponse == null) {
                    Log.d(TAG, "Using HttpURLConnection fallback...")
                    appResponse = NetworkClient.getAppsFallback(System.currentTimeMillis())
                }
                
                if (appResponse != null) {
                    apps = appResponse.apps
                    errorMessage = null
                    Log.d(TAG, "Successfully loaded ${apps.size} apps")
                } else {
                    errorMessage = "Failed to load apps. All network methods failed."
                    Log.e(TAG, "All network methods failed")
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> 
                        "Cannot reach server. Check DNS settings or internet connection."
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Connection timeout. Please check your internet connection."
                    e.message?.contains("Failed to connect", ignoreCase = true) == true -> 
                        "Failed to connect to server. Check your internet connection."
                    e.message?.contains("SSL", ignoreCase = true) == true -> 
                        "SSL error. Please check network security settings."
                    else -> 
                        "Network error: ${e.message ?: e.javaClass.simpleName}"
                }
                errorMessage = errorMsg
                Log.e(TAG, "Exception loading apps: ${e.message}", e)
                e.printStackTrace()
            } finally {
                isLoading = false
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Invalidating template - final state (isLoading: false)")
                    invalidate()
                }
            }
        }
    }

    companion object {
        private const val MAX_GRID_ITEMS = 60
        private const val TAG = "LiAutoCarApp"
    }
}

