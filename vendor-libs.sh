#!/usr/bin/env bash
# Vendors the AndroidX + Kotlin runtime deps that Whammy's Gradle-free build
# needs into libs/ (dexed class jars) and libs/aar/<name>/ (res + manifest for
# the resource-bearing AARs, linked via aapt2 --extra-packages in build.sh).
#
# libs/ is git-ignored, so run this once after a fresh clone (and it is the
# authoritative record of exactly which artifacts/versions the build expects).
# Idempotent — safe to re-run.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p libs libs/aar
G="https://dl.google.com/dl/android/maven2"
MC="https://repo1.maven.org/maven2"

fetch() { curl -fsSL -o "$2" "$1" && echo "  ok $(basename "$2")"; }

# androidx <group/path> <artifact> <version> <res|"">
#   Downloads the .aar (falling back to .jar), drops classes.jar into libs/,
#   and for res-bearing artifacts copies res/ + AndroidManifest.xml into
#   libs/aar/<artifact>/ (build.sh compiles those and adds --extra-packages).
androidx() {
  local path="$1" art="$2" ver="$3" res="$4"
  local base="$G/$path/$art/$ver/$art-$ver" tmp; tmp="$(mktemp -d)"
  if curl -fsSL -o "$tmp/a.aar" "$base.aar" 2>/dev/null; then
    (cd "$tmp" && unzip -oq a.aar)
    cp "$tmp/classes.jar" "libs/$art-$ver.jar"
    if [ "$res" = res ] && [ -d "$tmp/res" ]; then
      rm -rf "libs/aar/$art"; mkdir -p "libs/aar/$art"
      cp -r "$tmp/res" "libs/aar/$art/"
      cp "$tmp/AndroidManifest.xml" "libs/aar/$art/"
    fi
    echo "  ok $art-$ver${res:+ (res)}"
  else
    fetch "$base.jar" "libs/$art-$ver.jar"
  fi
  rm -rf "$tmp"
}

echo "AndroidX (RecyclerView + its transitive deps):"
androidx androidx/annotation                 annotation                   1.7.1  ""
androidx androidx/collection                 collection                   1.1.0  ""
androidx androidx/core                       core                         1.13.1 res
androidx androidx/core                       core-ktx                     1.13.1 ""   # ViewGroupKt — poolingcontainer needs it on detach (crash if missing)
androidx androidx/lifecycle                  lifecycle-common             2.6.2  ""
androidx androidx/lifecycle                  lifecycle-runtime            2.6.2  res
androidx androidx/arch/core                  core-common                  2.2.0  ""
androidx androidx/arch/core                  core-runtime                 2.2.0  ""
androidx androidx/customview                 customview                   1.0.0  ""
androidx androidx/customview                 customview-poolingcontainer  1.0.0  res
androidx androidx/interpolator               interpolator                 1.0.0  ""
androidx androidx/recyclerview               recyclerview                 1.3.2  res

echo "Kotlin stdlib:"
fetch "$MC/org/jetbrains/kotlin/kotlin-stdlib/1.6.21/kotlin-stdlib-1.6.21.jar" libs/kotlin-stdlib-1.6.21.jar

echo "Test-only (JUnit + org.json for ./test.sh):"
fetch "$MC/org/json/json/20240303/json-20240303.jar" libs/json.jar
fetch "$MC/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar" libs/junit-console.jar

echo "Done. libs/ populated."
