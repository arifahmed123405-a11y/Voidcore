#!/usr/bin/env sh
# Pinned Gradle bootstrap; validates distribution against Gradle's published SHA-256.
set -eu
VERSION=8.9
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/void-bootstrap/$VERSION"
BIN="$CACHE/gradle-$VERSION/bin/gradle"
if [ ! -x "$BIN" ]; then
 command -v curl >/dev/null || { echo "Install curl or run Gradle 8.9 through Android Studio."; exit 1; }
 command -v unzip >/dev/null || { echo "Install unzip."; exit 1; }
 mkdir -p "$CACHE"
 ARCHIVE="$CACHE/distribution.zip"
 BASE="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
 curl --fail --location --retry 2 "$BASE" --output "$ARCHIVE"
 curl --fail --location --retry 2 "$BASE.sha256" --output "$CACHE/expected.sha256"
 EXPECTED=$(tr -d '\r\n ' < "$CACHE/expected.sha256")
 if command -v sha256sum >/dev/null; then ACTUAL=$(sha256sum "$ARCHIVE" | cut -d ' ' -f 1)
 else ACTUAL=$(shasum -a 256 "$ARCHIVE" | cut -d ' ' -f 1); fi
 [ "$EXPECTED" = "$ACTUAL" ] || { echo "Gradle checksum mismatch"; rm -f "$ARCHIVE"; exit 1; }
 unzip -q -o "$ARCHIVE" -d "$CACHE"
 rm "$ARCHIVE"
fi
exec "$BIN" "$@"
