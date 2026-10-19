#!/usr/bin/env bash
# Builds GoalTracker.apk from source with: aapt2 -> javac -> d8 -> zip -> (zipalign) -> apksigner
# No Gradle, no Flutter, no Android Studio.
set -euo pipefail
cd "$(dirname "$0")"

APP_NAME="GoalTracker"
MIN_SDK=26
TARGET_SDK=34
ANDROID_JAR="${ANDROID_JAR:-$HOME/android-sdk/android.jar}"
KEYSTORE="${KEYSTORE:-$HOME/.android/goaltracker.keystore}"
B="$PWD/build"

for t in javac keytool aapt2 d8 apksigner zip; do
  command -v "$t" >/dev/null 2>&1 || { echo "Missing tool '$t'. Run: bash setup.sh"; exit 1; }
done
[ -s "$ANDROID_JAR" ] || { echo "android.jar not found at $ANDROID_JAR. Run: bash setup.sh"; exit 1; }

echo "[1/7] Clean"
rm -rf "$B"; mkdir -p "$B/gen" "$B/obj" "$B/dex"

echo "[2/7] Compile resources (aapt2 compile)"
aapt2 compile --dir res -o "$B/res.zip"

echo "[3/7] Link resources + manifest, generate R.java (aapt2 link)"
aapt2 link -o "$B/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  --java "$B/gen" --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --version-code 1 --version-name 1.0 --auto-add-overlay "$B/res.zip"

echo "[4/7] Compile Java (javac)"
javac --release 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$B/obj" \
  $(find src "$B/gen" -name '*.java')

echo "[5/7] Convert to DEX (d8)"
d8 --release --lib "$ANDROID_JAR" --min-api $MIN_SDK --output "$B/dex" \
  $(find "$B/obj" -name '*.class')

echo "[6/7] Package"
cp "$B/base.apk" "$B/unaligned.apk"
( cd "$B/dex" && zip -q -j "$B/unaligned.apk" classes.dex )
if command -v zipalign >/dev/null 2>&1; then
  zipalign -f -p 4 "$B/unaligned.apk" "$B/aligned.apk"
else
  echo "   (zipalign not installed - skipping alignment)"
  cp "$B/unaligned.apk" "$B/aligned.apk"
fi

echo "[7/7] Sign (apksigner)"
if [ ! -f "$KEYSTORE" ]; then
  echo "   creating a local signing key at $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias goaltracker -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Goal Tracker, O=Local, C=US" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/$APP_NAME.apk" "$B/aligned.apk"
rm -f "$PWD/$APP_NAME.apk.idsig"

echo
echo "SUCCESS: $PWD/$APP_NAME.apk  ($(du -h "$APP_NAME.apk" | cut -f1))"

# The system installer can't read Termux's private folder or paths with [brackets],
# so also drop a copy into the public Downloads folder.
APK_FOR_INSTALL="$PWD/$APP_NAME.apk"
if [ -d "$HOME/storage/downloads" ]; then
  if cp -f "$APP_NAME.apk" "$HOME/storage/downloads/$APP_NAME.apk"; then
    APK_FOR_INSTALL="$HOME/storage/downloads/$APP_NAME.apk"
    echo "Copied to: Downloads/$APP_NAME.apk  (install it from the Files app)"
  fi
else
  echo "Tip: run 'termux-setup-storage' once so the APK can be copied to Downloads."
fi

if [ "${1:-}" = "--install" ]; then
  if command -v adb >/dev/null 2>&1 && adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{f=1} END{exit !f}'; then
    echo "Installing over adb (prints the exact error if it fails)..."
    adb install -r "$APK_FOR_INSTALL" || true
  elif command -v termux-open >/dev/null 2>&1; then
    termux-open "$APK_FOR_INSTALL"
  fi
else
  echo "Install: bash build.sh --install    or open Downloads/$APP_NAME.apk in the Files app"
fi
