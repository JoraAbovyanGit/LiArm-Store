package com.example.plsworkver3.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.plsworkver3.R
import com.example.plsworkver3.data.AppInfo
import com.example.plsworkver3.AppManager
import com.example.plsworkver3.storage.FirebaseIconManager

class DynamicLayoutManager(private val context: Context) {
    private val skipNetworkIconLoadingForDebug = false

    fun createAppCard(
        parent: ViewGroup,
        appInfo: AppInfo,
        onButtonClick: (AppInfo) -> Unit
    ): View {
        return try {
            val inflater = LayoutInflater.from(context)
            val cardView = inflater.inflate(R.layout.app_card_grid, parent, false)

            // Find views safely
            val appIcon = cardView.findViewById<ImageView>(R.id.appIcon)
            val appName = cardView.findViewById<TextView>(R.id.appName)
            val appDescription = cardView.findViewById<TextView>(R.id.appDescription)
            val appVersion = cardView.findViewById<TextView>(R.id.appVersion)
            val actionButton = cardView.findViewById<Button>(R.id.actionButton)

            // Set app data safely
            appName?.text = appInfo.appName ?: context.getString(R.string.unknown_app)
            appDescription?.text = getLocalizedDescription(context, appInfo) ?: context.getString(R.string.no_description_available)
            
            // Show version - check if update is available to show both versions
            val isInstalled = AppManager.isAppInstalled(context, appInfo.packageName)
            val needsUpdate = if (isInstalled) AppManager.needsUpdate(context, appInfo.packageName) else false
            
            if (needsUpdate && isInstalled) {
                val installedVersion = AppManager.getInstalledVersion(context, appInfo.packageName)
                val availableVersion = appInfo.appVersion ?: ""
                appVersion?.text = if (!installedVersion.isNullOrBlank() && !availableVersion.isBlank()) {
                    "v$installedVersion → v$availableVersion"
                } else if (!availableVersion.isBlank()) {
                    "v$availableVersion"
                } else {
                    ""
                }
            } else {
                appVersion?.text = if (!appInfo.appVersion.isNullOrBlank()) "v${appInfo.appVersion}" else ""
            }

            // Load app icon with Firebase Storage
            loadAppIconFromFirebase(appIcon, appInfo.appIcon, appInfo.appName)

            // Set button click listener safely
            actionButton?.setOnClickListener {
                try {
                    onButtonClick(appInfo)
                } catch (e: Exception) {
                    println("❌ Error in button click for ${appInfo.appName}: ${e.message}")
                }
            }

            // Set initial button state
            actionButton?.let {
                val isInstalled = AppManager.isAppInstalled(context, appInfo.packageName)
                val needsUpdate = if (isInstalled) AppManager.needsUpdate(context, appInfo.packageName) else false
                updateButtonState(it, isInstalled, needsUpdate, appInfo)
            }

            // Open app when clicking the icon if installed
            appIcon?.setOnClickListener {
                try {
                    val installed = AppManager.isAppInstalled(context, appInfo.packageName)
                    if (installed) {
                        AppManager.launchApp(context, appInfo.packageName)
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.app_not_installed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    println("❌ Error on icon click for ${appInfo.appName}: ${e.message}")
                }
            }

            cardView
        } catch (e: Exception) {
            println("❌ Error creating app card: ${e.message}")
            // Return a simple fallback view
            createFallbackCard(parent, appInfo, onButtonClick)
        }
    }

    private fun createFallbackCard(
        parent: ViewGroup,
        appInfo: AppInfo,
        onButtonClick: (AppInfo) -> Unit
    ): View {
        val inflater = LayoutInflater.from(context)
        val cardView = inflater.inflate(R.layout.app_card_grid, parent, false)

        try {
            val appName = cardView.findViewById<TextView>(R.id.appName)
            val appDescription = cardView.findViewById<TextView>(R.id.appDescription)
            val appVersion = cardView.findViewById<TextView>(R.id.appVersion)
            val actionButton = cardView.findViewById<Button>(R.id.actionButton)

            appName?.text = appInfo.appName ?: context.getString(R.string.unknown_app)
            appDescription?.text = getLocalizedDescription(context, appInfo) ?: ""
            appVersion?.text = if (!appInfo.appVersion.isNullOrBlank()) "v${appInfo.appVersion}" else ""
            actionButton?.text = context.getString(R.string.download_button)
            actionButton?.setOnClickListener { onButtonClick(appInfo) }
        } catch (e: Exception) {
            println("❌ Error creating fallback card: ${e.message}")
        }

        return cardView
    }

    fun updateAppCardView(
        cardView: View,
        appInfo: AppInfo,
        onButtonClick: (AppInfo) -> Unit
    ) {
        try {
            // Find views safely
            val appIcon = cardView.findViewById<ImageView>(R.id.appIcon)
            val appName = cardView.findViewById<TextView>(R.id.appName)
            val appDescription = cardView.findViewById<TextView>(R.id.appDescription)
            val appVersion = cardView.findViewById<TextView>(R.id.appVersion)
            val actionButton = cardView.findViewById<Button>(R.id.actionButton)

            // Update app data
            appName?.text = appInfo.appName ?: context.getString(R.string.unknown_app)
            appDescription?.text = getLocalizedDescription(context, appInfo) ?: context.getString(R.string.no_description_available)
            
            // Show version info - display both installed and available versions if update is available
            val isInstalled = AppManager.isAppInstalled(context, appInfo.packageName)
            val needsUpdate = if (isInstalled) AppManager.needsUpdate(context, appInfo.packageName) else false
            
            if (needsUpdate && isInstalled) {
                val installedVersion = AppManager.getInstalledVersion(context, appInfo.packageName)
                val availableVersion = appInfo.appVersion ?: ""
                appVersion?.text = if (!installedVersion.isNullOrBlank() && !availableVersion.isBlank()) {
                    "v$installedVersion → v$availableVersion"
                } else if (!availableVersion.isBlank()) {
                    "v$availableVersion"
                } else {
                    ""
                }
            } else {
                appVersion?.text = if (!appInfo.appVersion.isNullOrBlank()) "v${appInfo.appVersion}" else ""
            }

            // Load app icon
            loadAppIconFromFirebase(appIcon, appInfo.appIcon, appInfo.appName)

            // Update button click listener
            actionButton?.setOnClickListener {
                try {
                    onButtonClick(appInfo)
                } catch (e: Exception) {
                    println("❌ Error in button click for ${appInfo.appName}: ${e.message}")
                }
            }

            // Update button state based on install status and update availability
            actionButton?.let {
                updateButtonState(it, isInstalled, needsUpdate, appInfo)
            }

            // Update icon click listener
            appIcon?.setOnClickListener {
                try {
                    val installed = AppManager.isAppInstalled(context, appInfo.packageName)
                    if (installed) {
                        AppManager.launchApp(context, appInfo.packageName)
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.app_not_installed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    println("❌ Error on icon click for ${appInfo.appName}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("❌ Error updating app card view: ${e.message}")
        }
    }

    fun updateButtonState(button: Button, isInstalled: Boolean, needsUpdate: Boolean = false, appInfo: AppInfo? = null) {
        try {
            when {
                needsUpdate && isInstalled -> {
                    // Show UPDATE button
                    button.text = context.getString(R.string.update_button)
                    button.background = ContextCompat.getDrawable(context, R.drawable.button_rounded_update)
                }
                isInstalled -> {
                    // Show REMOVE button
                    button.text = context.getString(R.string.remove_button)
                    button.background = ContextCompat.getDrawable(context, R.drawable.button_rounded_remove)
                }
                else -> {
                    // Show DOWNLOAD button
                    button.text = context.getString(R.string.download_button)
                    button.background = ContextCompat.getDrawable(context, R.drawable.button_rounded_download)
                }
            }
        } catch (e: Exception) {
            println("❌ Error updating button state: ${e.message}")
            button.text = when {
                needsUpdate && isInstalled -> "UPDATE"
                isInstalled -> "REMOVE"
                else -> "DOWNLOAD"
            }
        }
    }

    fun updateAllButtonStates(appViews: List<View>, appList: List<AppInfo>) {
        try {
            appViews.forEachIndexed { index, view ->
                if (index < appList.size) {
                    val button = view.findViewById<Button>(R.id.actionButton)
                    val appInfo = appList[index]
                    val isInstalled = AppManager.isAppInstalled(context, appInfo.packageName)
                    val needsUpdate = if (isInstalled) AppManager.needsUpdate(context, appInfo.packageName) else false
                    updateButtonState(button, isInstalled, needsUpdate, appInfo)
                }
            }
        } catch (e: Exception) {
            println("❌ Error updating all button states: ${e.message}")
        }
    }

    private fun loadAppIconFromFirebase(imageView: ImageView?, iconUrl: String?, appName: String?) {
        if (imageView == null) {
            println("❌ ImageView is null for $appName")
            return
        }

        val safeAppName = appName ?: "Unknown"
        val safeIconUrl = iconUrl ?: ""

        println("🖼️ Loading Firebase icon for $safeAppName from: $safeIconUrl")

        // Always set fallback first
        try {
            imageView.setImageResource(R.drawable.ic_launcher_foreground)
        } catch (e: Exception) {
            println("❌ Error setting fallback image: ${e.message}")
        }

        if (skipNetworkIconLoadingForDebug) {
            // Keep placeholder only to rule out any image-loading issues
            println("🖼️ Skipping network icon loading (debug) for $safeAppName")
            return
        }

        // Try Firebase Storage first if it's a Firebase URL
        if (safeIconUrl.contains("firebasestorage.googleapis.com")) {
            try {
                FirebaseIconManager.loadIconFromFirebase(context, imageView, extractIconName(safeIconUrl), safeAppName)
                return
            } catch (e: Exception) {
                println("❌ Firebase loading failed, trying direct URL: ${e.message}")
            }
        }

        // Fallback to direct URL loading
        if (safeIconUrl.isBlank() || !isValidUrl(safeIconUrl)) {
            println("❌ Invalid URL for $safeAppName, using fallback")
            return
        }

        try {
            Glide.with(context)
                .load(safeIconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .fallback(R.drawable.ic_launcher_foreground)
                .timeout(5000)
                .into(imageView)
        } catch (e: Exception) {
            println("❌ Exception loading image for $safeAppName: ${e.message}")
        }
    }
    
    private fun extractIconName(firebaseUrl: String): String {
        return try {
            // Extract icon name from Firebase URL
            // URL format: https://firebasestorage.googleapis.com/v0/b/bucket/o/icons%2Ficonname.png?alt=media
            val parts = firebaseUrl.split("%2F")
            if (parts.size > 1) {
                parts[1].split("?")[0] // Remove query parameters
            } else {
                "default.png"
            }
        } catch (e: Exception) {
            "default.png"
        }
    }
    
    /**
     * Get localized description for an app based on current locale
     * Supports: app_description_en, app_description_ru, app_description_hy
     * Falls back to app_description if language-specific not found
     */
    private fun getLocalizedDescription(context: Context, appInfo: AppInfo): String? {
        val locale = context.resources.configuration.locales[0]?.language ?: "en"
        
        // Try to get language-specific description from JSON
        val localizedDesc = when (locale) {
            "ru" -> appInfo.appDescriptionRu
            "hy" -> appInfo.appDescriptionHy
            "en" -> appInfo.appDescriptionEn
            else -> null
        }
        
        // Fallback to base description if language-specific not available
        return localizedDesc ?: appInfo.appDescription
    }
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme == "http" || uri.scheme == "https"
        } catch (e: Exception) {
            false
        }
    }
}
