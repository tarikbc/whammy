#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/test
CP="libs/junit-console.jar:libs/json.jar"
# only compile Android-independent sources + tests
javac --release 17 -cp "$CP" -d build/test \
  src/main/java/com/tarikbc/whammy/Chart.java \
  src/main/java/com/tarikbc/whammy/EncoreApi.java \
  src/main/java/com/tarikbc/whammy/SongStore.java \
  $(find test/java -name '*.java')
java -jar libs/junit-console.jar execute -cp "build/test:libs/json.jar" --scan-classpath --details=tree
