# How to Migrate Apps Data to Firestore

## Understanding Firebase Services

- **Firebase Storage** (`apps` folder): This is for storing **files** (APKs, icons, images)
- **Firestore Database** (`apps` collection): This is for storing **structured data** (app names, descriptions, URLs, etc.)

You need **BOTH**:
- Storage: Keep your APK files and icons
- Firestore: Store app metadata (name, description, package name, etc.)

## Step 1: Deploy Firestore Rules

```bash
firebase deploy --only firestore:rules
```

## Step 2: Create Apps Collection in Firestore

### Option A: Using Firebase Console (Easiest)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: **liarm-store**
3. Click **Firestore Database** in the left menu
4. Click **Create database** (if not created yet)
   - Choose **Start in test mode** (we'll secure it with rules)
   - Select a location (choose closest to your users)
5. Click **Start collection**
   - Collection ID: `apps`
   - Document ID: Click **Auto-ID** (or use package name)
6. Add your first app document with these fields:

| Field Name | Type | Example Value |
|------------|------|---------------|
| `app_name` | string | "Telegram" |
| `app_icon` | string | "https://firebasestorage.googleapis.com/..." |
| `package_name` | string | "org.telegram.messenger" |
| `download_url` | string | "https://firebasestorage.googleapis.com/..." |
| `app_description` | string | "Fast and secure messaging" |
| `app_version` | string | "12.1.1" |
| `createdAt` | timestamp | (current time) |

7. Click **Save**

### Option B: Using Migration Script (Faster for multiple apps)

I'll create a script to import from your current apps.json

## Step 3: Import Your Current Apps

If you have an `apps.json` file, you can use this Node.js script to import:

```javascript
// migrate-apps.js
const admin = require('firebase-admin');
const fs = require('fs');

// Initialize Firebase Admin
const serviceAccount = require('./path-to-your-service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function migrateApps() {
  // Read your apps.json file
  const appsData = JSON.parse(fs.readFileSync('apps.json', 'utf8'));
  
  const batch = db.batch();
  let count = 0;
  
  for (const app of appsData.apps) {
    const docRef = db.collection('apps').doc(); // Auto-generated ID
    // Or use package name: db.collection('apps').doc(app.package_name);
    
    batch.set(docRef, {
      app_name: app.app_name,
      app_icon: app.app_icon,
      package_name: app.package_name,
      download_url: app.download_url,
      app_description: app.app_description || '',
      app_version: app.app_version || '',
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    count++;
    
    // Firestore batches are limited to 500 operations
    if (count % 500 === 0) {
      await batch.commit();
      console.log(`Imported ${count} apps...`);
    }
  }
  
  // Commit remaining
  if (count % 500 !== 0) {
    await batch.commit();
  }
  
  console.log(`✅ Successfully imported ${count} apps to Firestore!`);
}

migrateApps().catch(console.error);
```

## Step 4: Verify Data Structure

Your Firestore collection should look like this:

```
apps (collection)
├── [auto-id-1] (document)
│   ├── app_name: "Telegram"
│   ├── app_icon: "https://..."
│   ├── package_name: "org.telegram.messenger"
│   ├── download_url: "https://..."
│   ├── app_description: "Fast and secure messaging"
│   ├── app_version: "12.1.1"
│   └── createdAt: [timestamp]
├── [auto-id-2] (document)
│   └── ...
```

## Step 5: Test the App

1. Run your Android app
2. It should now fetch from Firestore instead of GitHub
3. Check logs for: "Successfully fetched X apps from Firestore"

## Troubleshooting

### "Permission denied" error
- Make sure you deployed the rules: `firebase deploy --only firestore:rules`
- Check that rules allow public read access

### "Collection not found"
- Make sure you created the `apps` collection in Firestore
- Check the collection name is exactly `apps` (lowercase)

### Apps not showing
- Check Firestore console to see if data exists
- Check app logs for error messages
- Verify field names match exactly (case-sensitive)

## Quick Manual Import (Small number of apps)

If you only have a few apps, you can manually add them:

1. Firebase Console → Firestore Database
2. Click **Start collection** → Name: `apps`
3. For each app:
   - Click **Add document**
   - Use **Auto-ID** for document ID
   - Add all fields as shown in the table above
   - Click **Save**

