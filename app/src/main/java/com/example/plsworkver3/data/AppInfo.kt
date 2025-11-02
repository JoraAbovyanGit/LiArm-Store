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
    val downloadUrl: String
)

data class AppListResponse(
    @SerializedName("apps")
    val apps: List<AppInfo>
)
