# Translation Guide - How to Add Multilingual Support

## Overview
Your app now supports translations for all UI elements (buttons, dialogs, messages) and app descriptions in **English**, **Russian**, and **Armenian**.

## How Translations Work

### 1. UI Strings (Buttons, Dialogs, Messages)
✅ **Already Done!** All UI strings are now in translation files:
- `values/strings.xml` - English (default)
- `values-ru/strings.xml` - Russian
- `values-hy/strings.xml` - Armenian

The app automatically uses the correct language based on the user's selection.

### 2. App Descriptions (From JSON)

App descriptions can be translated in two ways:

#### Option A: Language-Specific Fields in JSON (Recommended)

Update your JSON to include language-specific description fields:

```json
{
  "apps": [
    {
      "app_name": "My App",
      "package_name": "com.example.app",
      "app_description": "Default description (fallback)",
      "app_description_en": "English description here",
      "app_description_ru": "Описание на русском языке",
      "app_description_hy": "Հայերեն նկարագրություն",
      "app_version": "1.0.0",
      "download_url": "https://..."
    }
  ]
}
```

**How it works:**
- App checks current language (en/ru/hy)
- Uses `app_description_en`, `app_description_ru`, or `app_description_hy`
- Falls back to `app_description` if language-specific not found

#### Option B: Single Description (Current)

If you only provide `app_description`, it will be shown in all languages (not translated).

## JSON Structure for Translations

### Full Example:

```json
{
  "apps": [
    {
      "app_name": "Telegram",
      "app_icon": "https://...",
      "package_name": "org.telegram.messenger",
      "download_url": "https://...",
      "app_version": "12.2.0",
      
      "app_description": "Fast and secure messaging app",
      "app_description_en": "Fast and secure messaging app",
      "app_description_ru": "Быстрое и безопасное приложение для обмена сообщениями",
      "app_description_hy": "Արագ և անվտանգ հաղորդագրությունների հավելված"
    }
  ]
}
```

## What Gets Translated

### ✅ Translated (from string resources):
- All buttons: DOWNLOAD, UPDATE, REMOVE, Cancel
- Dialog titles and messages
- Error messages
- Toast notifications
- Permission messages
- Status messages

### ❌ NOT Translated (kept as-is):
- App names (e.g., "Telegram", "Yandex Navigator")
- Package names
- Version numbers

### 🔄 Conditionally Translated:
- App descriptions (if you add language-specific fields to JSON)

## Adding New Translations

### For UI Strings:
1. Add the string to `values/strings.xml` (English)
2. Add translation to `values-ru/strings.xml` (Russian)
3. Add translation to `values-hy/strings.xml` (Armenian)

### For App Descriptions:
1. Add language-specific fields to your JSON:
   - `app_description_en`
   - `app_description_ru`
   - `app_description_hy`
2. The app will automatically use the correct one based on user's language

## Testing Translations

1. **Change language** using the language buttons (Eng/Rus/Arm)
2. **Verify** all UI elements are translated
3. **Check** app descriptions show correct language (if you added language-specific fields)

## Tips

1. **Keep app names in English** - Users recognize them better
2. **Always provide fallback** - Use `app_description` as fallback
3. **Test all languages** - Make sure translations fit in UI
4. **Update JSON gradually** - You don't need to translate all 70+ apps at once

## Current Status

✅ **UI Strings**: Fully translated (English, Russian, Armenian)
✅ **Code**: Updated to use string resources
✅ **App Descriptions**: Ready for language-specific JSON fields
✅ **Fallback**: Works with single description if language-specific not provided

---
**Your app is now ready for multilingual support!** 🎉
