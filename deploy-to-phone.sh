#!/usr/bin/env bash
set -euo pipefail

REPO="ijstt/antigravity-mobile-companion"
ARTIFACT_NAME="Antigravity-Companion-Debug-APK"
OUTPUT_DIR="/tmp/antigravity-companion-apk"

echo "=== Antigravity Companion: Automated Mobile Deployment ==="
mkdir -p "$OUTPUT_DIR"

# 1. Fetch GitHub Token
TOKEN=$(git credential fill <<EOF | grep '^password=' | cut -d= -f2
protocol=https
host=github.com
EOF
)

if [ -z "$TOKEN" ]; then
    echo "Error: GitHub credentials not found"
    exit 1
fi

# 2. Get latest artifact download URL
echo "Querying GitHub Actions for latest APK artifact..."
ARTIFACT_URL=$(python3 -c "
import requests
headers = {'Authorization': 'Bearer $TOKEN', 'Accept': 'application/vnd.github+json'}
runs = requests.get('https://api.github.com/repos/$REPO/actions/runs?status=success&per_page=5', headers=headers).json()
for run in runs.get('workflow_runs', []):
    artifacts = requests.get(run['artifacts_url'], headers=headers).json()
    for art in artifacts.get('artifacts', []):
        if art['name'] == '$ARTIFACT_NAME':
            print(art['archive_download_url'])
            exit(0)
exit(1)
" || true)

if [ -z "$ARTIFACT_URL" ]; then
    echo "No successful APK artifact found yet. Please wait for the build to complete."
    exit 1
fi

echo "Downloading APK artifact from GitHub Actions..."
curl -sL -H "Authorization: Bearer $TOKEN" "$ARTIFACT_URL" -o "$OUTPUT_DIR/artifact.zip"
unzip -q -o "$OUTPUT_DIR/artifact.zip" -d "$OUTPUT_DIR"

APK_PATH=$(find "$OUTPUT_DIR" -name "*.apk" | head -n 1)
if [ ! -f "$APK_PATH" ]; then
    echo "Error: app-debug.apk not found in artifact archive"
    exit 1
fi

echo "APK ready: $APK_PATH"

# 3. Deploy via ADB
FIRST_DEVICE=$(adb devices | grep -w "device" | head -n 1 | awk '{print $1}' || true)
if [ -z "$FIRST_DEVICE" ]; then
    echo "No ADB devices currently attached."
    echo "You can manually install the APK from: $APK_PATH"
    exit 0
fi

echo "Deploying APK to device ($FIRST_DEVICE)..."
adb -s "$FIRST_DEVICE" push "$APK_PATH" /data/local/tmp/app-debug.apk
if ! adb -s "$FIRST_DEVICE" shell pm install -r -d /data/local/tmp/app-debug.apk; then
    echo "Reinstalling cleanly..."
    adb -s "$FIRST_DEVICE" shell pm uninstall com.antigravity.companion.debug || true
    adb -s "$FIRST_DEVICE" shell pm install /data/local/tmp/app-debug.apk
fi

adb -s "$FIRST_DEVICE" shell pm grant com.antigravity.companion.debug android.permission.CAMERA 2>/dev/null || true
echo "Launching Antigravity Companion on device..."
adb -s "$FIRST_DEVICE" shell am start -n com.antigravity.companion.debug/com.antigravity.companion.MainActivity
echo "=== Deployment Successful ==="
