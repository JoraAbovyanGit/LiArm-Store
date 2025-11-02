package com.example.plsworkver3.storage

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.plsworkver3.R
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

object FirebaseIconManager {
    
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference
    
    /**
     * Get Firebase Storage URL for an app icon
     * @param iconName The name of the icon file (e.g., "telegram.png", "whatsapp.png")
     * @return Firebase Storage URL or null if not found
     */
    fun getIconUrl(iconName: String): String {
        return "https://firebasestorage.googleapis.com/v0/b/liarm-store.firebasestorage.app/o/icons%2F$iconName?alt=media"
    }
    
    /**
     * Load app icon from Firebase Storage with fallback
     * @param context Android context
     * @param imageView ImageView to load the icon into
     * @param iconName Name of the icon file
     * @param appName Name of the app (for logging)
     */
    fun loadIconFromFirebase(
        context: Context,
        imageView: ImageView,
        iconName: String,
        appName: String
    ) {
        println("🖼️ Loading Firebase icon for $appName: $iconName")
        
        val iconUrl = getIconUrl(iconName)
        
        try {
            Glide.with(context)
                .load(iconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .fallback(R.drawable.ic_launcher_foreground)
                .timeout(5000)
                .into(imageView)
        } catch (e: Exception) {
            println("❌ Error loading Firebase icon for $appName: ${e.message}")
            imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }
    
    /**
     * Get a list of available icon names
     * This can be used to verify which icons are uploaded
     */
    fun getAvailableIcons(): List<String> {
        return listOf(
            "telegram.png",
            "whatsapp.png", 
            "youtube.png",
            "instagram.png",
            "facebook.png",
            "twitter.png",
            "spotify.png",
            "netflix.png",
            "discord.png",
            "zoom.png"
        )
    }
    
    /**
     * Generate Firebase Storage URLs for all available icons
     * This can be used to update your API data
     */
    fun generateIconUrls(): Map<String, String> {
        return getAvailableIcons().associateWith { iconName ->
            getIconUrl(iconName)
        }
    }
}
