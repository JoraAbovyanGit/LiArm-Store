# 📥 Automatic App Download Setup Instructions

## ✅ What's Been Implemented

Your app now has automatic download and install functionality, just like LiApp Store!

### Features Added:
1. **Automatic Downloads** - Downloads APKs directly from your server
2. **Auto-Install** - Automatically prompts for installation after download
3. **Button States** - Buttons change between "DOWNLOAD" and "REMOVE"
4. **Progress Tracking** - Shows download progress in notification bar
5. **FileProvider** - Secure APK installation for Android 7+

---

## 🚀 How to Complete Setup

### Step 1: Host Your APK Files

You need to host APK files somewhere accessible. Options:

#### Option A: GitHub Releases (Easiest)
1. Create a GitHub repository
2. Upload APK files
3. Go to Releases → Create a new release
4. Upload APKs and copy the "Download" URL

Example URLs will look like:
```
https://github.com/username/repo/releases/download/v1.0/app.apk
```

#### Option B: Your Own Server
- Upload APKs to your server
- Ensure they're publicly accessible
- Use HTTPS for security

#### Option C: Cloud Storage (Google Drive, Dropbox, etc.)
1. Upload APK to cloud storage
2. Get public sharing link
3. Convert to direct download link (use services like `d.gdocs.live` for Drive)

### Step 2: Update APK URLs in AppManager.kt

Open `app/src/main/java/com/example/plsworkver3/AppManager.kt`

Replace these lines (around line 24-25):
```kotlin
const val APK_URL_YANDEX_NAVIGATOR = "https://your-server.com/apps/yandex-navigator.apk"
const val APK_URL_YANDEX_MAPS = "https://your-server.com/apps/yandex-maps.apk"
```

With your actual APK URLs:
```kotlin
const val APK_URL_YANDEX_NAVIGATOR = "https://github.com/yourname/repo/releases/download/v1.0/yandex-navigator.apk"
const val APK_URL_YANDEX_MAPS = "https://github.com/yourname/repo/releases/download/v1.0/yandex-maps.apk"
```

### Step 3: Add More Apps

To add more apps (like App 3, App 4), follow this pattern:

1. **Add package name and URL** (in AppManager.kt):
```kotlin
const val PACKAGE_YOUR_NEW_APP = "com.your.package"
const val APK_URL_YOUR_NEW_APP = "https://your-server.com/app.apk"
```

2. **Update the map** (in AppManager.kt):
```kotlin
private val apkUrls = mapOf(
    PACKAGE_YANDEX_NAVIGATOR to APK_URL_YANDEX_NAVIGATOR,
    PACKAGE_YANDEX_MAPS to APK_URL_YANDEX_MAPS,
    PACKAGE_YOUR_NEW_APP to APK_URL_YOUR_NEW_APP  // Add this line
)
```

3. **Add button in layout** (activity_main.xml)
4. **Add button handler in MainActivity.kt**

---

## 🔧 How It Works

### Download Flow:
1. User taps "DOWNLOAD" button
2. Shows confirmation dialog
3. Starts downloading APK in background
4. Shows progress in notification bar
5. When download completes, automatically prompts user to install
6. User taps "Install" → App is installed
7. Returns to your app → Button changes to "REMOVE"

### Remove Flow:
1. User taps "REMOVE" button
2. Shows confirmation dialog
3. Opens Android's uninstall screen
4. User confirms removal
5. Returns to your app → Button changes to "DOWNLOAD"

---

## 📱 Permissions Added

All necessary permissions have been added to `AndroidManifest.xml`:
- ✅ `INTERNET` - Download APKs
- ✅ `REQUEST_INSTALL_PACKAGES` - Install APKs automatically
- ✅ `WRITE_EXTERNAL_STORAGE` - Save downloaded files (for Android 10 and below)
- ✅ `DOWNLOAD_WITHOUT_NOTIFICATION` - Silent downloads

---

## ⚠️ Important Notes

### Security
- Always use HTTPS for APK URLs
- Verify APK signatures before installing (optional but recommended)
- Test downloads on real devices

### Testing
1. Build and install your app on a device
2. Tap "DOWNLOAD" button
3. Watch notification for download progress
4. Installation prompt should appear automatically
5. After installation, button should show "REMOVE"

### Common Issues

**Problem:** "APK file not found" error
**Solution:** Check your APK URL is accessible and returns a file

**Problem:** Installation blocked
**Solution:** User needs to enable "Install from Unknown Sources" in settings (this will be prompted automatically)

**Problem:** Download doesn't start
**Solution:** Check internet connection and APK URL

---

## 🎯 Next Steps

1. Upload your APKs to hosting (GitHub, server, etc.)
2. Update URLs in `AppManager.kt`
3. Build and test
4. Add more apps as needed

Your app store is now ready! 🎉

