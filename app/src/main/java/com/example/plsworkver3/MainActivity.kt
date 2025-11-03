package com.example.plsworkver3

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.plsworkver3.data.AppInfo
import com.example.plsworkver3.data.AppListResponse
import com.example.plsworkver3.network.NetworkClient
import com.example.plsworkver3.ui.DynamicLayoutManager
import com.example.plsworkver3.ui.AppGridAdapter
import com.example.plsworkver3.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.util.*


class MainActivity : AppCompatActivity() {

    // Dynamic layout
    private lateinit var appContainer: GridView
    private lateinit var dynamicLayoutManager: DynamicLayoutManager
    private var appList: List<AppInfo> = emptyList()
    private var appAdapter: AppGridAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set main layout immediately and initialize UI
        setContentView(R.layout.activity_main)
        println("🚀 MainActivity onCreate() called - activity_main set")

        try {
            // Check internet early; if unavailable, show error page
            if (!NetworkUtils.isInternetAvailable(this)) {
                startActivity(Intent(this, ErrorActivity::class.java))
                finish()
                return
            }
            checkAndRequestDownloadPermissions()
            setupLanguageButtons()
            println("✅ Language buttons setup complete")

            setupDynamicLayout()
            println("✅ Dynamic layout setup complete")

            // Removed temporary loading TextView used for debugging

            // Optional: show quick feedback while loading
            Toast.makeText(this, "App is loading...", Toast.LENGTH_SHORT).show()

            loadAppsFromApi()
            println("✅ API loading started")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            println("❌ Error in onCreate: ${e.message}")
        }
    }

    private fun checkAndRequestDownloadPermissions() {
        try {
            val prefs = getSharedPreferences("perm_prefs", MODE_PRIVATE)
            // Storage permission only relevant on Android 9 (API 28) and below
            if (android.os.Build.VERSION.SDK_INT <= 28) {
                val alreadyRequested = prefs.getBoolean("storage_perm_requested", false)
                val hasPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission && !alreadyRequested) {
                    requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
                    prefs.edit().putBoolean("storage_perm_requested", true).apply()
                }
            }

            // Android 13+ notifications (DownloadManager notifications)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val askedNotif = prefs.getBoolean("asked_post_notifications", false)
                val notifGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted && !askedNotif) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                    prefs.edit().putBoolean("asked_post_notifications", true).apply()
                }
            }

            // Unknown sources (install from this app)
            val askedUnknown = prefs.getBoolean("asked_unknown_sources", false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val canInstall = packageManager.canRequestPackageInstalls()
                if (!canInstall && !askedUnknown) {
                    // Show dialog explaining why we need the permission, then open settings
                    showInstallPermissionDialog()
                    prefs.edit().putBoolean("asked_unknown_sources", true).apply()
                }
            }
        } catch (e: Exception) {
            println("❌ Permission init error: ${e.message}")
        }
    }

    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            try {
                if (intent?.action == android.content.Intent.ACTION_PACKAGE_ADDED ||
                    intent?.action == android.content.Intent.ACTION_PACKAGE_REMOVED ||
                    intent?.action == android.content.Intent.ACTION_PACKAGE_CHANGED) {
                    refreshDynamicApps()
                }
            } catch (e: Exception) {
                println("❌ Error handling package change: ${e.message}")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                this,
                packageChangeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            println("❌ Error registering package receiver: ${e.message}")
        }

        // Listen for detected package name from APK
        try {
            val detectFilter = android.content.IntentFilter("com.example.plsworkver3.PACKAGE_DETECTED")
            ContextCompat.registerReceiver(
                this,
                packageDetectedReceiver,
                detectFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            // ignore
        }

        // Listen for install session result
        try {
            val installResultFilter = android.content.IntentFilter("com.example.plsworkver3.INSTALL_RESULT")
            ContextCompat.registerReceiver(
                this,
                installResultReceiver,
                installResultFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (e: Exception) {
            // ignore if already unregistered
        }
        try {
            unregisterReceiver(packageDetectedReceiver)
        } catch (e: Exception) {
            // ignore
        }
        try { unregisterReceiver(installResultReceiver) } catch (_: Exception) {}
    }

    private val packageDetectedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val oldPkg = intent?.getStringExtra("oldPackage") ?: return
            val newPkg = intent.getStringExtra("newPackage") ?: return
            try {
                if (appList.isNotEmpty()) {
                    appList = appList.map { app ->
                        if (app.packageName == oldPkg) app.copy(packageName = newPkg) else app
                    }
                    AppManager.updateAppData(appList)
                    refreshDynamicApps()
                }
            } catch (e: Exception) {
                println("❌ Error applying detected package update: ${e.message}")
            }
        }
    }

    private val installResultReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            try {
                val status = intent?.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)
                val msg = intent?.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(this@MainActivity, "Install result: $status ${msg ?: ""}", Toast.LENGTH_SHORT).show()
                refreshDynamicApps()
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh button states when user returns to the activity
        refreshDynamicApps()
    }

    override fun onRestart() {
        super.onRestart()
        // Handle app restart properly
        refreshDynamicApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intent (when app is already running)
        refreshDynamicApps()
    }

    private fun setupLanguageButtons() {
        val btnEnglish = findViewById<Button>(R.id.btnEnglish)
        val btnRussian = findViewById<Button>(R.id.btnRussian)
        val btnArmenian = findViewById<Button>(R.id.btnArmenian)

        btnEnglish.setOnClickListener { setLocale("en") }
        btnRussian.setOnClickListener { setLocale("ru") }
        btnArmenian.setOnClickListener { setLocale("hy") }
    }



    private fun handleAppButtonClick(packageName: String, appDisplayName: String) {
        val isInstalled = AppManager.isAppInstalled(this, packageName)

        if (isInstalled) {
            // App is installed - directly remove it
            AppManager.uninstallApp(this, packageName)
        } else {
            // App not installed - download and install
            showDownloadConfirmationDialog(packageName, appDisplayName)
        }
    }


    private fun showDownloadConfirmationDialog(packageName: String, appName: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.download_title))
            .setMessage(getString(R.string.download_message, appName))
            .setPositiveButton(getString(R.string.download_button)) { _, _ ->
                // Download and install app automatically
                AppManager.downloadAndInstallApp(this, packageName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRemoveConfirmationDialog(packageName: String, appName: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_title))
            .setMessage("Are you sure you want to remove $appName?")
            .setPositiveButton(getString(R.string.remove_button)) { _, _ ->
                AppManager.uninstallApp(this, packageName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupDynamicLayout() {
        try {
            appContainer = findViewById(R.id.appContainer)
            println("✅ App container found: $appContainer")
            
            dynamicLayoutManager = DynamicLayoutManager(this)
            println("✅ Dynamic layout manager created")
        } catch (e: Exception) {
            println("❌ Error in setupDynamicLayout: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun loadAppsFromApi() {
        println("🚀 loadAppsFromApi() called!")
        lifecycleScope.launch {
            try {
                println("🔍 Trying to fetch apps from API...")
                println("🔗 API URL: https://raw.githubusercontent.com/JoraAbovyanGit/LiArm-Store/main/apps.json")
                val response = NetworkClient.apiService.getApps(System.currentTimeMillis())
                println("📡 Response code: ${response.code()}")
                println("📡 Response message: ${response.message()}")
                println("📡 Response headers: ${response.headers()}")
                
                if (response.isSuccessful) {
                    response.body()?.let { appResponse ->
                        println("✅ Successfully loaded ${appResponse.apps.size} apps")
                        
                        // Debug: Print app details including icon URLs
                        appResponse.apps.forEach { app ->
                            println("📱 App: ${app.appName} - Icon: ${app.appIcon}")
                        }
                        
                        appList = appResponse.apps
                        AppManager.updateAppData(appList)
                        displayDynamicApps()
                        

                        Toast.makeText(this@MainActivity, "Loaded ${appResponse.apps.size} apps", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        println("❌ Response body is null")
                        Toast.makeText(this@MainActivity, "No data received from server", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    println("❌ API call failed: ${response.code()} - ${response.message()}")
                    println("❌ Error body: ${response.errorBody()?.string()}")
                    println("❌ Full URL attempted: https://raw.githubusercontent.com/JoraAbovyanGit/LiArm-Store/main/apps.json")
                    Toast.makeText(this@MainActivity, "Failed to load apps: ${response.code()}", Toast.LENGTH_LONG).show()
                    
                    // Fallback to test data
                    loadTestData()
                }
            } catch (e: Exception) {
                println("💥 Network error: ${e.message}")
                println("💥 Exception type: ${e.javaClass.simpleName}")
                Toast.makeText(this@MainActivity, "Network error, using test data", Toast.LENGTH_LONG).show()
                
                // Load test data when network fails
                loadTestData()
                e.printStackTrace()
            }
        }
    }
    
    private fun loadTestData() {
        println("🧪 Loading test data...")
        try {
            appList = listOf(
                AppInfo(
                    appName = "Yandex Navigator",
                    appIcon = "https://firebasestorage.googleapis.com/v0/b/liarm-store.firebasestorage.app/o/icons%2Fnavig.png?alt=media",
                    packageName = "ru.yandex.yandexnavi",
                    downloadUrl = "https://firebasestorage.googleapis.com/v0/b/liarm-store.firebasestorage.app/o/apps%2Fru.yandex.yandexnavi.apk?alt=media",
                    appDescription = "Navigation and maps app",
                    appVersion = "1.0.0"
                ),
                AppInfo(
                    appName = "Telegram",
                    appIcon = "https://firebasestorage.googleapis.com/v0/b/liarm-store.firebasestorage.app/o/icons%2Ftelegram.png?alt=media",
                    packageName = "org.telegram.messenger",
                    downloadUrl = "https://firebasestorage.googleapis.com/v0/b/liarm-store.firebasestorage.app/o/apps%2Ftelegram.apk?alt=media",
                    appDescription = "Fast and secure messaging",
                    appVersion = "12.1.1"
                )
            )
            
            AppManager.updateAppData(appList)
            displayDynamicApps()
            Toast.makeText(this, "Loaded 2 test apps", Toast.LENGTH_SHORT).show()
            println("✅ Test data loaded successfully")
        } catch (e: Exception) {
            println("❌ Error loading test data: ${e.message}")
        }
    }
    
    private fun getNumColumns(): Int {
        // Always use 2 columns regardless of orientation
        return 2
    }
    
    private fun displayDynamicApps() {
        try {
            println("🧩 Displaying ${appList.size} apps in grid")
            appAdapter = AppGridAdapter(this, appList) { app ->
                handleAppButtonClick(app.packageName, app.appName)
            }
            appContainer.adapter = appAdapter
            
            // Update number of columns based on orientation
            val numColumns = getNumColumns()
            appContainer.numColumns = numColumns
            
            // Fix GridView height issue inside ScrollView
            // GridView with wrap_content inside ScrollView only shows first row
            // We need to calculate and set explicit height
            appContainer.post {
                try {
                    val numRows = (appList.size + numColumns - 1) / numColumns // Ceiling division
                    
                    // Estimate item height based on card layout:
                    // - Icon: 56dp
                    // - Padding: 12dp top + 12dp bottom = 24dp
                    // - Version text: ~20dp
                    // - Total: ~100-110dp per item
                    val itemHeightDp = 110
                    val itemHeightPx = (itemHeightDp * resources.displayMetrics.density).toInt()
                    
                    // Vertical spacing (8dp from layout)
                    val verticalSpacingPx = (8 * resources.displayMetrics.density).toInt()
                    
                    // Calculate total height
                    val calculatedHeight = (numRows * itemHeightPx) + 
                                          ((numRows - 1) * verticalSpacingPx) + 
                                          (appContainer.paddingTop + appContainer.paddingBottom)
                    
                    val layoutParams = appContainer.layoutParams
                    layoutParams.height = calculatedHeight
                    appContainer.layoutParams = layoutParams
                    
                    println("✅ Grid height set: ${appList.size} items, $numColumns columns, $numRows rows, height=${calculatedHeight}px")
                } catch (e: Exception) {
                    println("⚠️ Error calculating GridView height: ${e.message}")
                    e.printStackTrace()
                }
            }
            println("✅ Grid adapter set")
        } catch (e: Exception) {
            println("❌ Error in displayDynamicApps: ${e.message}")
            Toast.makeText(this, "Error displaying apps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun refreshDynamicApps() {
        if (appList.isNotEmpty()) {
            // Notify adapter that data changed so it refreshes button states
            appAdapter?.notifyDataSetChanged()
            
            // Update number of columns based on orientation
            val numColumns = getNumColumns()
            appContainer.numColumns = numColumns
            
            // Recalculate GridView height after data change
            appContainer.post {
                try {
                    val numRows = (appList.size + numColumns - 1) / numColumns
                    val itemHeightPx = (110 * resources.displayMetrics.density).toInt()
                    val verticalSpacingPx = (8 * resources.displayMetrics.density).toInt()
                    val calculatedHeight = (numRows * itemHeightPx) + 
                                          ((numRows - 1) * verticalSpacingPx) + 
                                          (appContainer.paddingTop + appContainer.paddingBottom)
                    
                    val layoutParams = appContainer.layoutParams
                    layoutParams.height = calculatedHeight
                    appContainer.layoutParams = layoutParams
                } catch (e: Exception) {
                    println("⚠️ Error recalculating GridView height: ${e.message}")
                }
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Refresh the grid layout when orientation changes
        if (appList.isNotEmpty()) {
            displayDynamicApps()
        }
    }

    private fun showInstallPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs permission to install other apps from this store. Please enable 'Install unknown apps' in the settings that will open.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    println("❌ Error opening install permission settings: ${e.message}")
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
        recreate()
    }
}
