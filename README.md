# Void Core — Phase 1 visual presence

Native Kotlin + Jetpack Compose. This project extends the audited Phase 0 architecture with visual choreography only. No intelligence or device-control features were added. The six voice presets and all Phase 0 domain contracts remain intact.

**Verification:** static source/configuration checks passed. Android Studio sync, Kotlin/Room compilation, the 12 JUnit tests, APK installation and actual Compose rendering have **not** been verified in this environment. The build attempt could not obtain Gradle or a full JDK 17 (only the Java 17 runtime is installed). See BUILD_VERIFICATION.md for the observed errors and reproducible Linux commands. No screenshot, benchmark, 60 FPS result, working APK or production readiness is claimed. The deliverable is the source project.

The latest attempted build and Linux setup instructions are in [BUILD_VERIFICATION.md](BUILD_VERIFICATION.md).

## Build and run exactly

Prerequisites: JDK 17, Android Studio supporting AGP 8.7, an internet connection for initial dependencies, Android SDK Platform 35, Build Tools 35.0.0 and Platform Tools. Minimum device is Android 8.0/API 26; Android 10 is supported by the declared minimum. Compile/target SDK is 35. This pin is for the foundation and is not a claim of current Play submission compliance.

1. Extract this archive and keep the `VoidCore` folder intact.
2. In Android Studio's SDK Manager, install Android SDK Platform 35, Android SDK Build-Tools 35.0.0, and Android SDK Platform-Tools. Set Gradle JDK to JDK 17.
3. Create `VoidCore/local.properties` with the SDK path, using forward slashes even on Windows:
   ```properties
   sdk.dir=/absolute/path/to/Android/sdk
   ```
   Typical Windows example: `sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk`.
4. Open a terminal in `VoidCore`. Generate the standard Gradle wrapper:
   - macOS/Linux: `sh ./gradlew wrapper --gradle-version 8.9 --distribution-type bin`
   - Windows PowerShell: `.\gradlew.bat wrapper --gradle-version 8.9 --distribution-type bin`

   The supplied launchers are transparent bootstrap scripts, **not** an official wrapper JAR. They download Gradle 8.9 and verify its ZIP against the SHA-256 published by Gradle. The command above replaces the launchers with the official wrapper and creates `gradle/wrapper/`. macOS/Linux needs curl and unzip. Alternatively install Gradle 8.9 yourself and run `gradle wrapper --gradle-version 8.9 --distribution-type bin` in this folder.
