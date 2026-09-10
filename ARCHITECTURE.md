# Extension boundaries

| Requested area | Kotlin package | Owns | Must not do |
|---|---|---|---|
| app | app | Composition root, navigation, lifecycle | Implement provider-specific logic |
| core-ui | coreui | Render snapshots, components | Infer independent assistant state |
| assistant-state | assistantstate | One observable semantic state | Authorize tools based on animation |
| voice-engine | voiceengine | Voice/audio contracts and profiles | Claim DSP controls all work in Android TTS |
| agent-brain | agentbrain | Propose plans | Execute Android actions |
| provider-router | providerrouter | Provider contracts and route policies | Upload local context without opt-in |
| android-tools | androidtools | Tool requests/results and adapter contract | Skip the validated executor |
| accessibility-engine | accessibilityengine | Availability and action proposals | Bypass protected applications |
| vision-engine | visionengine | Scoped vision input and output | Silently capture or upload screens |
| overlay-service | overlayservice | App-owned preview layout and future overlay contract | Start a real overlay in Phase 0 |
| notification-engine | notificationengine | Notification observation contract | Access notifications without user grant |
| automation-engine | automationengine | Trigger definitions | Schedule real work in Phase 0 |
| workflow-engine | workflowengine | Steps, dependencies, status, checkpoints | Retry side effects without reconciliation |
| memory | memory | Local records and DAO | Treat app-private Room as custom encryption |
| security-engine | securityengine | Validation and audited dispatch | Treat model text as user confirmation |

## Phase 1 visual architecture

`VoidApplication` retains the only production AssistantStateEngine. The Activity-scoped `VisualDemoViewModel` receives it and the existing PresencePreviewController through a ViewModel factory. It advances canonical preview states for demos; it has no provider, Android tool, workflow executor or Android API dependency.

`CoreRuntime` builds one shared CoreRenderModel from the canonical snapshot. It contains animated presentation values, the shared frame clock and state-entry/completion/dismissal envelopes. It does not own another AssistantState. Renderer geometry targets in CorePose describe shape/luminance, not semantic behavior.

`CoreRenderer.Render(model, modifier, form)` remains the replacement seam. EnergyVeilRenderer implements it with cached closed spline contours, layered shell paths, a dark occluding void, open filaments, purposeful particles and completion/dismissal fragments. A future renderer can consume the same model without changing navigation or the demo controller.

The visual overhaul adds `DimensionalField`, an internal rendering helper with cached rear/front paths per projected ribbon. Geometry is updated once per draw from the existing interpolated weights; rear paths are drawn before the shell and foreground paths after the void. No depth buffer or full 3D engine is required. High quality uses 12 ribbons at 128 samples, while Low uses 4 at 48. Additional geometry/glow passes require device profiling.

`SpatialSurfaces` provides a cached room-lighting modifier and shared panel treatment. `LocalVisualSettings` supplies the existing settings to cards and command surfaces; it is a presentation configuration provider, not a semantic assistant state. It does not own mutable state. Reduce Transparency removes scene fog, panel shadows and reflection accents. The environment has no separate clock. Core framing adds a reflected floor; Edge Agent draws a split rail within the same renderer. Navigation, ViewModel jobs, data contracts and all real-capability restrictions remain unchanged.

`PresenceStage` uses one renderer instance throughout Full Presence → Compact → Capsule → Edge Agent and back. Bounds, center, capsule weighting and docking offset animate together. A fake phone background, localized dimming and edge light are in-app content. No WindowManager or service appears in the manifest.

## In-app spatial scenery

`PreviewScene` describes only fake screen content (ordinary phone, keyboard, fullscreen, lock screen, action target). It is stored in VisualDemoUi. Scene presets use the injected presence controller for mode changes and the canonical state engine for semantic changes. The new spatial sequence and mock-unlock sequence use the existing cancellable job/generation guard. They do not access OS state or any tool executor.

PresenceStage keeps one renderer node while its bounds and position change; keyboard/lock scenery clamps its height. The action-target filament reads the interpolated canonical ACTING projection weight. Lock-screen text is fixed and excludes transcript/context data. Mock unlock expands without reinstantiating the renderer. No new navigation route, system window, service, permission, database record or provider dependency is introduced.

## Canonical state and lifecycle

Home, Conversation, Workspace and Diagnostics receive the same CoreRenderModel and canonical snapshot. Future voice, audio, workflow, overlay and executor interfaces retain StateSynchronizedAdapter. StepStatus, WorkflowStatus, ProviderHealth, ImplementationStatus and presence layout describe their respective domains and never replace semantic assistant state.

The display-frame clock reads the interpolated activity weight. It runs only on visual routes while the Activity is STARTED, and is disabled under Reduce Motion. High quality uses each display tick; Balanced/Low throttle ambient state writes to at most 45/30 Hz, subject to display quantization. State-entry animations use Compose animation clocks. A single model supplies both drawing and diagnostics; no microphone data is generated by another engine.

Timed demo jobs live in the ViewModel scope. Manual selection and new state sequences cancel prior jobs; a generation guard prevents a cancelled job's finally block from changing the new sequence's running flag. Navigation/backgrounding stops playback. Mode playback changes layout only. Stop sequence cancels future transitions, not the ambient motion of the selected state.

## State choreography

| State | Geometry/temporal behavior |
|---|---|
| SLEEPING | Very slow internal phase, faint energy |
| INVOKING | Assembly envelope contracts dispersed filaments toward the emerging shell |
| LISTENING | Mock listening envelope opens/deforms the shell slightly |
| THINKING | Internal meridians advance faster than the calm shell boundary |
| PLANNING | Branch/fork weight reveals paths and junctions |
| ACTING | Center tightens; projection weight introduces outward strokes |
| WAITING_FOR_USER | Slow attentive pulse and neutral warm tint |
| SPEAKING | Mock speech envelope changes volume/deformation |
| SUCCESS | Outer alignment and one pulse; core remains asymmetric |
| ERROR_RECOVERY | Separation envelope rises then reforms the strands |
| DISMISSING | Contraction, filament conversion and edge retraction |

