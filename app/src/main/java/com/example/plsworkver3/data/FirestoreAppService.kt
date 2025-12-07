package com.example.plsworkver3.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Service for fetching app data from Firebase Firestore.
 * More secure than GitHub raw URLs:
 * - Firebase Security Rules control access
 * - Real-time updates possible
 * - Better caching and offline support
 * - No public repository exposure
 */
object FirestoreAppService {
    private const val TAG = "FirestoreAppService"
    private const val COLLECTION_APPS = "apps"
    private const val FIELD_APP_NAME = "app_name"
    private const val FIELD_CREATED_AT = "createdAt"
    
    private val db = FirebaseFirestore.getInstance()
    
    /**
     * Fetch all apps from Firestore.
     * Apps are ordered by creation date (newest first).
     */
    suspend fun getApps(): Result<AppListResponse> {
        return try {
            Log.d(TAG, "Fetching apps from Firestore...")
            
            val snapshot = db.collection(COLLECTION_APPS)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .await()
            
            val apps = snapshot.documents.mapNotNull { doc ->
                try {
                    AppInfo(
                        appName = doc.getString("app_name") ?: "",
                        appIcon = doc.getString("app_icon") ?: "",
                        packageName = doc.getString("package_name") ?: "",
                        downloadUrl = doc.getString("download_url") ?: "",
                        appDescription = doc.getString("app_description") ?: "",
                        appVersion = doc.getString("app_version") ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing app document ${doc.id}: ${e.message}")
                    null
                }
            }
            
            Log.d(TAG, "Successfully fetched ${apps.size} apps from Firestore")
            Result.success(AppListResponse(apps))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching apps from Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Fetch apps with caching support.
     * Uses Firestore's built-in offline persistence.
     */
    suspend fun getAppsWithCache(): Result<AppListResponse> {
        return try {
            // Firestore automatically uses cache if offline
            // Source.CACHE for offline-only, Source.SERVER for online-only
            val snapshot = db.collection(COLLECTION_APPS)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            
            val apps = snapshot.documents.mapNotNull { doc ->
                try {
                    AppInfo(
                        appName = doc.getString("app_name") ?: "",
                        appIcon = doc.getString("app_icon") ?: "",
                        packageName = doc.getString("package_name") ?: "",
                        downloadUrl = doc.getString("download_url") ?: "",
                        appDescription = doc.getString("app_description") ?: "",
                        appVersion = doc.getString("app_version") ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing app document ${doc.id}: ${e.message}")
                    null
                }
            }
            
            Log.d(TAG, "Successfully fetched ${apps.size} apps from Firestore (with cache)")
            Result.success(AppListResponse(apps))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching apps, trying cache: ${e.message}")
            // Try cache if server fails
            try {
                val snapshot = db.collection(COLLECTION_APPS)
                    .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                    .get(com.google.firebase.firestore.Source.CACHE)
                    .await()
                
                val apps = snapshot.documents.mapNotNull { doc ->
                    try {
                        AppInfo(
                            appName = doc.getString("app_name") ?: "",
                            appIcon = doc.getString("app_icon") ?: "",
                            packageName = doc.getString("package_name") ?: "",
                            downloadUrl = doc.getString("download_url") ?: "",
                            appDescription = doc.getString("app_description") ?: "",
                            appVersion = doc.getString("app_version") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                Log.d(TAG, "Using cached data: ${apps.size} apps")
                Result.success(AppListResponse(apps))
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache also failed: ${cacheError.message}")
                Result.failure(e) // Return original error
            }
        }
    }
}

