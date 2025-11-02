package com.example.plsworkver3

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.net.toUri
import com.example.plsworkver3.installation.XapkInstaller
import java.io.IOException

object AppManager {

    private var appData: MutableMap<String, com.example.plsworkver3.data.AppInfo> = mutableMapOf()
    
    // Legacy constants for backward compatibility
    const val PACKAGE_YANDEX_NAVIGATOR = "ru.yandex.yandexnavi"
    const val PACKAGE_YANDEX_MAPS = "ru.yandex.yandexmaps"
    

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
            setVisibleInDownloadsUi(true)
            // Set MIME type based on extension
            val isXapk = apkUrl.lowercase().endsWith(".xapk")
            setMimeType(if (isXapk) "application/octet-stream" else "application/vnd.android.package-archive")
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
                                val isXapk = downloadedFile.name.lowercase().endsWith(".xapk")
                                if (isXapk) {
                                    println("📦 Detected XAPK, starting extraction and split install")
                                    installXapk(ctx, downloadedFile, packageName)
                                } else {
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
                                }
                            } else {
                                Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val isXapk = apkFile.name.lowercase().endsWith(".xapk")
                            if (isXapk) installXapk(ctx, apkFile, packageName) else installApk(ctx, apkFile, packageName)
                        }
                        cursor?.close()
                    } catch (e: Exception) {
                        println("❌ Error handling download complete: ${e.message}")
                        val isXapk = apkFile.name.lowercase().endsWith(".xapk")
                        if (isXapk) installXapk(ctx, apkFile, packageName) else installApk(ctx, apkFile, packageName)
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
        
        // Check if we have permission to install packages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // Request permission to install packages
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please enable 'Install unknown apps' permission and try again", Toast.LENGTH_LONG).show()
                return
            }
        }
        
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
            Toast.makeText(context, "Installing... Please tap 'Install' when prompted. Return to app after installation.", Toast.LENGTH_LONG).show()
            
            // Unregister receiver
            context.unregisterReceiver(downloadReceiver)
            downloadReceiver = null
        } catch (e: Exception) {
            Toast.makeText(context, "Installation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun installXapk(context: Context, xapkFile: File, requestedPackage: String) {
        println("📦 Starting XAPK install from: ${xapkFile.absolutePath}")
        println("📦 File exists: ${xapkFile.exists()}, size: ${xapkFile.length()}")
        try {
            val extractDir = XapkInstaller.extractXapk(context, xapkFile) ?: run {
                println("❌ Failed to extract XAPK")
                Toast.makeText(context, "Failed to extract XAPK", Toast.LENGTH_SHORT).show()
                return
            }
            println("✅ XAPK extracted to: ${extractDir.absolutePath}")
            val parts = XapkInstaller.findApkParts(extractDir)
            println("📦 Found ${parts.size} APK parts: ${parts.map { it.name }}")
            if (parts.isEmpty()) {
                Toast.makeText(context, "No APK files found in XAPK", Toast.LENGTH_SHORT).show()
                return
            }

            // Detect package from base.apk if possible
            val baseApk = parts.firstOrNull { it.name.contains("base", ignoreCase = true) } ?: parts.first()
            val detectedPkg = extractPackageNameFromApk(context, baseApk)
            if (!detectedPkg.isNullOrBlank() && detectedPkg != requestedPackage) {
                println("📦 XAPK package: $detectedPkg (requested: $requestedPackage)")
                getAppInfo(requestedPackage)?.let { info ->
                    appData.remove(requestedPackage)
                    appData[detectedPkg] = info.copy(packageName = detectedPkg)
                }
                try {
                    val updateIntent = Intent("com.example.plsworkver3.PACKAGE_DETECTED").apply {
                        putExtra("oldPackage", requestedPackage)
                        putExtra("newPackage", detectedPkg)
                    }
                    context.sendBroadcast(updateIntent)
                } catch (_: Exception) { }
            }

            // Filter splits to device ABI and display density to avoid parse errors
            val supportedAbis = android.os.Build.SUPPORTED_ABIS.map { it.lowercase() }
            val displayMetrics = context.resources.displayMetrics
            val densityDpi = displayMetrics.densityDpi
            fun matchesAbi(name: String): Boolean {
                val lower = name.lowercase()
                val hasAbiTag = listOf("armeabi-v7a", "arm64_v8a", "arm64-v8a", "x86", "x86_64").any { lower.contains(it) }
                if (!hasAbiTag) return true // language or base or config not tied to ABI
                return supportedAbis.any { abi -> lower.contains(abi.replace('-', '_')) || lower.contains(abi) }
            }
            fun matchesDensity(name: String): Boolean {
                val lower = name.lowercase()
                // If no density tag present, accept
                val hasDensityTag = listOf("ldpi","mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi").any { lower.contains(it) }
                if (!hasDensityTag) return true
                val wanted = when (densityDpi) {
                    in 0..160 -> "mdpi"
                    in 161..240 -> "hdpi"
                    in 241..320 -> "xhdpi"
                    in 321..480 -> "xxhdpi"
                    else -> "xxxhdpi"
                }
                return lower.contains(wanted)
            }

            val filteredParts = parts.filter { file ->
                val okAbi = matchesAbi(file.name)
                val okDpi = matchesDensity(file.name)
                if (!okAbi) println("⛔ Skipping split due to ABI mismatch: ${file.name} (device=${supportedAbis})")
                if (!okDpi) println("⛔ Skipping split due to density mismatch: ${file.name} (device=${densityDpi})")
                okAbi && okDpi
            }
            println("📦 After filtering: ${filteredParts.size} APKs will be installed: ${filteredParts.map { it.name }}")
            if (filteredParts.isEmpty()) {
                Toast.makeText(context, "No compatible APK splits for this device", Toast.LENGTH_LONG).show()
                return
            }

            // Install multiple APKs via PackageInstaller session (user confirmation required)
            println("📦 Creating PackageInstaller session...")
            val pm = context.packageManager
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            // If we detected a package, hint it to installer for better parsing
            detectedPkg?.let { 
                params.setAppPackageName(it)
                println("📦 Set package hint: $it")
            }
            val sessionId = installer.createSession(params)
            println("📦 Session ID: $sessionId")
            val session = installer.openSession(sessionId)
            try {
                filteredParts.forEachIndexed { index, file ->
                    val name = "split_${index}_${file.name}"
                    println("📦 Writing split $index: $name")
                    file.inputStream().use { input ->
                        session.openWrite(name, 0, file.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    println("✅ Wrote split $index")
                }
                println("📦 All splits written, committing session...")
                val intent = Intent("com.example.plsworkver3.INSTALL_RESULT")
                val pi = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                session.commit(pi.intentSender)
                println("✅ Session committed, installer should appear")
                Toast.makeText(context, "Opening installer...", Toast.LENGTH_LONG).show()
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            println("❌ XAPK install exception: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "XAPK install failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        // Prefer modern uninstall action
        val pkgUri = "package:$packageName".toUri()
        try {
            val uninstall = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = pkgUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(uninstall)
            Toast.makeText(context, "Opening uninstall screen...", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) { }

        // Fallback to ACTION_DELETE
        try {
            val delete = Intent(Intent.ACTION_DELETE).apply {
                data = pkgUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(delete)
            Toast.makeText(context, "Opening uninstall screen...", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) { }

        // Final fallback: open app details settings
        try {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = pkgUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            Toast.makeText(context, "Open app details to uninstall", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open uninstall screen", Toast.LENGTH_SHORT).show()
        }
    }
}