5. Open the `VoidCore` folder in Android Studio, allow Gradle sync, select the `app` run configuration, select an API 26+ emulator or USB-debugging phone, then Run.
6. To build/test from a terminal:
   ```sh
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
   On Windows use `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug`.
7. Install on a connected device:
   ```sh
   ./gradlew installDebug
   adb shell am start -n dev.voidcore/.app.MainActivity
   ```
   Windows uses `.\gradlew.bat installDebug` for the first command. APK location: `app/build/outputs/apk/debug/app-debug.apk`.

Optional CI: `.github/workflows/android.yml` runs tests/lint/assembly and uploads a debug APK when placed in your own GitHub repository. It has not been executed as part of this delivery.

## Launch and test the visual prototype

1. **Home** opens with the central Void Core in SLEEPING. The primary input supports typing, a Mic button and a mock attachment toggle. Mic selects LISTENING without recording. Send produces THINKING → SPEAKING → SUCCESS with a clearly labeled mock response.
2. Open **Visual lab** from Home (or **You → Diagnostics**). On a fresh session the fake phone surface has no assistant manifestation. **Invoke** brings in edge light, assembles the core and enters LISTENING. All content behind it is drawn in the app; it is not your real phone screen.
3. **Play full sequence** runs SLEEPING → INVOKING → LISTENING → THINKING → PLANNING → ACTING → WAITING_FOR_USER → SPEAKING → SUCCESS → DISMISSING → SLEEPING. It takes about 20 seconds. The surface hides at the end. ERROR_RECOVERY is available in the manual state selector.
4. **Dismiss** contracts the shell, collapses the strands into a filament and retracts it toward the selected edge. **Fast swipe-away** runs the abbreviated version; a horizontal swipe of at least 48 dp on the manifestation triggers the same preview. Buttons provide an accessible alternative to the gesture.
5. Select **Full Presence / Compact / Capsule / Edge Agent**, or use **Play morph → and back**. One renderer node changes its size, center, core placement and shell proportions. Dock left/right changes the eventual edge. Capsule status strings are explicitly marked visual demos.
6. Toggle **Reduce Motion**, **Reduce Transparency**, and **Low / Balanced / High** quality. These are session settings retained by the ViewModel across activity recreation; they are not persisted after process death and do not automatically follow system accessibility preferences yet.
7. **Conversation** presents a large core, clean user text, a minimal mock response and the same command surface. No chat history is stored.
8. **Agent workspace** opens in ACTING with the scripted goal “Send latest PDF to Ahmed.” Find file/Resolve Ahmed are marked with sample checkmarks; Open WhatsApp is the active sample step. Pause/Resume, Stop and Take Control alter visual state only. Reset preview becomes available after stopping. No file, contact or app is accessed.

Manual state selection cancels pending state/mode demos. Starting a new state sequence cancels the old one. Stop sequence cancels timed state changes and mode playback; the selected state's ambient visual may continue. Demos stop on leaving a screen or backgrounding the Activity. Ordinary Android coroutine cancellation is preserved. None of the demos submits a ToolRequest or touches the executor.

## Focused living-presence refinement

This pass keeps the upgraded layout, navigation and renderer seam. Invocation now begins as an edge fragment; the core travels inward as the shell assembles. Sleeping is darker and slower. Thinking compresses internal paths, waiting gives one 2.4-second pulse, recovery loses alignment and slows into reformation, and success briefly aligns before relaxing. Listening/speaking retain distinct mock envelopes. Dismissal retains contraction → filament → edge retraction. Sparse warm/pink accents appear only within the restrained blue/violet field.

Home's command surface receives a faint core-colored connecting filament. Bottom navigation now floats on the shared panel material with outlined active destinations and retained 48 dp targets/tab semantics.

In **Visual Lab**, the **Spatial behavior** controls select:

- **Phone surface**: ordinary in-app preview.
- **Mock keyboard avoidance**: compact core is bounded above a drawn keyboard. The fake keyboard is not an IME.
- **Mock fullscreen app**: same renderer shrinks to Edge Agent over neutral content.
- **Mock lock screen**: lower-third compact presence, a fixed clock and privacy-safe text; canonical WAITING_FOR_USER represents the mock biometric/permission wait. **Mock unlock & expand** grows that same presence before LISTENING, with no authentication API.
- **Directional action target**: ACTING drives a thin curved filament and a local control outline with a brief pulse; no control is activated.
- **Play safe edge → keyboard → fullscreen → attention**: cancellable timed UI-only sequence. Existing left/right docking controls work in these scenes.

Scene selection is scenery configuration, not another assistant state. The same presence controller and canonical assistant engine still own modes and semantics. Manual state selection, scene changes and stopping demos cancel pending spatial sequences. The lock-screen preview does not display command text, task details or personal context.

Visual tiers are **High / Balanced / Low** (the internal `STANDARD` identifier is retained). Ambient-clock updates are limited to at most 45 per second in Balanced and 30 in Low, quantized to the display's frame clock; High follows display frames. State/mode transitions retain Compose's animation clock. These limits are not measured FPS guarantees. Reduce Motion disables the ambient clock and spatial travel. Reduce Transparency keeps solid scene/panel treatment. No real Android screen/keyboard/lock state is queried.

## Phase 1 visual overhaul

The supplied concept board informed the lighting, glass depth, blue/ice/lavender energy treatment and layered composition. It is not embedded as a background image. Everything is native Compose drawing and layout, with the existing VoidCore branding and routes.

- **Depth-split ribbon field:** cached projected curves are split into rear and foreground paths around the asymmetric singularity. Perspective, tilted orbital planes, restrained bloom strokes and localized highlights provide depth. Internal geometry remains faintly visible through the dark center.
- **State distinction:** listening widens the field; speaking modulates ribbon inclinations and volume; planning exposes a branching web; thinking advances internal paths; acting stretches projection; waiting adds a paired open aperture. Invocation spreads/reassembles ribbon planes, recovery separates/reforms them, and completion aligns them before the existing filament dismissal.
- **Environmental scene:** cached blue-black radial illumination, one faint field line, sparse fixed dust and a reflected light pool behind/below the core. Environmental dust does not animate independently.
- **Home:** revised typography and framing, a hero core above a reflected floor, an inset command panel and one layered task preview.
- **Conversation:** a larger 390 dp core stage, clean command typography and a shared command surface.
- **Agent Workspace:** framed core, connected step treatment and an inset active step; progress remains scripted.
- **Visual Lab:** the same core inside the fake phone scene, refined capsule borders, a recognizable split Edge Agent rail, grouped comfort controls, and a slower critically damped presence morph.
- **Shared surfaces:** opaque-backed gradient panels, restrained shadows, reflected top edges, refined chips and command controls. Reduce Transparency replaces these with solid treatments and removes environmental fog/reflections. There is no new state source or independent animation clock.

The latest overhaul is source-verified only. No rendered Android screenshot, successful Android compilation or device performance measurement is claimed.

## Visual systems implemented in source

- **Central void:** an asymmetric dark, spline-defined occluding shape. Small parallax offsets and offset edge contours give the impression of light bending around it.
- **Liquid shell:** layered, offset spline contours, restrained gradient highlights and a solid dark interior. Depth/refraction is a 2.5D illusion, not physically simulated glass.
- **Energy veil:** continuous meridians and open curved filaments, with separate internal activity, branching, projection, attention and amplitude weights.
- **Particles:** bounded, sparse samples primarily during invocation, planning, acting, success, recovery and dismissal. They are absent from steady sleep/listening/thinking/waiting/speaking once transitions settle.
- **Morphing:** canonical state drives an updateTransition; shape, luminance, topology weights and tint interpolate. Entry/completion/dismissal envelopes supply transient choreography. Mode geometry uses critically damped springs without bounce.
- **Surfaces:** spacious Presence Home, Conversation shell, scripted Agent Workspace and the fake-phone invocation surface.
- **Audio/haptics:** SharedFlow events carry cue type, canonical revision and haptic intent. Wake, listening, action, confirmation, success, warning and dismissal hooks exist. No sink is installed and no sound or vibration occurs.

| Assistant state | Choreography |
|---|---|
| SLEEPING | Very slow internal phase, faint energy, settled shell |
| INVOKING | Dispersed filaments/particles converge; shell assembles; edge light appears in the phone preview |
| LISTENING | Slightly open shell responds to a deterministic mock listening envelope |
| THINKING | Calm outer boundary; quicker interior meridians converge around the void |
| PLANNING | Temporary branching paths and small junctions, distinct from thinking |
| ACTING | Tightened center and a few outward energy strokes |
| WAITING_FOR_USER | Restrained attentive pulse and warm neutral accent |
| SPEAKING | Rhythmic volumetric deformation from a mock speech envelope; no waveform |
| SUCCESS | Near-symmetric outer-shell alignment and one completion pulse; the central void stays asymmetric |
| ERROR_RECOVERY | Temporary separation and distortion followed by reformation over 2.4 seconds |
| DISMISSING | Settle → contract → filament → edge retraction → disappearance |

Reduce Motion fixes scale and parallax, stops the continuous clock, removes particles, avoids large invocation/dismissal movement and changes modes without spatial animation. Brief opacity/color transitions, static branching and state text preserve meaning. Reduce Transparency uses solid shell/surface treatments and omits diffuse glow, particles and translucent refraction accents. Transition opacity remains available for appearance/disappearance.

## Performance considerations

| Quality | Contour/ribbon samples | Internal strands | Maximum particles | Shell layers | Projected ribbons |
|---|---:|---:|---:|---:|---:|
| Low | 48 | 4 | 8 | 2 | 4 |
| Balanced (default) | 80 | 7 | 16 | 4 | 7 |
| High | 128 | 10 | 32 | 6 | 12 |

Spline paths, projected ribbon paths and point arrays are reused. High quality adds depth passes and glow strokes, increasing CPU geometry work and GPU overdraw; prefer Low on older phones until profiling is complete. Low also omits panel shadows and the widest ribbon bloom stroke. Trigonometric base samples are precomputed. The animation clock is read in Canvas draw code rather than driving the entire screen's layout. No full 3D engine, bitmap blur pass, full-screen RenderEffect or shader requiring Android 12+ is used. Gradients approximate soft scattering. Some brushes and offsets are still constructed per draw; GPU overdraw and allocation behavior require profiling.

Ambient-clock updates follow the selected tier and are gated by Activity lifecycle and visual routes, and disabled in Reduce Motion. Diagnostics polls at 8 Hz, while drawing follows the display frame clock. The Home timeline can still tick while its core is scrolled out of view; there is no per-item viewport visibility optimization yet. Targeting 60 FPS is a design goal, not a measured result. Test Low first on weaker phones and profile a release build before tuning budgets.

The graphics approach follows Compose's custom Canvas/DrawScope surface. [Android graphics documentation](https://developer.android.com/develop/ui/compose/graphics/draw/overview).

## Architecture and files

One `app` Gradle module retains all fifteen packages. The Application still constructs the only production AssistantStateEngine. The Activity-scoped VisualDemoViewModel is injected with that engine and the existing presence controller; it never constructs another semantic source. Its data contains UI flags/text/settings, not an alternate assistant state.

```text
app/src/main/java/dev/voidcore/
  app/
    MainActivity.kt             navigation and shared visual composition
    VisualDemoViewModel.kt      cancellable mock sequences; feedback events
    VisualScreens.kt            Home, Conversation, Workspace, Diagnostics
    Diagnostics.kt              honest implementation/availability statuses
  coreui/
    AiCore.kt                  replaceable renderer, core framing and spline geometry
    DimensionalField.kt        cached perspective ribbons and depth-separated passes
    SpatialSurfaces.kt         shared scene lighting and accessible panel treatment
    CoreRuntime.kt             shared transition/clock/envelopes
    VisualModels.kt            geometry targets, quality and accessibility settings
    PresenceStage.kt           fake phone and one morphing presence element
    CommandSurface.kt          text, mic and attachment visual controls
    DesignSystem.kt            existing tokens/components; larger chip targets
  voiceengine/
    VisualFeedback.kt          cue/haptic interfaces, no output adapter
  ...                          audited Phase 0 packages retained
