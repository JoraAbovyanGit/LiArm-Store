package com.example.plsworkver3.utils

object IconUrlGenerator {
    
    private const val FIREBASE_BUCKET = "liarm-store.firebasestorage.app"
    
    /**
     * Generate Firebase Storage URL for an app icon
     */
    fun generateFirebaseUrl(iconName: String): String {
        return "https://firebasestorage.googleapis.com/v0/b/$FIREBASE_BUCKET/o/icons%2F$iconName?alt=media"
    }
    
    /**
     * Generate URLs for common app icons
     */
    fun generateCommonIconUrls(): Map<String, String> {
        val commonIcons = listOf(
            "telegram.png",
            "whatsapp.png", 
            "youtube.png",
            "instagram.png",
            "facebook.png",
            "twitter.png",
            "spotify.png",
            "netflix.png",
            "discord.png",
            "zoom.png",
            "tiktok.png",
            "snapchat.png",
            "linkedin.png",
            "reddit.png",
            "pinterest.png"
        )
        
        return commonIcons.associateWith { iconName ->
            generateFirebaseUrl(iconName)
        }
    }
    
    /**
     * Print URLs in JSON format for easy copying to your API
     */
    fun printUrlsForApi() {
        println("📱 Firebase Storage URLs for your apps.json:")
        println("=============================================")
        
        generateCommonIconUrls().forEach { (iconName, url) ->
            println("\"$iconName\": \"$url\",")
        }
        
        println("\n📋 Instructions:")
        println("1. Upload these icon files to Firebase Storage in the 'icons' folder")
        println("2. Copy the URLs above into your apps.json file")
        println("3. Update your API data with these URLs")
    }
}
