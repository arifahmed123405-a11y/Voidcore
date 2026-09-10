#!/usr/bin/env bash
# Run from any directory after installing JDK 17 and Android SDK 35.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v javac >/dev/null || { echo 'Missing full JDK 17: javac is unavailable.' >&2; exit 1; }
[[ "$(javac -version 2>&1)" == javac\ 17.* ]] || { echo 'Set JAVA_HOME to JDK 17.' >&2; exit 1; }
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" ]] || { echo 'Set ANDROID_HOME to your Android SDK directory.' >&2; exit 1; }
[[ -f "$sdk/platforms/android-35/android.jar" && -x "$sdk/build-tools/35.0.0/aapt2" ]] || {
 echo 'Install platforms;android-35 and build-tools;35.0.0 using sdkmanager.' >&2; exit 1;
}
export ANDROID_HOME="$sdk"
mkdir -p build/verification
# pipefail preserves failures, and --continue allows independent checks to run.
sh ./gradlew --no-daemon --console=plain --stacktrace --continue \
 testDebugUnitTest lintDebug compileDebugKotlin assembleDebug 2>&1 | tee build/verification/gradle.log
apk="$PWD/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$apk" ]] || { echo 'Gradle returned success but the debug APK is missing.' >&2; exit 1; }
"$sdk/build-tools/35.0.0/apksigner" verify "$apk"
printf 'APK: %s\nBytes: %s\n' "$apk" "$(wc -c < "$apk" | tr -d ' ')"
sha256sum "$apk"
