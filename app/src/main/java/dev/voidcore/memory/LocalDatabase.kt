package dev.voidcore.memory

import android.content.Context
import dev.voidcore.securityengine.ActionAuditLog
import dev.voidcore.securityengine.AuditEvent
import dev.voidcore.voiceengine.AudioProfile
import dev.voidcore.voiceengine.VoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lightweight app-private persistence used by the current foundation.
 *
 * This intentionally avoids annotation processors so the embedded LiteRT-LM Kotlin toolchain
 * cannot collide with Room/KAPT/KSP during builds. Secrets never belong here; provider keys stay
 * in the Android-Keystore-backed SecretStore.
 */
@Serializable data class AssistantPreferenceEntity(val key: String, val value: String)
@Serializable data class VoiceProfileEntity(val id: String, val profile: VoiceProfile)
@Serializable data class AudioProfileEntity(val id: String, val profile: AudioProfile)
@Serializable data class TrustRuleEntity(val id: String, val scope: String, val action: String, val allow: Boolean = false, val expiresAt: Long? = null)
@Serializable data class PeopleAliasEntity(val id: String, val alias: String, val localContactId: String)
@Serializable data class RecentContextEntity(val id: String, val text: String, val expiresAt: Long, val localOnly: Boolean = true)
@Serializable data class TaskWorkflowEntity(val id: String, val name: String, val definitionJson: String, val checkpointJson: String, val revision: Long = 0)
@Serializable data class AutomationEntity(val id: String, val name: String, val workflowId: String, val trigger: String, val timeZoneId: String, val enabled: Boolean = false)
@Serializable data class ActivityEntity(val id: String, val timestamp: Long, val category: String, val outcome: String, val requestId: String)

class FoundationDao(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("void-core.foundation", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val voiceState = MutableStateFlow(readVoices())
    private val audioState = MutableStateFlow(readAudio())
    private val activityState = MutableStateFlow(readActivity())
    private val preferenceState = MutableStateFlow(readPreferences())
    private val trustState = MutableStateFlow(readTrustRules())
    private val aliasState = MutableStateFlow(readAliases())
    private val contextState = MutableStateFlow(readRecentContext())
    private val workflowState = MutableStateFlow(readWorkflows())
    private val automationState = MutableStateFlow(readAutomations())

    fun voices(): Flow<List<VoiceProfileEntity>> = voiceState
    fun audio(): Flow<List<AudioProfileEntity>> = audioState
    fun activity(): Flow<List<ActivityEntity>> = activityState
    fun preferences(): Flow<List<AssistantPreferenceEntity>> = preferenceState
    fun trustRules(): Flow<List<TrustRuleEntity>> = trustState
    fun peopleAliases(): Flow<List<PeopleAliasEntity>> = aliasState
    fun automations(): Flow<List<AutomationEntity>> = automationState

    suspend fun recentContext(now: Long): List<RecentContextEntity> = contextState.value.filter { it.expiresAt > now }
    suspend fun workflow(id: String): TaskWorkflowEntity? = workflowState.value.firstOrNull { it.id == id }

    suspend fun save(value: AssistantPreferenceEntity) = synchronized(this) {
        preferenceState.value = preferenceState.value.upsert(value) { it.key }
        prefs.edit().putString(KEY_PREFERENCES, json.encodeToString(preferenceState.value)).apply()
    }

    suspend fun save(value: VoiceProfileEntity) = synchronized(this) {
        voiceState.value = voiceState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_VOICES, json.encodeToString(voiceState.value)).apply()
    }

    suspend fun deleteVoice(id: String) = synchronized(this) {
        voiceState.value = voiceState.value.filterNot { it.id == id }
        prefs.edit().putString(KEY_VOICES, json.encodeToString(voiceState.value)).apply()
    }

    suspend fun save(value: AudioProfileEntity) = synchronized(this) {
        audioState.value = audioState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_AUDIO, json.encodeToString(audioState.value)).apply()
    }

    suspend fun save(value: TrustRuleEntity) = synchronized(this) {
        trustState.value = trustState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_TRUST, json.encodeToString(trustState.value)).apply()
    }

    suspend fun save(value: PeopleAliasEntity) = synchronized(this) {
        aliasState.value = aliasState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_ALIASES, json.encodeToString(aliasState.value)).apply()
    }

    suspend fun save(value: RecentContextEntity) = synchronized(this) {
        contextState.value = contextState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_CONTEXT, json.encodeToString(contextState.value)).apply()
    }

    suspend fun save(value: TaskWorkflowEntity) = synchronized(this) {
        workflowState.value = workflowState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_WORKFLOWS, json.encodeToString(workflowState.value)).apply()
    }

    suspend fun save(value: AutomationEntity) = synchronized(this) {
        automationState.value = automationState.value.upsert(value) { it.id }
        prefs.edit().putString(KEY_AUTOMATIONS, json.encodeToString(automationState.value)).apply()
    }

    suspend fun append(value: ActivityEntity) = synchronized(this) {
        activityState.value = (listOf(value) + activityState.value).take(100)
        prefs.edit().putString(KEY_ACTIVITY, json.encodeToString(activityState.value)).apply()
    }

    suspend fun purgeExpired(now: Long) = synchronized(this) {
        contextState.value = contextState.value.filter { it.expiresAt > now }
        prefs.edit().putString(KEY_CONTEXT, json.encodeToString(contextState.value)).apply()
    }

    private fun readVoices(): List<VoiceProfileEntity> = decode(KEY_VOICES)
    private fun readAudio(): List<AudioProfileEntity> = decode(KEY_AUDIO)
    private fun readActivity(): List<ActivityEntity> = decode(KEY_ACTIVITY)
    private fun readPreferences(): List<AssistantPreferenceEntity> = decode(KEY_PREFERENCES)
    private fun readTrustRules(): List<TrustRuleEntity> = decode(KEY_TRUST)
    private fun readAliases(): List<PeopleAliasEntity> = decode(KEY_ALIASES)
    private fun readRecentContext(): List<RecentContextEntity> = decode(KEY_CONTEXT)
    private fun readWorkflows(): List<TaskWorkflowEntity> = decode(KEY_WORKFLOWS)
    private fun readAutomations(): List<AutomationEntity> = decode(KEY_AUTOMATIONS)

    private inline fun <reified T> decode(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
    }

    private fun <T> List<T>.upsert(value: T, key: (T) -> String): List<T> {
        val target = key(value)
        val index = indexOfFirst { key(it) == target }
        if (index < 0) return this + value
        return toMutableList().also { it[index] = value }
    }

    private companion object {
        const val KEY_VOICES = "voice_profiles"
        const val KEY_AUDIO = "audio_profiles"
        const val KEY_ACTIVITY = "activity_history"
        const val KEY_PREFERENCES = "assistant_preferences"
        const val KEY_TRUST = "trust_rules"
        const val KEY_ALIASES = "people_aliases"
        const val KEY_CONTEXT = "recent_context"
        const val KEY_WORKFLOWS = "tasks_workflows"
        const val KEY_AUTOMATIONS = "automation_definitions"
    }
}

class LocalDatabase private constructor(context: Context) {
    private val dao = FoundationDao(context)
    fun foundation(): FoundationDao = dao

    companion object {
        fun create(context: Context): LocalDatabase = LocalDatabase(context.applicationContext)
    }
}

class LocalAuditLog(private val dao: FoundationDao) : ActionAuditLog {
    override suspend fun append(event: AuditEvent) {
        dao.append(
            ActivityEntity(
                id = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                category = event.category.name,
                outcome = event.outcome,
                requestId = event.requestId
            )
        )
    }
}