State targets interpolate with restrained easing. Mode morphs use critically damped springs. The renderer avoids a circular identity; circles are limited to diffuse scattering and sparse particle points. Low/Standard/High tiers bound contour samples, shell layers, strands and particles. Performance is unmeasured.

## Accessibility and feedback

VisualSettings contains Reduce Motion, Reduce Transparency, quality and docking edge. Reduce Motion fixes the core's scale/parallax and suppresses continuous movement and particles; color, static topology and state descriptions remain. Presence geometry snaps to its destination rather than traveling. Reduce Transparency uses solid surface treatments and removes diffuse/glass accents. Settings survive activity recreation through the ViewModel, but not process death.

Command input, mic/attachment actions, state text and presence previews have accessible text/descriptions. The invisible invocation surface is hidden from accessibility semantics. Swipe dismissal also has an explicit button. Touch targets were enlarged for state chips and bottom navigation. TalkBack and large-font behavior are not device-verified.

VisualFeedbackSource exposes state-revision-associated VisualFeedbackEvent records for wake/listening/action/confirmation/success/warning/dismissal plus HapticIntent. VisualFeedbackSink is a future interface. No sink, audio asset, vibration API or copyrighted sound is installed.

## Retained Phase 0 boundaries

The planner produces workflow proposals only. GatedExecutor remains the only current source caller of tool.execute. Its installed FoundationSecurityGate denies all requests; no adapters are registered. Request arguments are frozen before validation and audit writes precede dispatch. The Phase 1 demo ViewModel never calls this path. Package boundaries are source conventions, not a security sandbox for arbitrary new Kotlin code; no model output is evaluated as code.

All permission/scope/risk/confirmation/protected-action/secure-app/audit contracts remain. Production consent binding, replay prevention, OS checks and execution verification are unimplemented. Workflow definitions retain order, dependencies, confirmation, retry, verification and partial-completion/checkpoint models. No workflow runner is added.

Provider generation, streaming, vision/tool capability, health, availability and failover contracts remain provider-neutral. There are no external provider SDKs or paid APIs. All nine Room entity groups and DAO definitions remain unchanged, schema version 1. No API secrets or new personal context are stored; future credentials still require a separate Keystore-backed store. Room runtime remains unverified.

VoicePresets still contains Neutral Core, Void, Architect, Spectral, Titan and Omega. VoiceProfile includes custom names and all 22 controls; the voice/audio catalog and command-interpreter boundaries remain unimplemented. Omega is original and non-human; it is a parameter definition, not synthesized audio.

## Tests and verification

The eight existing FoundationTest cases remain. VisualDemoTest adds manual-selection cancellation, stopping before speech, mode/state isolation and completion/dismissal return-to-sleep coverage. These twelve JUnit tests are written but have not run in this environment.

The updated static-audit script passed. It checks source/config pins, references, entity-derived SQL, canonical state, visual target inventory, current execution boundaries, sequence order, settings, screens, feedback hooks and diagnostics labels. This is not a Kotlin compiler, Room processor, renderer test, security proof or frame-rate benchmark. Exact build/run and physical-device checks are in README.

The original Phase 1 boundary is superseded by the Phase 2 runtime additions documented below.

## Phase 2 runtime additions

`Phase2ViewModel` is the conversation/voice coordinator. It is the only Phase 2 component that maps speech/provider events into the canonical `AssistantStateEngine`. `AndroidVoiceEngine` wraps Android `SpeechRecognizer` and `TextToSpeech`; manual microphone input is real, while always-on wake word remains intentionally unavailable.

`LocalTaskRouter` classifies obvious device actions, voice-profile changes, vision requests, rewrites, reasoning and conversation before any remote request. Device actions return a Phase-3 boundary response and never reach `GatedExecutor`.

`DefaultProviderRouter` owns provider ordering and failover. `GeminiProvider` and `GroqProvider` are adapters behind `AiProvider`; UI code does not call provider-specific APIs. Provider secrets are encrypted by `SecretStore` using Android Keystore. Room has no credential columns.

The visual renderer is unchanged in identity and still consumes the canonical assistant state. Real SpeechRecognizer RMS values feed the LISTENING envelope when available. TTS start/end callbacks drive SPEAKING/SUCCESS; advanced speech DSP is not implemented.

## Phase 3 runtime additions

`LocalActionParser` converts a small deterministic command set into immutable `ToolRequest` values. `GatedExecutor` receives a `Phase3SecurityGate` and the application-owned native tool registry. Only low-risk `interactive-command` requests are allowed. The registry implements app launch, torch, volume, brightness, timer/alarm, media key, clipboard and explicit accessibility UI actions. `VoidAccessibilityService` is opt-in and is never enabled programmatically.

Phase 3 does not authorize calls/messages/files or provider-generated arbitrary tool execution. Those remain behind the same security boundary for later phases.

## Phase 4 boundary

Phase 4 introduces notification intelligence only. `VoidNotificationListenerService` observes notifications after the user explicitly grants Android Notification access. `NotificationRepository` holds recent notification context and ephemeral `RemoteInput` reply handles. The assistant may read the latest visible notification text locally and send a reply only through the notification's own inline-reply `PendingIntent`. It does not scrape message databases, bypass authentication, or claim unsupported media transcription. Automatic voice announcements are opt-in and default off. Calls, persistent outside-app overlay, voice-note transcription, file workflows, and broader message automation remain later phases.
