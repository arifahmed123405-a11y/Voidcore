package dev.voidcore.app

enum class AppRoute(val title: String, val primary: Boolean = false) {
 HOME("Home", true), AUTOMATIONS("Automations", true), ACTIVITY("Activity", true), YOU("You", true),
 CONVERSATION("Conversation"), AGENT_WORKSPACE("Agent Workspace"), PROJECTS("Projects"), UNIVERSAL_SEARCH("Universal Search"), MEMORY("Memory"), CONNECTIONS("Connections"), TRUST_SAFETY("Trust & Safety"), VOICE_LAB("Voice Lab"), AUDIO_LAB("Audio Lab"), AI_PROVIDERS("AI Providers"), PRIVACY("Privacy"), OFFLINE("Offline"), PERFORMANCE("Performance"), CAMERA_VISION("Camera/Vision"), NOTIFICATIONS("Notifications"), CALLS("Calls"), DIAGNOSTICS("Diagnostics")
}
