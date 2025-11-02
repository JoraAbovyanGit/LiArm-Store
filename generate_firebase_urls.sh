# Firebase Storage Icon URL Generator
# This script helps you generate Firebase Storage URLs for your app icons

# Your Firebase Storage bucket
BUCKET="liarm-store.firebasestorage.app"

# List of app icons you want to upload
ICONS=(
    "telegram.png"
    "whatsapp.png"
    "youtube.png"
    "instagram.png"
    "facebook.png"
    "twitter.png"
    "spotify.png"
    "netflix.png"
    "discord.png"
    "zoom.png"
    "tiktok.png"
    "snapchat.png"
    "linkedin.png"
    "reddit.png"
    "pinterest.png"
)

echo "Firebase Storage URLs for app icons:"
echo "====================================="

for icon in "${ICONS[@]}"; do
    url="https://firebasestorage.googleapis.com/v0/b/$BUCKET/o/icons%2F$icon?alt=media"
    echo "\"$icon\": \"$url\","
done

echo ""
echo "Instructions:"
echo "1. Upload these icon files to Firebase Storage in the 'icons' folder"
echo "2. Copy the URLs above into your apps.json file"
echo "3. Update your API data with these URLs"
