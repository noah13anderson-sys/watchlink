#!/usr/bin/env bash
# Builds WatchLink.apk without Gradle. Needs:
#   JDK 17   (e.g. sudo pacman -S jdk17-openjdk)
#   Android SDK with build-tools;34.0.0 and platforms;android-34 in $ANDROID_HOME
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/sdk}"
BT="$ANDROID_HOME/build-tools/34.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
rm -rf build && mkdir -p build/classes build/dex build/gen

# resources (home screen widget) first: linking writes R.java for the code
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" build/res.zip --java build/gen -o build/unsigned.apk
javac --release 11 -classpath "$JAR" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --min-api 29 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
python3 -c "import zipfile; zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED).write('build/dex/classes.dex','classes.dex')"
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

KS=debug.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=WatchLink Debug"
"$BT/apksigner" sign --ks $KS --ks-pass pass:android --out WatchLink.apk build/aligned.apk
echo "Built $(pwd)/WatchLink.apk"
echo "Install with: adb install -r WatchLink.apk"
