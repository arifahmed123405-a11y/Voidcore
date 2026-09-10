package dev.voidcore.providerrouter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class DefaultProviderRouter(private val providers: List<AiProvider>) : ProviderRouter {
 private val mutableStatuses = MutableStateFlow(providers.mapIndexed { index, provider ->
  ProviderStatus(provider.id, index, ProviderHealth.UNKNOWN, true, provider.location)
 })
 override val statuses = mutableStatuses.asStateFlow()

 private fun ordered(policy: RoutingPolicy): List<AiProvider> {
  val byId = providers.associateBy { it.id }
  return (policy.priorityOrder.mapNotNull(byId::get) + providers.filterNot { it.id in policy.priorityOrder }).distinctBy { it.id }
 }

 override suspend fun generate(request: GenerationRequest, policy: RoutingPolicy): GenerationResult {
  var last: Throwable? = null
  for (provider in ordered(policy)) {
   try {
    val result = provider.generate(request)
    mark(provider.id, ProviderHealth.HEALTHY, true)
    return result
   } catch (t: Throwable) {
    last = t
    mark(provider.id, ProviderHealth.DEGRADED, true)
    if (!policy.allowFailover) break
   }
  }
  throw IllegalStateException(last?.message ?: "No AI provider is available", last)
 }

 override fun stream(request: GenerationRequest, policy: RoutingPolicy): Flow<GenerationEvent> = flow {
  var lastReason = "No AI provider is available"
  for (provider in ordered(policy)) {
   var emittedToken = false
   var completed: GenerationResult? = null
   var failedReason: String? = null
   provider.stream(request).collect { event ->
    when (event) {
     is GenerationEvent.Token -> { emittedToken = true; emit(event) }
     is GenerationEvent.Complete -> completed = event.result
     is GenerationEvent.Failed -> failedReason = event.reason
    }
   }
   completed?.let {
    mark(provider.id, ProviderHealth.HEALTHY, true)
    emit(GenerationEvent.Complete(it))
    return@flow
   }
   lastReason = failedReason ?: "$provider returned no completed response"
   mark(provider.id, ProviderHealth.DEGRADED, true)
   if (emittedToken) {
    emit(GenerationEvent.Failed("Streaming failed after output began: $lastReason"))
    return@flow
   }
   if (!policy.allowFailover) break
  }
  emit(GenerationEvent.Failed(lastReason))
 }

 override suspend fun refreshHealth() {
  mutableStatuses.value = providers.mapIndexed { index, provider ->
   val health = runCatching { provider.healthCheck() }.getOrDefault(ProviderHealth.UNAVAILABLE)
   ProviderStatus(provider.id, index, health, health != ProviderHealth.UNAVAILABLE, provider.location)
  }
 }

 private fun mark(id: String, health: ProviderHealth, available: Boolean) {
  mutableStatuses.value = mutableStatuses.value.map { if (it.id == id) it.copy(health = health, available = available) else it }
 }
}