app/src/test/java/dev/voidcore/
  FoundationTest.kt             eight Phase 0 invariant tests
  VisualDemoTest.kt             four cancellation/sequence/state-isolation tests
scripts/static-audit.py         static checks; no Android build required
```

## Still mocked / not available

All speech amplitudes, transcripts, task progress, attachments, provider samples and cross-app status text are synthetic. No wake word, microphone capture, STT, TTS, AI API, accessibility service, notification listener, real overlay, camera, browser, call, WhatsApp or file workflow is implemented. No permissions or network dependencies were added. The existing deny-all security gate and empty Android adapter registry are unchanged. Room remains a lazy foundation; no new personal-data persistence or credentials are introduced.

Diagnostics reports state, presence mode, state phase/progress, both mock amplitudes, visual settings, renderer status and module availability. IMPLEMENTED means source exists, not that an Android/device test passed. Interfaces without a runtime adapter remain NOT AVAILABLE. Cue hooks do not imply working sound/haptics.

## Verification still required

Run `python3 scripts/static-audit.py` for the repeatable source/configuration check. It passed here, including Room query spelling against an entity-derived in-memory SQLite schema. It does not parse/compile Kotlin, resolve Maven artifacts or render Compose.

In Android Studio, run sync, KSP/schema generation, `testDebugUnitTest`, `lintDebug` and `assembleDebug`. The 12 JUnit tests have not run here. On a physical API 26+ device, verify all states, interrupted/repeated invocations, both dismissals, rapid mode changes, left/right docking, every visual setting, screen rotation/backgrounding, keyboard/IME behavior, TalkBack, large fonts and small screens. Measure frame times, allocations, battery use and thermal behavior. Confirm that no permission prompts, files, apps or network operations occur.

Phase 1 visual work is preserved. Phase 2 runtime additions are documented below.

## Phase 2 — voice + free AI routing (ChatGPT implementation)

Phase 2 adds real in-app microphone speech recognition through Android `SpeechRecognizer`, Android `TextToSpeech`, a local task classifier, short-term conversation context, secure user-managed provider keys, and two interchangeable remote provider adapters (Gemini and Groq). The Android action executor remains deny-by-default until Phase 3.

### Configure a free provider
Open **You → AI Providers** and paste a Gemini API key and/or Groq API key. Keys are encrypted with Android Keystore and are not stored in Room or source control. The router tries Gemini first, then Groq. Simple device-action phrases and voice-profile commands are classified locally and do not consume an AI request.

### Voice Lab
Open **You → Voice Lab**. Android TTS currently applies pitch and speaking speed. All advanced synthetic controls remain in `VoiceProfile` for the planned DSP layer; they are not falsely reported as active. Custom voice profiles can be saved locally in Room.

### Phase 2 boundaries
No real overlay, AccessibilityService, notification listener, calls, browser automation, file workflows, camera vision, or Android action execution is enabled. Wake-word detection remains an interface/future capability; manual mic activation is real.

## Phase 3 — native Android actions + accessibility foundation

Phase 3 keeps the Phase 2 voice/free-provider runtime and adds a validated local action path. Safe commands can now execute through Android APIs for app launching, flashlight, media controls, volume, brightness (after the user grants Modify system settings), timers/alarms, clipboard, and a small explicit AccessibilityService command set. Every local action still crosses the security gate and audit log before an adapter runs. Calls, messaging, files, notification intelligence, outside-app overlay, vision and autonomous cross-app workflows remain later phases.

The visual renderer was also deepened toward the original concept with chromatic blue/violet/magenta caustics, stronger liquid-glass membrane separation, volumetric glow fields and additional curved energy highlights. The same canonical 11-state renderer remains the single source of visual presence.

## Phase 4 — Notification intelligence

Phase 4 adds a real Android `NotificationListenerService` with an explicit user-granted Notification access flow. New notifications are classified into text, voice, image, video, document, missed-call, group, or other categories and assigned a conservative priority. Important direct messaging notifications can optionally be announced by voice; this is off by default. The latest notification becomes short-lived conversational context, enabling local follow-ups such as “What did he say?” and, when Android exposes `RemoteInput`, “Tell him I’ll talk to you later.” Replies use the notification's own inline reply `PendingIntent`; Void Core does not bypass the target app's permissions or authentication.

Gemini's default provider model is now the stable `gemini-3.6-flash` model. Provider abstraction/failover remains unchanged.

## Local-first runtime (0.4.1)
Void Core now prefers a local llama.cpp OpenAI-compatible server at `http://127.0.0.1:8080`. Deterministic Android commands execute without an LLM. Ambiguous device commands may be interpreted by the local model, converted into a strict whitelist JSON schema, then passed through the existing security gate. Remote Gemini/Groq providers are never allowed to directly create executable Android tool requests.

