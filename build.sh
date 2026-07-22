#!/usr/bin/env bash
set -euo pipefail
[ -e libs/recyclerview-1.3.2.jar ] || { echo "libs/ not vendored — run ./vendor-libs.sh first" >&2; exit 1; }
export ANDROID_HOME="${ANDROID_HOME:-/Users/tarikbc/Library/Android/sdk}"
BT="$ANDROID_HOME/build-tools/35.0.0"; PLAT="$ANDROID_HOME/platforms/android-34/android.jar"
PKG=com.tarikbc.whammy; OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/apk"
# 1. compile + link resources -> R.java + compiled resources
# Compile the whole res/ dir in one call so a bad resource (e.g. an illegal
# '--' inside an XML comment) FAILS THE BUILD LOUDLY instead of being
# silently dropped — a per-file loop with `2>/dev/null` used to hide these.
"$BT/aapt2" compile --dir src/main/res -o "$OUT/obj/res.zip"

# RecyclerView needs androidx.recyclerview.R (R.attr.recyclerViewStyle /
# R.styleable.RecyclerView.* etc.) to exist and be loadable at runtime —
# its precompiled bytecode reads them unconditionally on every
# construction, XML or programmatic (confirmed by disassembly; see
# vendor-libs.sh). This build never merges AARs by default, so we
# vendor just the res/ + AndroidManifest.xml of every AAR whose classes
# actually reference their own R — found by grepping every vendored jar's
# .class files for "/R$" (javap chokes on some Kotlin classes, so this
# byte-level grep is the reliable check): recyclerview, androidx.core
# (ViewCompat's <clinit> reads androidx.core.R$id), customview-
# poolingcontainer, and lifecycle-runtime — into libs/aar/<name>/,
# compile them alongside the app's own res, and use --extra-packages so
# aapt2 emits real R.java files for each package backed by the same
# merged resource table. No other vendored jar (annotation/collection/
# customview/core-runtime/interpolator/kotlin-stdlib) references its own
# R at all, so their resources are never needed here.
EXTRA_PKGS=""
AAR_RES_ARGS=()
if [ -d libs/aar ]; then
  for aar in libs/aar/*/; do
    name="$(basename "$aar")"
    [ -d "${aar}res" ] || continue
    "$BT/aapt2" compile --dir "${aar}res" -o "$OUT/obj/aar_${name}.zip"
    AAR_RES_ARGS+=("$OUT/obj/aar_${name}.zip")
    pkg="$(grep -o 'package="[^"]*"' "${aar}AndroidManifest.xml" | head -1 | sed 's/package="//;s/"$//')"
    [ -n "$pkg" ] && EXTRA_PKGS="${EXTRA_PKGS:+$EXTRA_PKGS:}$pkg"
  done
fi

"$BT/aapt2" link -o "$OUT/apk/base.ap_" -I "$PLAT" \
  --manifest src/main/AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  ${EXTRA_PKGS:+--extra-packages "$EXTRA_PKGS"} \
  $(ls "$OUT/obj"/*.flat 2>/dev/null) $([ -f "$OUT/obj/res.zip" ] && echo "$OUT/obj/res.zip") \
  "${AAR_RES_ARGS[@]}"
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
# Generate a throwaway debug keystore on first run (keystore/ is git-ignored,
# so a fresh clone won't have one). This is a local debug signing key only.
if [ ! -f keystore/whammy-debug.jks ]; then
  mkdir -p keystore
  keytool -genkeypair -keystore keystore/whammy-debug.jks -storepass whammy123 \
    -keypass whammy123 -alias whammy -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Whammy Debug, O=Whammy" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks keystore/whammy-debug.jks --ks-pass pass:whammy123 \
  --key-pass pass:whammy123 "$OUT/whammy.apk"
echo "built $OUT/whammy.apk"
