#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /d/JAVA21 /d/java17_zulu; do
    if [ -x "$candidate/bin/jar" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/jar" ]; then
  echo "JAVA_HOME is not set to a JDK (jar tool not found)" >&2
  exit 1
fi

MVN="${MVN:-mvn}"
if ! command -v "$MVN" >/dev/null 2>&1; then
  if [ -x /d/apache-maven-3.9.9/bin/mvn ]; then
    MVN=/d/apache-maven-3.9.9/bin/mvn
  else
    echo "Maven not found. Set MVN or install Maven." >&2
    exit 1
  fi
fi

BASE="$ROOT/.build-cache/fakeplayer-0.3.19.jar"
BASE_URL="https://github.com/tanyaofei/minecraft-fakeplayer/releases/download/0.3.19/fakeplayer-0.3.19.jar"
BASE_SHA256="46e8289a66e9857403de1af28528f9d32d32f54bc3ccb43a68cd40796292714f"

if [ -f "$BASE" ] && ! printf '%s  %s\n' "$BASE_SHA256" "$BASE" | sha256sum -c - >/dev/null 2>&1; then
  echo "Cached upstream jar is corrupted, downloading again..."
  rm -f "$BASE"
fi

if [ ! -f "$BASE" ]; then
  echo "Downloading upstream NMS bridge jar..."
  mkdir -p "$ROOT/.build-cache"
  curl -L --fail --retry 3 -o "$BASE" "$BASE_URL"
fi

if ! printf '%s  %s\n' "$BASE_SHA256" "$BASE" | sha256sum -c - >/dev/null 2>&1; then
  echo "Checksum mismatch for $BASE" >&2
  rm -f "$BASE"
  exit 1
fi

echo "Building core and api..."
"$MVN" -q -pl fakeplayer-api,fakeplayer-core -am -DskipTests clean package

echo "Merging custom classes with upstream NMS bridges..."
WORK="$ROOT/target/custom"
rm -rf "$WORK"
mkdir -p "$WORK"
(
  cd "$WORK"
  "$JAVA_HOME/bin/jar" xf "$BASE"
)

rm -rf "$WORK/io/github/hello09x/fakeplayer/core"
rm -rf "$WORK/io/github/hello09x/fakeplayer/api"
rm -f "$WORK/plugin.yml" "$WORK/config.yml"
rm -rf "$WORK/message"

cp -R "$ROOT/fakeplayer-api/target/classes/." "$WORK/"
cp -R "$ROOT/fakeplayer-core/target/classes/." "$WORK/"

OUT="$ROOT/target/fakeplayer-custom.jar"
(
  cd "$WORK"
  "$JAVA_HOME/bin/jar" --create --file "$OUT" .
)

echo "Built: $OUT"
