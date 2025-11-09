package com.example.plsworkver3

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.os.UserManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import androidx.core.net.toUri
import java.io.IOException

object AppManager {

    private var appData: MutableMap<String, com.example.plsworkver3.data.AppInfo> = mutableMapOf()
    

    fun updateAppData(apps: List<com.example.plsworkver3.data.AppInfo>) {
        appData.clear()
        apps.forEach { app ->
            appData[app.packageName] = app
        }
    }

    fun getAppInfo(packageName: String): com.example.plsworkver3.data.AppInfo? {
        return appData[packageName]
    }
    

    fun getDownloadUrl(packageName: String): String? {
        return appData[packageName]?.downloadUrl
    }
    
    private var downloadReceiver: BroadcastReceiver? = null
    private var currentDownloadId: Long = -1
    private var lastDownloadedApkFile: File? = null

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        // Fast path: if we can obtain a launch intent, it's visible and installed
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) return true

        return try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get the installed version of an app
     * @return Version string (e.g., "1.2.3") or null if not installed
     */
    fun getInstalledVersion(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            packageInfo?.versionName
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if an app needs an update by comparing installed version with available version
     * @return true if update is available (available version > installed version)
     */
    fun needsUpdate(context: Context, packageName: String): Boolean {
        val appInfo = getAppInfo(packageName) ?: return false
        val availableVersion = appInfo.appVersion ?: return false
        val installedVersion = getInstalledVersion(context, packageName) ?: return false
        
        return com.example.plsworkver3.utils.VersionUtils.isNewerVersion(availableVersion, installedVersion)
    }


    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (launchIntent != null) {
                // Clear any existing task and start fresh
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                
                context.startActivity(launchIntent)
                true
            } else {
                // Try alternative launch method
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }


    fun downloadAndInstallApp(context: Context, packageName: String) {
        val apkUrl = getDownloadUrl(packageName) ?: run {
            Toast.makeText(context, "APK URL not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create download directory
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        
        // Create file name
        val fileName = "${packageName}_${System.currentTimeMillis()}.apk"
        val file = File(downloadDir, fileName)
        
        // Remove old file if exists
        if (file.exists()) {
            file.delete()
        }
        
        // Setup download manager
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadUri = apkUrl.toUri()
        
        val request = DownloadManager.Request(downloadUri).apply {
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setTitle("Downloading App")
            setDescription("Downloading application...")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            // Show completion notification from DownloadManager
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            @Suppress("DEPRECATION")
            setVisibleInDownloadsUi(true)
            // Set MIME type for APK files
            setMimeType("application/vnd.android.package-archive")
        }
        
        // Start download
        currentDownloadId = downloadManager.enqueue(request)
        lastDownloadedApkFile = file
        // Persist metadata for manifest receiver
        try {
            val prefs = context.getSharedPreferences("download_meta", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("downloadId", currentDownloadId)
                .putString("filePath", file.absolutePath)
                .putString("requestedPackage", packageName)
                .apply()
        } catch (_: Exception) {}
        
        // Setup broadcast receiver to handle download completion
        setupDownloadReceiver(context, packageName, file)
        
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
    }
    

    private fun setupDownloadReceiver(context: Context, packageName: String, apkFile: File) {
        if (downloadReceiver == null) {
            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    println("📥 Download complete broadcast received")
                    val dlId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (dlId != currentDownloadId) return

                    val ctx = context ?: return
                    try {
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val query = DownloadManager.Query().setFilterById(currentDownloadId)
                        val cursor = dm.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                            println("📥 Download status: $status, uri=$localUri")
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                // Resolve to a real file (handles file:// and content://)
                                val downloadedFile = resolveDownloadedFile(ctx, localUri, lastDownloadedApkFile ?: apkFile)
                                // Try to detect real package name from the downloaded APK
                                val detectedPkg = extractPackageNameFromApk(ctx, downloadedFile)
                                if (!detectedPkg.isNullOrBlank() && detectedPkg != packageName) {
                                    println("📦 Detected package from APK: $detectedPkg (requested: $packageName)")
                                    Toast.makeText(ctx, "Detected package: $detectedPkg", Toast.LENGTH_SHORT).show()
                                    // If our data map used a placeholder or wrong package, remap it
                                    getAppInfo(packageName)?.let { info ->
                                        appData.remove(packageName)
                                        appData[detectedPkg] = info.copy(packageName = detectedPkg)
                                    }
                                    // Notify UI layer to update any app list entries using old package
                                    try {
                                        val updateIntent = Intent("com.example.plsworkver3.PACKAGE_DETECTED").apply {
                                            putExtra("oldPackage", packageName)
                                            putExtra("newPackage", detectedPkg)
                                        }
                                        ctx.sendBroadcast(updateIntent)
                                    } catch (e: Exception) { }
                                }
                                installApk(ctx, downloadedFile, packageName)
                            } else {
                                Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            installApk(ctx, apkFile, packageName)
                        }
                        cursor?.close()
                    } catch (e: Exception) {
                        println("❌ Error handling download complete: ${e.message}")
                        installApk(ctx, apkFile, packageName)
                    }
                }
            }
            
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            ContextCompat.registerReceiver(
                context,
                downloadReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun resolveDownloadedFile(context: Context, localUriString: String?, fallback: File): File {
        if (localUriString.isNullOrBlank()) return fallback
        return try {
            val uri = Uri.parse(localUriString)
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                File(uri.path ?: return fallback)
            } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                val input = context.contentResolver.openInputStream(uri) ?: return fallback
                val target = File(context.cacheDir, "dl_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "file"}")
                input.use { ins -> target.outputStream().use { outs -> ins.copyTo(outs) } }
                target
            } else {
                fallback
            }
        } catch (_: IOException) {
            fallback
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Extract the package name from a local APK file.
     */
    fun extractPackageNameFromApk(context: Context, apkFile: File): String? {
        return try {
            val pm = context.packageManager
            val path = apkFile.absolutePath
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(path, 0)
            }
            // For some devices, need to set these for icon/resources, but packageName is accessible
            pkgInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }
    

    fun installApk(context: Context, apkFile: File, packageName: String) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if app is already installed (update scenario)
        val isInstalled = isAppInstalled(context, packageName)
        val isUpdate = isInstalled
        
        // Check if we have permission to install packages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // Check if we've already asked for permission
                val prefs = context.getSharedPreferences("perm_prefs", Context.MODE_PRIVATE)
                val alreadyAsked = prefs.getBoolean("asked_unknown_sources", false)
                
                if (alreadyAsked) {
                    // We already asked - just inform the user they need to enable it
                    Toast.makeText(context, "Please enable 'Install unknown apps' permission in Settings to continue", Toast.LENGTH_LONG).show()
                } else {
                    // First time asking - redirect to settings
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please enable 'Install unknown apps' permission and try again", Toast.LENGTH_LONG).show()
                    prefs.edit().putBoolean("asked_unknown_sources", true).apply()
                }
                return
            }
        }
        
        // Try using PackageInstaller API for better update handling (Android 5.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                installApkWithPackageInstaller(context, apkFile, packageName, isUpdate)
                return
            } catch (e: Exception) {
                println("❌ PackageInstaller failed, falling back to ACTION_VIEW: ${e.message}")
                // Fall through to ACTION_VIEW method
            }
        }
        
        // Fallback to ACTION_VIEW method
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                // For Android 8.0 and above
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            
            context.startActivity(intent)
            if (isUpdate) {
                Toast.makeText(context, "Updating app... Please tap 'Update' when prompted.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Installing... Please tap 'Install' when prompted. Return to app after installation.", Toast.LENGTH_LONG).show()
            }
            
            // Unregister receiver
            context.unregisterReceiver(downloadReceiver)
            downloadReceiver = null
        } catch (e: Exception) {
            Toast.makeText(context, "Installation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Install APK using PackageInstaller API (better for updates)
     * This method allows us to specify REPLACE_EXISTING flag for updates
     */
    @Suppress("DEPRECATION")
    private fun installApkWithPackageInstaller(context: Context, apkFile: File, packageName: String, isUpdate: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            if (isUpdate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Allow replacing existing app for updates
                setAppPackageName(packageName)
            }
        }
        
        var sessionId = -1
        try {
            sessionId = packageInstaller.createSession(params)
        } catch (e: Exception) {
            throw Exception("Failed to create install session: ${e.message}")
        }
        
        val session = packageInstaller.openSession(sessionId)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        
        try {
            inputStream = FileInputStream(apkFile)
            outputStream = session.openWrite("package", 0, -1)
            
            val buffer = ByteArray(65536)
            var length: Int
            while (inputStream.read(buffer).also { length = it } != -1) {
                outputStream.write(buffer, 0, length)
            }
            
            session.fsync(outputStream)
            outputStream.close()
            outputStream = null
            
            // Create install intent receiver
            val intent = Intent("com.example.plsworkver3.INSTALL_RESULT").apply {
                setPackage(context.packageName)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Commit the session
            session.commit(pendingIntent.intentSender)
            session.close()
            
            if (isUpdate) {
                Toast.makeText(context, "Updating app...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Installing app...", Toast.LENGTH_SHORT).show()
            }
            
            // Unregister receiver
            context.unregisterReceiver(downloadReceiver)
            downloadReceiver = null
        } catch (e: Exception) {
            session.abandon()
            throw Exception("Failed to install: ${e.message}")
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = "market://details?id=$packageName".toUri()
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = "https://play.google.com/store/apps/details?id=$packageName".toUri()
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Unable to open Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        println("🗑️ Attempting to uninstall: $packageName")
        val pkgUri = "package:$packageName".toUri()
        
        // Check if package is actually installed first
        if (!isAppInstalled(context, packageName)) {
            Toast.makeText(context, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
            return
        }

        // Explain common reasons before opening system UI
        if (isSystemApp(context, packageName)) {
            Toast.makeText(context, "System app or preinstalled update; cannot uninstall (you can only disable)", Toast.LENGTH_LONG).show()
            // Fall through to open details
        }

        if (hasUninstallRestriction(context)) {
            Toast.makeText(context, "Uninstall is blocked by device policy on this user/profile", Toast.LENGTH_LONG).show()
            // Fall through to open details so user can see status
        }
        // Open app details settings directly (single unified flow)
        try {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = pkgUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            println("🗑️ Opening app details settings for $packageName")
            context.startActivity(settingsIntent)
            Toast.makeText(context, "Tap 'Uninstall' in app details", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            println("❌ Failed to open app details: ${e.message}")
            Toast.makeText(context, "Unable to open uninstall screen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: Exception) { false }
    }

    private fun hasUninstallRestriction(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                // DISALLOW_UNINSTALL_APPS is the canonical restriction that blocks uninstall
                um.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS)
            } else false
        } catch (_: Exception) { false }
    }
}
