#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-/Users/tarikbc/Library/Android/sdk}"
BT="$ANDROID_HOME/build-tools/34.0.0"; PLAT="$ANDROID_HOME/platforms/android-34/android.jar"
PKG=com.tarikbc.whammy; OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/apk"
# 1. compile + link resources -> R.java + compiled resources
find src/main/res -name '*.xml' -o -name '*.png' -o -name '*.ttf' | while read f; do
  "$BT/aapt2" compile "$f" -o "$OUT/obj" >/dev/null; done 2>/dev/null || \
  "$BT/aapt2" compile --dir src/main/res -o "$OUT/obj/res.zip"
"$BT/aapt2" link -o "$OUT/apk/base.ap_" -I "$PLAT" \
  --manifest src/main/AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  $(ls "$OUT/obj"/*.flat 2>/dev/null) $([ -f "$OUT/obj/res.zip" ] && echo "$OUT/obj/res.zip")
# 2. compile java (app sources + generated R + any libs)
CP="$PLAT"; for j in libs/*.jar; do [ -e "$j" ] && CP="$CP:$j"; done
find src/main/java "$OUT/gen" -name '*.java' > "$OUT/srcs.txt"
javac --release 17 -cp "$CP" -d "$OUT/classes" @"$OUT/srcs.txt"
# 3. dex
"$BT/d8" --min-api 26 --output "$OUT" $(find "$OUT/classes" -name '*.class') $(ls libs/*.jar 2>/dev/null || true)
# 4. package dex into the ap_, align, sign
cd "$OUT/apk" && cp base.ap_ whammy.unsigned.apk && cd - >/dev/null
( cd "$OUT" && zip -uj apk/whammy.unsigned.apk classes.dex >/dev/null )
"$BT/zipalign" -f 4 "$OUT/apk/whammy.unsigned.apk" "$OUT/whammy.apk"
"$BT/apksigner" sign --ks keystore/whammy-debug.jks --ks-pass pass:whammy123 \
  --key-pass pass:whammy123 "$OUT/whammy.apk"
echo "built $OUT/whammy.apk"
