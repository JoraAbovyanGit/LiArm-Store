package com.example.plsworkver3.network

import com.example.plsworkver3.data.AppListResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("apps.json")
    suspend fun getApps(@Query("ts") ts: Long = System.currentTimeMillis()): Response<AppListResponse>
}
