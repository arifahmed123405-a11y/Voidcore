package dev.voidcore.providerrouter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ProviderHealth { UNKNOWN, HEALTHY, DEGRADED, UNAVAILABLE }
enum class ProviderLocation { LOCAL, REMOTE }
data class GenerationRequest(val text: String, val imageUris: List<String> = emptyList(), val allowTools: Boolean = false, val allowRemoteContext: Boolean = false)
data class GenerationResult(val text: String, val providerId: String)
sealed interface GenerationEvent { data class Token(val text: String) : GenerationEvent; data class Complete(val result: GenerationResult) : GenerationEvent; data class Failed(val reason: String) : GenerationEvent }
data class ProviderStatus(val id: String, val priority: Int, val health: ProviderHealth, val available: Boolean, val location: ProviderLocation)
interface AiProvider {
 val id: String
 val location: ProviderLocation
 suspend fun generate(request: GenerationRequest): GenerationResult
 fun stream(request: GenerationRequest): Flow<GenerationEvent>
 fun supportsVision(): Boolean
 fun supportsTools(): Boolean
 suspend fun healthCheck(): ProviderHealth
}
data class RoutingPolicy(val priorityOrder: List<String>, val preferLocal: Boolean = true, val allowFailover: Boolean = true)
/** Adapters must be free/local. No provider is installed in Phase 0.
 * Remote context requires explicit opt-in. Never retry after emitted tokens or tool side effects without reconciliation. */
interface ProviderRouter {
 val statuses: StateFlow<List<ProviderStatus>>
 suspend fun generate(request: GenerationRequest, policy: RoutingPolicy): GenerationResult
 fun stream(request: GenerationRequest, policy: RoutingPolicy): Flow<GenerationEvent>
 suspend fun refreshHealth()
}