Example same-device server command (Termux):
`./llama-server -m /path/to/model.gguf -c 2048 -t 4 --host 127.0.0.1 --port 8080`


## Phase 5 — Embedded local AI

Void Core no longer requires Termux or a localhost llama.cpp server for its primary on-device model path. The app can download a LiteRT-LM model into private app storage, verify its SHA-256 checksum, load it on-device, stream responses, and use Gemini/Groq only as optional conversation fallbacks. Android actions remain deterministic and security-gated outside the LLM.


## Phase 5E visual fidelity
The renderer now uses a larger round dark-glass sphere, stronger surface illumination, cool and warm caustics, tighter luminous ribbons, denser orbiting particles, and depth-weighted particle brightness to make the sphere itself receive and reflect the surrounding energy.


## Phase 5F visual fidelity — particle lattice

- The central dark-glass sphere keeps a fixed physical size across semantic states. Only invoke/dismiss scale it.
- Continuous hard orbital arcs were removed from the core presentation. Moving micro-particles now form the luminous strands around the sphere.
- High quality uses dense sub-pixel cool particles with sparse peach/orange luminous kernels and depth-weighted front/back brightness.
- Listening, thinking, speaking, planning, acting and success change particle speed, light and density cues without making the sphere pulse in size.
- The particle field remains animated continuously while the solid sphere stays spatially anchored.


## Phase 5G visual fidelity — luminous veil
- The approved/generated luminous orb artwork is used only as a transparent energy-veil texture; the physical sphere remains procedural and fixed-size.
- Two independently rotating veil layers plus procedural micro-particles create a much denser luminous field without scaling the sphere between semantic states.
- Hard rim/outer-shell strokes were removed in favor of annular glow, surface lighting, and particle-made trails.
- Sphere scaling is limited to invoke/dismiss transitions.
