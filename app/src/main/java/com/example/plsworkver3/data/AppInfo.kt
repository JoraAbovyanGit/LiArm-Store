package com.example.plsworkver3.data

import com.google.gson.annotations.SerializedName

data class AppInfo(
    @SerializedName("app_name")
    val appName: String,
    
    @SerializedName("app_icon")
    val appIcon: String,
    
    @SerializedName("package_name")
    val packageName: String,
    
    @SerializedName("download_url")
    val downloadUrl: String,
    
    @SerializedName("app_description")
    val appDescription: String? = null,
    
    // Optional: Language-specific descriptions
    @SerializedName("app_description_en")
    val appDescriptionEn: String? = null,
    
    @SerializedName("app_description_ru")
    val appDescriptionRu: String? = null,
    
    @SerializedName("app_description_hy")
    val appDescriptionHy: String? = null,
    
    @SerializedName("app_version")
    val appVersion: String? = null,
    
    @SerializedName("category")
    val category: String? = "main"
)

data class AppListResponse(
    @SerializedName("apps")
    val apps: List<AppInfo>
)
