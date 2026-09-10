package dev.voidcore.app

import android.app.Application
import dev.voidcore.assistantstate.AssistantStateEngine
import dev.voidcore.overlayservice.PresencePreviewController
import dev.voidcore.memory.LocalDatabase
import dev.voidcore.memory.LocalAuditLog
import dev.voidcore.securityengine.*
import dev.voidcore.androidtools.nativeToolRegistry
import dev.voidcore.voiceengine.AndroidVoiceEngine
import dev.voidcore.providerrouter.*
import dev.voidcore.notificationengine.NotificationRepository
import dev.voidcore.localmodel.EmbeddedModelManager

class VoidApplication : Application() {
 val assistantState = AssistantStateEngine()
 val presence = PresencePreviewController(assistantState)
 val database by lazy { LocalDatabase.create(this) }
 val executor by lazy { GatedExecutor(assistantState, Phase3SecurityGate, LocalAuditLog(database.foundation()), nativeToolRegistry(this)) }
 val secretStore by lazy { SecretStore(this) }
 val notificationRepository by lazy { NotificationRepository(this) }
 val embeddedModelManager by lazy { EmbeddedModelManager(this) }
 val voiceEngine by lazy { AndroidVoiceEngine(this, assistantState) }
 val providerRouter by lazy {
  val client = phase2HttpClient()
  DefaultProviderRouter(listOf(
   EmbeddedLiteRtProvider(embeddedModelManager),
   GeminiProvider(client, secretStore),
   GroqProvider(client, secretStore)
  ))
 }
}
