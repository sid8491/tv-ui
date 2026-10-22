#!/usr/bin/env bash
# Builds tvkeyremap.apk with the SDK's own tools (no Gradle). Run from this folder.
set -e
export MSYS_NO_PATHCONV=1
SDK="C:/Users/Sid/AppData/Local/Android/Sdk"; BT="$SDK/build-tools/37.0.0"; AJAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"; export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
KS_PASS="${TV_KS_PASS:-$(sed -n "s/^pass=//p" ../keystore.properties 2>/dev/null)}"; [ -n "$KS_PASS" ] || { echo "set TV_KS_PASS or create ../keystore.properties (pass=...)"; exit 1; }
rm -rf out && mkdir -p out/gen out/classes out/dex
"$BT/aapt2.exe" compile --dir res -o out/res.zip
"$BT/aapt2.exe" link -o out/base.apk -I "$AJAR" --manifest AndroidManifest.xml --java out/gen out/res.zip
javac -source 1.8 -target 1.8 -bootclasspath "$AJAR" -d out/classes $(find out/gen src -name '*.java')
"$BT/d8.bat" --release --min-api 29 --lib "$AJAR" --output out/dex $(find out/classes -name '*.class')
python - <<'PY'
import zipfile, shutil
shutil.copy('out/base.apk','out/unaligned.apk')
with zipfile.ZipFile('out/unaligned.apk','a') as z: z.write('out/dex/classes.dex','classes.dex')
PY
"$BT/zipalign.exe" -f 4 out/unaligned.apk out/aligned.apk
[ -f tvkeyremap.jks ] || keytool -genkeypair -keystore tvkeyremap.jks -alias key -keyalg RSA -keysize 2048 -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Sid TV Key Remap"
"$BT/apksigner.bat" sign --ks tvkeyremap.jks --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --out tvkeyremap.apk out/aligned.apk
ls -la tvkeyremap.apk
