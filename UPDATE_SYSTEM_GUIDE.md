# Dynamic Update System - How It Works

## Overview
Your app now has a **fully dynamic update system** that automatically detects when apps need updates by comparing versions. You **don't need to check for updates daily** - the system does it automatically!

## How It Works

### 1. Version Comparison
- When your app loads the JSON from GitHub, it compares:
  - **Installed version**: Read from the device's PackageManager
  - **Available version**: From your JSON file (`app_version` field)

### 2. Automatic Detection
- If `available_version > installed_version` → Shows **UPDATE** button (orange)
- If `available_version == installed_version` → Shows **REMOVE** button (red)
- If app not installed → Shows **DOWNLOAD** button (green)

### 3. Update Process
- User taps **UPDATE** button
- Shows dialog: "Update AppName from version X.X.X to Y.Y.Y?"
- Downloads and installs the new APK from Firebase Storage
- Same process as download, but user sees it's an update

## What You Need to Do

### When You Upload a New APK:

1. **Upload APK to Firebase Storage** (same as before)
   - Upload to: `apps/package.name.apk`

2. **Update GitHub JSON File**
   - Update the `app_version` field (e.g., "1.0.0" → "1.0.1")
   - Update the `download_url` if the Firebase URL changed
   - Commit and push to GitHub

3. **That's it!** 
   - Users will automatically see the UPDATE button
   - No need to check daily or manually update anything

## JSON Format

```json
{
  "apps": [
    {
      "app_name": "My App",
      "app_icon": "https://...",
      "package_name": "com.example.app",
      "download_url": "https://firebasestorage.../app.apk",
      "app_description": "Description",
      "app_version": "1.0.1"  // ← Update this when you upload new APK
    }
  ]
}
```

## Version Format

- Supports semantic versioning: `1.2.3`
- Also works with: `1.0`, `2.5.1`, `10.0.0-beta`
- Comparison is done by splitting on `.` and comparing numbers
- Examples:
  - `1.0.1` > `1.0.0` ✓
  - `1.1.0` > `1.0.9` ✓
  - `2.0.0` > `1.9.9` ✓

## Benefits

✅ **No Daily Checking**: Users see updates automatically when you update JSON
✅ **Dynamic**: Works for 1 app or 1000 apps - no difference
✅ **User-Friendly**: Clear UPDATE button with version comparison
✅ **Easy Maintenance**: Just update JSON when you upload new APK
✅ **Automatic**: Version comparison happens in real-time

## Example Workflow

1. **You upload new APK to Firebase**: `telegram_12.2.0.apk`
2. **You update JSON**:
   ```json
   {
     "app_version": "12.2.0",  // Changed from "12.1.1"
     "download_url": "https://.../telegram_12.2.0.apk"
   }
   ```
3. **User opens your store app**
4. **System automatically detects**: Installed version (12.1.1) < Available version (12.2.0)
5. **User sees**: Orange "UPDATE" button
6. **User taps UPDATE**: Downloads and installs new version

## Technical Details

- **VersionUtils.kt**: Handles version comparison logic
- **AppManager.kt**: 
  - `getInstalledVersion()`: Gets version from device
  - `needsUpdate()`: Compares versions
- **DynamicLayoutManager.kt**: Updates button state and UI
- **MainActivity.kt**: Handles update confirmation and download

## Tips

1. **Always update version number** in JSON when uploading new APK
2. **Use consistent version format**: `major.minor.patch` (e.g., `1.2.3`)
3. **Test version comparison**: Install an old version, update JSON, verify UPDATE button appears
4. **Version display**: Shows "v1.0.0 → v1.0.1" when update is available

---
**That's it! Your update system is now fully automated and dynamic! 🚀**
