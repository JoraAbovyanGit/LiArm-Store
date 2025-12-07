/**
 * Migration script to import apps from apps.json to Firestore
 * 
 * Usage:
 * 1. Get your service account key from Firebase Console:
 *    Project Settings → Service Accounts → Generate New Private Key
 * 2. Save it as 'service-account-key.json' in project root
 * 3. Place your apps.json file in project root
 * 4. Run: node migrate-apps.js
 */

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Check if service account key exists
const serviceAccountPath = path.join(__dirname, 'service-account-key.json');
if (!fs.existsSync(serviceAccountPath)) {
  console.error('❌ Error: service-account-key.json not found!');
  console.log('📝 Steps to get it:');
  console.log('   1. Go to Firebase Console → Project Settings → Service Accounts');
  console.log('   2. Click "Generate New Private Key"');
  console.log('   3. Save as "service-account-key.json" in project root');
  process.exit(1);
}

// Check if apps.json exists
const appsJsonPath = path.join(__dirname, 'apps.json');
if (!fs.existsSync(appsJsonPath)) {
  console.error('❌ Error: apps.json not found!');
  console.log('📝 Please place your apps.json file in the project root');
  process.exit(1);
}

// Initialize Firebase Admin
const serviceAccount = require(serviceAccountPath);
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function migrateApps() {
  try {
    console.log('📖 Reading apps.json...');
    const appsData = JSON.parse(fs.readFileSync(appsJsonPath, 'utf8'));
    
    if (!appsData.apps || !Array.isArray(appsData.apps)) {
      throw new Error('Invalid apps.json format. Expected { "apps": [...] }');
    }
    
    console.log(`📦 Found ${appsData.apps.length} apps to import`);
    
    // Test Firestore connection first
    console.log('🔍 Testing Firestore connection...');
    try {
      // Try to read from a test collection to verify database exists
      await db.collection('_test').limit(1).get();
      console.log('✅ Firestore connection successful\n');
    } catch (testError) {
      if (testError.code === 5 || testError.message.includes('NOT_FOUND')) {
        console.error('\n❌ ERROR: Firestore database not found!');
        console.log('\n📝 Please create Firestore database first:');
        console.log('   1. Go to: https://console.firebase.google.com/');
        console.log('   2. Select your project: liarm-store');
        console.log('   3. Click "Firestore Database" in left menu');
        console.log('   4. Click "Create database"');
        console.log('   5. Choose "Start in test mode"');
        console.log('   6. Select a location (choose closest to your users)');
        console.log('   7. Click "Enable"');
        console.log('\n   Then run this script again.\n');
        process.exit(1);
      } else {
        throw testError;
      }
    }
    
    console.log('🚀 Starting migration...\n');
    
    let successCount = 0;
    let errorCount = 0;
    const errors = [];
    
    // Process in batches of 500 (Firestore limit)
    for (let i = 0; i < appsData.apps.length; i += 500) {
      const batch = db.batch();
      const batchApps = appsData.apps.slice(i, i + 500);
      let batchErrors = 0;
      
      for (const app of batchApps) {
        try {
          // Validate required fields
          if (!app.app_name || !app.package_name || !app.download_url) {
            throw new Error(`Missing required fields for app: ${app.app_name || 'unknown'}`);
          }
          
          // Use package name as document ID (unique identifier)
          const docRef = db.collection('apps').doc(app.package_name);
          
          // Preserve all fields from apps.json
          const appData = {
            app_name: app.app_name,
            app_icon: app.app_icon || '',
            package_name: app.package_name,
            download_url: app.download_url,
            app_description: app.app_description || '',
            app_version: app.app_version || '',
            createdAt: admin.firestore.FieldValue.serverTimestamp()
          };
          
          // Add optional fields if they exist
          if (app.category) appData.category = app.category;
          if (app.app_description_en) appData.app_description_en = app.app_description_en;
          if (app.app_description_ru) appData.app_description_ru = app.app_description_ru;
          if (app.app_description_hy) appData.app_description_hy = app.app_description_hy;
          
          batch.set(docRef, appData, { merge: true }); // merge: true prevents overwriting existing data
          
        } catch (error) {
          batchErrors++;
          errors.push({ app: app.app_name || app.package_name, error: error.message });
          console.error(`❌ Error processing ${app.app_name || app.package_name}: ${error.message}`);
        }
      }
      
      // Commit batch
      try {
        await batch.commit();
        const batchSuccess = batchApps.length - batchErrors;
        successCount += batchSuccess;
        errorCount += batchErrors;
        console.log(`✅ Imported batch ${Math.floor(i / 500) + 1}: ${batchSuccess} apps (${batchErrors} errors)`);
      } catch (error) {
        console.error(`❌ Batch commit failed: ${error.code || error.message}`);
        if (error.code === 5 || error.message.includes('NOT_FOUND')) {
          console.error('\n❌ Firestore database not found!');
          console.log('📝 Please create Firestore database in Firebase Console first.');
          console.log('   See instructions above.\n');
          process.exit(1);
        }
        errorCount += batchApps.length;
        errors.push({ app: 'batch', error: error.message });
      }
    }
    
    console.log('\n📊 Migration Summary:');
    console.log(`   ✅ Success: ${successCount} apps`);
    console.log(`   ❌ Errors: ${errorCount} apps`);
    
    if (errors.length > 0) {
      console.log('\n⚠️  Errors encountered:');
      errors.forEach(({ app, error }) => {
        console.log(`   - ${app}: ${error}`);
      });
    }
    
    if (successCount > 0) {
      console.log('\n🎉 Migration completed! Your apps are now in Firestore.');
      console.log('📱 Test your app - it should now fetch from Firestore instead of GitHub.');
    }
    
    process.exit(errorCount > 0 ? 1 : 0);
  } catch (error) {
    console.error('❌ Migration failed:', error.message);
    console.error(error.stack);
    process.exit(1);
  }
}

// Run migration
migrateApps();

