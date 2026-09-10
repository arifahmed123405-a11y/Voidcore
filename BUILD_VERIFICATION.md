# Build verification attempt — Phase 0 + Phase 1

No APK was produced. No Kotlin compiler diagnostics, JUnit results, Android lint results, or successful assembly are available. Static checks are not a substitute for these gates.

## Observed environment failures

- OpenJDK **runtime** 17.0.20 is installed. `javac` is absent: a full JDK 17 is missing.
- `apt-get install -y openjdk-17-jdk-headless` exited 100: `Unable to locate package openjdk-17-jdk-headless`.
- `apt-get update` exited 100 with `setgroups ... Operation not permitted`, `setegid/seteuid ... Invalid argument`, and HTTPS method exit 112. The package index could not be refreshed.
- Gradle 8.9 is not installed. Both the initial download probe and actual bootstrap/build attempt ended at the execution boundary with `network approval was cancelled before a decision was returned`. The official distribution endpoint redirects to GitHub. This is a dependency acquisition failure, not a Gradle compilation error.
- Google SDK downloads were reachable. Command-line tools 12.0 were downloaded, checked against Google's repository checksum, extracted, and `sdkmanager --version` returned `12.0`. SDK installation then exited 0. Platform 35 (`android.jar`), Build Tools 35.0.0 (`aapt2`) and Platform Tools exist under `/opt/voidcore-android-sdk`. SDK metadata confirms API 35 and Build Tools revision 35.0.0. These successful installs do not resolve the missing JDK compiler or Gradle distribution.

Actual requested build attempt (bounded to avoid an indefinite download):

```sh
timeout 45 sh ./gradlew --no-daemon --console=plain --continue testDebugUnitTest lintDebug compileDebugKotlin assembleDebug
```

Gradle did not start, so all four tasks are **NOT RUN**. The new `bash scripts/verify-debug.sh` preflight exited 1 with `Missing full JDK 17: javac is unavailable.` No compile errors can honestly be identified or claimed fixed from this attempt. Existing application code and build version pins are unchanged.

## Exact Linux setup and build commands

Run on a Linux x86-64 machine with package-install privileges and outbound access to Ubuntu package repositories, Google SDK/Maven, Maven Central, Gradle distributions and its GitHub redirect. These commands assume a fresh dedicated SDK folder and the extracted project in the current directory. License acceptance is interactive.

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk curl unzip
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version

export ANDROID_HOME="$HOME/Android/voidcore-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
curl --fail --location --retry 2   https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip   --output /tmp/voidcore-commandlinetools.zip
printf '%s  %s\n'   2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258   /tmp/voidcore-commandlinetools.zip | sha256sum --check
unzip /tmp/voidcore-commandlinetools.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/12.0"
export PATH="$ANDROID_HOME/cmdline-tools/12.0/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME"   "platforms;android-35" "build-tools;35.0.0" "platform-tools"

cd VoidCore
# Bootstrap verifies Gradle 8.9's published distribution checksum.
sh ./gradlew wrapper --gradle-version 8.9 --distribution-type bin
bash scripts/verify-debug.sh
# Only after that script succeeds:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.voidcore/.app.MainActivity
```

`verify-debug.sh` runs unit tests, lint, explicit debug Kotlin compilation and assembleDebug with failure propagation, records the Gradle log, checks APK existence, verifies its signature and prints absolute path, byte size and SHA-256. No failure suppression or lint baseline is added. The SDK download hash above was calculated from the archive checked against Google's repository checksum during this attempt.

Expected output location, **not an existing artifact here**: `VoidCore/app/build/outputs/apk/debug/app-debug.apk`.

Reports after successful execution:

- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint: `app/build/reports/lint-results-debug.html`
- Combined build log: `build/verification/gradle.log`

## Android Studio alternative

Install SDK Platform 35 and Build Tools 35.0.0 in SDK Manager. Select JDK 17 as Gradle JDK, set `ANDROID_HOME` for the terminal (or use `local.properties` with `sdk.dir` for Studio), generate the wrapper as described in README, and open the project. In the terminal, run `bash scripts/verify-debug.sh` with JDK/SDK environment variables set, or run `./gradlew testDebugUnitTest lintDebug compileDebugKotlin assembleDebug`. Choose the app run configuration and an API 26+ device. These instructions still require actual execution outside this restricted environment.

## Preserved scope

Minimum Android: **8.0 / API 26**. Target/compile Android: **15 / API 35**. Build Tools: **35.0.0**. Gradle: **8.9**; AGP: **8.7.3**; Kotlin/Compose plugin: **2.0.21**; JDK requirement: **17**.

All six presets remain: **Neutral Core, Void, Architect, Spectral, Titan, Omega**. All eleven canonical states, four presence modes, Visual Lab, Home, Conversation/Agent Workspace previews, Reduce Motion, Reduce Transparency, visual quality settings and Diagnostics remain in the source. No real AI/device functionality or Phase 2 changes were added.

Physical-device verification remains necessary for installation/startup, all choreography and interrupted sequences, mode morphing/dismissal, lifecycle/rotation, TalkBack and large text, transparency/motion/quality settings, frame timing, GPU overdraw, memory, thermals and battery. Even a successful future APK build will not establish those results.

## Phase 2 verification note
The Phase 2 source passes the repository static audit, including permission, state, provider-boundary, Room-schema and security checks. This execution environment could not resolve `services.gradle.org`, so Gradle dependency resolution and Android compilation were not run here. The existing GitHub Actions workflow remains the authoritative compile/test/lint/APK build path.

## CI workflow license fix

The GitHub Actions workflow intentionally does not use `android-actions/setup-android@v3`.
That action performs a blanket `sdkmanager --licenses` pass which can fail on unrelated
Google TV add-on licenses before the VoidCore Gradle build starts. The workflow now uses
the Android SDK already present on `ubuntu-latest` and installs only platform-tools,
Android 35, and build-tools 35.0.0 with non-interactive acceptance for those requested
packages.
