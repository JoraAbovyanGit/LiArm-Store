package com.example.plsworkver3

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.plsworkver3.data.AppInfo
import com.example.plsworkver3.data.AppListResponse
import com.example.plsworkver3.network.NetworkClient
import com.example.plsworkver3.ui.DynamicLayoutManager
import com.example.plsworkver3.ui.AppRecyclerAdapter
import com.example.plsworkver3.ui.GridSpacingItemDecoration
import com.example.plsworkver3.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.util.*


class MainActivity : AppCompatActivity() {

    // Dynamic layout
    private lateinit var appContainer: RecyclerView
    private lateinit var dynamicLayoutManager: DynamicLayoutManager
    private var appList: List<AppInfo> = emptyList()
    private var filteredAppList: List<AppInfo> = emptyList()
    private var appAdapter: AppRecyclerAdapter? = null

    // Uninstall UX
    private var uninstallingPackage: String? = null
    private var uninstallDialog: AlertDialog? = null
    
    // Category and search
    private lateinit var drawerLayout: DrawerLayout
    private var currentCategory: String = "main"
    private var searchQuery: String = ""
    private val lastVisitedCategories = mutableListOf<String>()
    private lateinit var navContainer: LinearLayout
    private lateinit var searchEditText: EditText
    private var gridLayoutManager: GridLayoutManager? = null
    private var itemDecoration: GridSpacingItemDecoration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupMainActivity()
    }

    private fun setupMainActivity() {
        // Set main layout immediately and initialize UI
        setContentView(R.layout.activity_main)
        println("🚀 MainActivity setupMainActivity() called - activity_main set")

        try {
            // Check internet early - but don't block if check fails
            // Let the API call handle actual network errors
            val hasNetwork = NetworkUtils.isInternetAvailable(this)
            if (!hasNetwork) {
                println("⚠️ Initial network check failed, but proceeding to try API call")
            }
            checkAndRequestDownloadPermissions()
            loadLastVisitedCategories()
            setupHeaderElements()
            setupLanguageButtons()
            println("✅ Language buttons setup complete")

            setupDynamicLayout()
            println("✅ Dynamic layout setup complete")

            // Removed temporary loading TextView used for debugging

            // Optional: show quick feedback while loading
            Toast.makeText(this, getString(R.string.app_loading), Toast.LENGTH_SHORT).show()

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
                    // Close uninstall dialog when the targeted package is removed
                    if (intent.action == android.content.Intent.ACTION_PACKAGE_REMOVED) {
                        val removedPkg = intent.data?.schemeSpecificPart
                        if (!removedPkg.isNullOrBlank() && removedPkg == uninstallingPackage) {
                            uninstallDialog?.dismiss()
                            uninstallDialog = null
                            Toast.makeText(this@MainActivity, getString(R.string.removed_package, removedPkg), Toast.LENGTH_SHORT).show()
                            uninstallingPackage = null
                        }
                    }
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

        // If uninstall was canceled, close dialog and notify
        uninstallingPackage?.let { pkg ->
            try {
                val stillInstalled = AppManager.isAppInstalled(this, pkg)
                if (stillInstalled && uninstallDialog != null) {
                    uninstallDialog?.dismiss()
                    uninstallDialog = null
                    Toast.makeText(this, getString(R.string.uninstall_canceled), Toast.LENGTH_SHORT).show()
                    // Offer next actions when cancel detected
                    showUninstallCanceledDialog(pkg)
                    uninstallingPackage = null
                }
            } catch (_: Exception) {}
        }
    }

    private fun showUninstallCanceledDialog(packageName: String) {
        val appDisplayName = appList.firstOrNull { it.packageName == packageName }?.appName ?: packageName
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_title))
            .setMessage(getString(R.string.uninstall_canceled_message, appDisplayName))
            .setPositiveButton(getString(R.string.retry_uninstall)) { _, _ ->
                AppManager.uninstallApp(this, packageName)
            }
            .setNeutralButton(getString(R.string.open_app_settings)) { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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

    private fun setupHeaderElements() {
        try {
            drawerLayout = findViewById(R.id.drawerLayout)
            navContainer = findViewById(R.id.navContainer)
            searchEditText = findViewById(R.id.searchEditText)
            
            // Menu button - open drawer
            findViewById<ImageButton>(R.id.menuButton)?.setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            // Setup drawer category items
            setupDrawerCategories()
            
            // Setup navigation bar (Main + last 2 categories)
            updateNavigationBar()
            
            // Search functionality
            setupSearchFunctionality()
        } catch (e: Exception) {
            println("❌ Error setting up header elements: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupDrawerCategories() {
        val categories = mapOf(
            "main" to R.id.categoryMain,
            "maps" to R.id.categoryMaps,
            "music" to R.id.categoryMusic,
            "charge_stations" to R.id.categoryChargeStations,
            "messengers" to R.id.categoryMessengers,
            "entertainment" to R.id.categoryEntertainment,
            "navigators" to R.id.categoryNavigators,
            "other" to R.id.categoryOther
        )
        
        categories.forEach { (category, viewId) ->
            findViewById<TextView>(viewId)?.setOnClickListener {
                selectCategory(category)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }
    
    private fun selectCategory(category: String) {
        // Update last visited categories
        if (category != "main") {
            lastVisitedCategories.remove(category)
            lastVisitedCategories.add(0, category)
            if (lastVisitedCategories.size > 2) {
                lastVisitedCategories.removeAt(2)
            }
            // Save to SharedPreferences
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putString("last_categories", lastVisitedCategories.joinToString(","))
                .apply()
        }
        
        currentCategory = category
        updateNavigationBar()
        filterAndDisplayApps()
    }
    
    private fun updateNavigationBar() {
        navContainer.removeAllViews()
        
        // Add Main button
        val mainButton = createNavButton(getString(R.string.category_main), "main")
        val mainParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        }
        mainButton.layoutParams = mainParams
        navContainer.addView(mainButton)
        
        // Add last 2 visited categories
        val lastCategories = lastVisitedCategories.take(2)
        lastCategories.forEach { category ->
            val button = createNavButton(getCategoryName(category), category)
            navContainer.addView(button)
        }
    }
    
    private fun createNavButton(text: String, category: String): TextView {
        val button = TextView(this)
        button.text = text
        button.setTextColor(ContextCompat.getColor(this, R.color.text_primary_dark))
        button.textSize = 14f
        val padding = (12 * resources.displayMetrics.density).toInt()
        button.setPadding(padding, padding / 2, padding, padding / 2)
        button.background = ContextCompat.getDrawable(this, R.drawable.nav_item_background)
        button.isClickable = true
        button.isFocusable = true
        
        // Highlight current category
        if (category == currentCategory) {
            button.setTextColor(ContextCompat.getColor(this, R.color.accent_secondary))
        }
        
        button.setOnClickListener {
            selectCategory(category)
        }
        
        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
            marginStart = (8 * resources.displayMetrics.density).toInt()
        }
        button.layoutParams = params
        
        return button
    }
    
    private fun getCategoryName(category: String): String {
        return when (category) {
            "main" -> getString(R.string.category_main)
            "maps" -> getString(R.string.category_maps)
            "music" -> getString(R.string.category_music)
            "charge_stations" -> getString(R.string.category_charge_stations)
            "messengers" -> getString(R.string.category_messengers)
            "entertainment" -> getString(R.string.category_entertainment)
            "navigators" -> getString(R.string.category_navigators)
            "other" -> getString(R.string.category_other)
            else -> category
        }
    }
    
    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                filterAndDisplayApps()
            }
        })
        
        findViewById<ImageButton>(R.id.searchButton)?.setOnClickListener {
            // Search is handled by TextWatcher
        }
    }
    
    private fun filterAndDisplayApps() {
        filteredAppList = appList.filter { app ->
            // First filter by category (search only within current category)
            val matchesCategory = if (currentCategory == "main") {
                // Main shows all categories
                true
            } else {
                // Other categories: only show apps from that category
                app.category?.lowercase() == currentCategory.lowercase()
            }
            
            // Then filter by search query (only if category matches)
            val matchesSearch = searchQuery.isEmpty() || 
                app.appName.lowercase().contains(searchQuery)
            
            // Both conditions must be true
            matchesCategory && matchesSearch
        }
        
        // Show/hide no results message
        updateNoResultsMessage()
        
        displayDynamicApps()
    }
    
    private fun updateNoResultsMessage() {
        val noResultsMessage = findViewById<TextView>(R.id.noResultsMessage)
        val appContainer = findViewById<RecyclerView>(R.id.appContainer)
        
        if (filteredAppList.isEmpty() && appList.isNotEmpty()) {
            // Show no results message
            noResultsMessage?.visibility = View.VISIBLE
            appContainer?.visibility = View.GONE
            
            // Set appropriate message based on search and category
            val message = if (searchQuery.isNotEmpty()) {
                // User searched for something
                if (currentCategory == "main") {
                    getString(R.string.no_results, searchQuery)
                } else {
                    getString(R.string.no_results_in_category, searchQuery)
                }
            } else {
                // No search query, just no apps in category
                getString(R.string.no_apps_in_category)
            }
            noResultsMessage?.text = message
        } else {
            // Hide no results message, show app list
            noResultsMessage?.visibility = View.GONE
            appContainer?.visibility = View.VISIBLE
        }
    }
    
    private fun loadLastVisitedCategories() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val saved = prefs.getString("last_categories", "") ?: ""
        if (saved.isNotEmpty()) {
            lastVisitedCategories.clear()
            lastVisitedCategories.addAll(saved.split(",").filter { it.isNotEmpty() })
        }
    }

    private fun setupLanguageButtons() {
        val btnEnglish = findViewById<Button>(R.id.btnEnglish)
        val btnRussian = findViewById<Button>(R.id.btnRussian)
        val btnArmenian = findViewById<Button>(R.id.btnArmenian)

        // Update active button indicator
        updateLanguageIndicator()

        btnEnglish.setOnClickListener { setLocale("en") }
        btnRussian.setOnClickListener { setLocale("ru") }
        btnArmenian.setOnClickListener { setLocale("hy") }
    }

    private fun updateLanguageIndicator() {
        val indicatorEnglish = findViewById<View>(R.id.indicatorEnglish)
        val indicatorRussian = findViewById<View>(R.id.indicatorRussian)
        val indicatorArmenian = findViewById<View>(R.id.indicatorArmenian)

        // Hide all indicators first
        indicatorEnglish.visibility = View.GONE
        indicatorRussian.visibility = View.GONE
        indicatorArmenian.visibility = View.GONE

        // Show indicator for current language
        val currentLang = resources.configuration.locales[0].language
        when (currentLang) {
            "ru" -> indicatorRussian.visibility = View.VISIBLE
            "hy" -> indicatorArmenian.visibility = View.VISIBLE
            else -> indicatorEnglish.visibility = View.VISIBLE
        }
    }



    private fun handleAppButtonClick(packageName: String, appDisplayName: String) {
        val isInstalled = AppManager.isAppInstalled(this, packageName)
        val needsUpdate = if (isInstalled) AppManager.needsUpdate(this, packageName) else false

        when {
            needsUpdate -> {
                // Show update confirmation dialog
                showUpdateConfirmationDialog(packageName, appDisplayName)
            }
            isInstalled -> {
            // Open app details screen directly for manual uninstall
            AppManager.uninstallApp(this, packageName)
            }
            else -> {
            // App not installed - download and install
            showDownloadConfirmationDialog(packageName, appDisplayName)
        }
    }
    }


    private fun showUpdateConfirmationDialog(packageName: String, appName: String) {
        val appInfo = AppManager.getAppInfo(packageName) ?: return
        val installedVersion = AppManager.getInstalledVersion(this, packageName) ?: "unknown"
        val availableVersion = appInfo.appVersion ?: "unknown"
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_title))
            .setMessage(getString(R.string.update_message, appName, installedVersion, availableVersion))
            .setPositiveButton(getString(R.string.update_button)) { _, _ ->
                // Download and install updated app
                AppManager.downloadAndInstallApp(this, packageName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
        println("🚀 loadAppsFromFirestore() called!")
        lifecycleScope.launch {
            try {
                println("🔍 Fetching apps from Firebase Firestore...")
                
                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.plsworkver3.data.FirestoreAppService.getAppsWithCache()
                }
                
                result.fold(
                    onSuccess = { appResponse ->
                        println("✅ Successfully loaded ${appResponse.apps.size} apps from Firestore")
                        
                        // Debug: Print app details
                        appResponse.apps.forEach { app ->
                            println("📱 App: ${app.appName} - Icon: ${app.appIcon}")
                        }
                        
                        appList = appResponse.apps
                        filteredAppList = appList
                        AppManager.updateAppData(appList)
                        filterAndDisplayApps()
                        
                        Toast.makeText(this@MainActivity, getString(R.string.loaded_apps, appResponse.apps.size), Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        println("❌ Firestore error: ${error.message}")
                        android.util.Log.e("MainActivity", "Firestore fetch failed", error)
                        Toast.makeText(this@MainActivity, getString(R.string.failed_to_load_apps, error.localizedMessage ?: "Unknown error"), Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@MainActivity, ErrorActivity::class.java))
                        finish()
                    }
                )
            } catch (e: Exception) {
                println("💥 Network error: ${e.message}")
                println("💥 Exception type: ${e.javaClass.simpleName}")
                println("💥 Stack trace: ${e.stackTraceToString()}")
                
                // Provide more specific error message
                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Connection timeout. Please check your internet connection."
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> 
                        "Cannot reach server. Check your internet connection or DNS settings."
                    e.message?.contains("SSL", ignoreCase = true) == true || 
                    e.message?.contains("certificate", ignoreCase = true) == true ||
                    e.message?.contains("chain validation", ignoreCase = true) == true -> 
                        "SSL certificate error. Please check your network security settings."
                    else -> 
                        "Network error: ${e.message ?: "Unknown error"}"
                }
                
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                
                // Only navigate to error page if it's a real network issue
                // Don't navigate if it's just a validation issue
                if (e.message?.contains("timeout") == true || 
                    e.message?.contains("Unable to resolve") == true ||
                    e.message?.contains("Failed to connect") == true) {
                startActivity(Intent(this@MainActivity, ErrorActivity::class.java))
                finish()
                }
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
    
    private fun displayDynamicApps() {
        try {
            val appsToDisplay = if (filteredAppList.isNotEmpty() || appList.isEmpty()) filteredAppList else appList
            println("🧩 Displaying ${appsToDisplay.size} apps in RecyclerView (filtered from ${appList.size} total)")
            
            // Setup GridLayoutManager only once
            if (gridLayoutManager == null) {
                gridLayoutManager = GridLayoutManager(this, 2)
                appContainer.layoutManager = gridLayoutManager
                
                // Add item decoration only once
                val spacing = resources.getDimensionPixelSize(R.dimen.grid_spacing)
                itemDecoration = GridSpacingItemDecoration(2, spacing, true)
                appContainer.addItemDecoration(itemDecoration!!)
            }
            
            // Create or update adapter
            appAdapter = AppRecyclerAdapter(appsToDisplay, dynamicLayoutManager) { app ->
                handleAppButtonClick(app.packageName, app.appName)
            }
            appContainer.adapter = appAdapter
            
            println("✅ RecyclerView adapter set with ${appsToDisplay.size} items")
        } catch (e: Exception) {
            println("❌ Error in displayDynamicApps: ${e.message}")
            Toast.makeText(this, getString(R.string.error_displaying_apps, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun refreshDynamicApps() {
        if (appList.isNotEmpty()) {
            filterAndDisplayApps()
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // RecyclerView handles orientation changes automatically
        if (appList.isNotEmpty()) {
            appAdapter?.notifyDataSetChanged()
        }
    }

    private fun showInstallPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.install_permission_message))
            .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    println("❌ Error opening install permission settings: ${e.message}")
                    Toast.makeText(this, getString(R.string.could_not_open_settings), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.later), null)
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
