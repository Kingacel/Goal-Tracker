#!/usr/bin/env bash
# One-time setup for building Goal Tracker inside Termux.
set -u

echo "==> Installing build tools (JDK, aapt2, d8, apksigner, zip)..."
pkg update -y
pkg install -y openjdk-17 aapt2 apksigner d8 zip unzip curl || {
  echo "!! A required package failed to install. Re-run, or try: pkg upgrade -y"; exit 1; }
# zipalign is optional; the build works without it (targetSdk 29 needs no alignment)
pkg install -y zipalign 2>/dev/null || echo "(zipalign not available - that's OK)"

JAR="${ANDROID_JAR:-$HOME/android-sdk/android.jar}"
mkdir -p "$(dirname "$JAR")"
if [ -s "$JAR" ]; then
  echo "==> android.jar already present: $JAR"
else
  echo "==> Downloading android.jar (Android API stubs, needed to compile)..."
  ok=0
  for api in 33 32 31 30 29 28; do
    url="https://github.com/Sable/android-platforms/raw/master/android-$api/android.jar"
    echo "   trying API $api ..."
    if curl -fL --retry 2 -o "$JAR.tmp" "$url" && [ "$(stat -c %s "$JAR.tmp")" -gt 1000000 ]; then
      mv "$JAR.tmp" "$JAR"; ok=1; echo "   got API $api"; break
    fi
    rm -f "$JAR.tmp"
  done
  if [ "$ok" -ne 1 ]; then
    echo "!! Could not download android.jar."
    echo "   Copy one from any Android SDK (platforms/android-XX/android.jar, XX >= 26)"
    echo "   to: $JAR   and run ./build.sh again."
    exit 1
  fi
fi
echo "==> Setup complete. Now run:  bash build.sh"
