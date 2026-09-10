package dev.voidcore.app

/** Describes implementation availability; never controls assistant behavior. */
enum class ImplementationStatus(val title: String) {
 IMPLEMENTED("IMPLEMENTED"), MOCKED("MOCKED"), NOT_AVAILABLE("NOT AVAILABLE")
}
data class ModuleDiagnostic(val name: String, val status: ImplementationStatus, val detail: String)
val foundationDiagnostics = listOf(
 ModuleDiagnostic("app", ImplementationStatus.IMPLEMENTED, "Native Compose navigation and reserved routes."),
 ModuleDiagnostic("core-ui", ImplementationStatus.IMPLEMENTED, "Phase 1 layered Canvas renderer, choreographed transitions and visual settings. Device rendering/FPS unverified."),
 ModuleDiagnostic("assistant-state", ImplementationStatus.IMPLEMENTED, "One application-owned StateFlow; current input is a manual preview."),
 ModuleDiagnostic("presence previews", ImplementationStatus.MOCKED, "Four in-app forms morph through one renderer; fake phone invocation and dismissal previews."),
 ModuleDiagnostic("voice-engine", ImplementationStatus.IMPLEMENTED, "Phase 2 Android SpeechRecognizer + Android TextToSpeech runtime. Wake-word remains unavailable; device behavior still requires physical verification."),
 ModuleDiagnostic("agent-brain", ImplementationStatus.NOT_AVAILABLE, "Plan proposal contract only. No reasoning."),
 ModuleDiagnostic("provider-router", ImplementationStatus.IMPLEMENTED, "Phase 2 Gemini + Groq free-tier adapters, encrypted user key storage, streaming and failover. Live provider tests require user keys/network."),
 ModuleDiagnostic("android-tools", ImplementationStatus.NOT_AVAILABLE, "Request contracts exist. No Android adapters registered."),
 ModuleDiagnostic("accessibility-engine", ImplementationStatus.NOT_AVAILABLE, "Interface only. No service or accessibility access."),
 ModuleDiagnostic("vision-engine", ImplementationStatus.NOT_AVAILABLE, "Interface only. No camera, screen capture or recognition."),
 ModuleDiagnostic("overlay-service", ImplementationStatus.NOT_AVAILABLE, "No system overlay. Preview controller changes in-app layout only."),
 ModuleDiagnostic("notification-engine", ImplementationStatus.NOT_AVAILABLE, "Interface only. No listener or notification access."),
 ModuleDiagnostic("automation-engine", ImplementationStatus.NOT_AVAILABLE, "Definitions only. Nothing scheduled."),
 ModuleDiagnostic("workflow-engine", ImplementationStatus.NOT_AVAILABLE, "State-linked interface and checkpoint models. No cross-app execution."),
 ModuleDiagnostic("security-engine", ImplementationStatus.IMPLEMENTED, "Deny-all Phase 0/1 gate and audited executor. Production permission/confirmation checks are contracts only."),
 ModuleDiagnostic("memory / database", ImplementationStatus.IMPLEMENTED, "App-private foundation store with live flows and persistence. Provider secrets remain isolated in Android Keystore.")
)
