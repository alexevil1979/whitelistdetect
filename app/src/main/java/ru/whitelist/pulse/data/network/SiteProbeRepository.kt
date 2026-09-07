package ru.whitelist.pulse.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.whitelist.pulse.domain.model.ProbeProgress
import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.repository.ProbeRepository
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiteProbeRepository @Inject constructor(
    private val probe: SiteProbe,
) : ProbeRepository {

    private val _progress = MutableStateFlow(ProbeProgress(0, 0, emptyMap()))
    private val _results = MutableStateFlow<List<SiteCheckResult>>(emptyList())

    override val progress: Flow<ProbeProgress> = _progress
    override val lastResults: Flow<List<SiteCheckResult>> = _results

    override suspend fun probe(
        endpoints: List<SiteEndpoint>,
        timeoutSec: Int,
        parallel: Int,
    ): List<SiteCheckResult> {
        if (endpoints.isEmpty()) {
            _progress.value = ProbeProgress(0, 0, emptyMap())
            _results.value = emptyList()
            return emptyList()
        }
        val groupedTotals = SiteGroup.entries.associateWith { group ->
            endpoints.count { it.group == group }
        }
        val groupedDone = SiteGroup.entries.associateWith { AtomicInteger(0) }
        val completed = AtomicInteger(0)
        _progress.value = ProbeProgress(
            completed = 0,
            total = endpoints.size,
            groupCompleted = groupedTotals.mapValues { (_, total) -> 0 to total },
        )
        val semaphore = Semaphore(parallel.coerceIn(2, 8))
        val results = coroutineScope {
            endpoints.map { endpoint ->
                async {
                    semaphore.withPermit {
                        val checking = SiteCheckResult(
                            endpointId = endpoint.id,
                            host = endpoint.host,
                            url = endpoint.url,
                            group = endpoint.group,
                            status = ProbeStatus.CHECKING,
                            latencyMs = null,
                            resolvedIp = null,
                            httpCode = null,
                            checkedAtEpochMs = System.currentTimeMillis(),
                        )
                        mergeResult(checking)
                        val result = probe.probe(endpoint, timeoutSec)
                        groupedDone[endpoint.group]?.incrementAndGet()
                        val done = completed.incrementAndGet()
                        _progress.value = ProbeProgress(
                            completed = done,
                            total = endpoints.size,
                            groupCompleted = groupedTotals.mapValues { (group, total) ->
                                (groupedDone[group]?.get() ?: 0) to total
                            },
                        )
                        mergeResult(result)
                        result
                    }
                }
            }.awaitAll()
        }
        _results.value = results
        return results
    }

    private fun mergeResult(result: SiteCheckResult) {
        val current = _results.value.toMutableList()
        val index = current.indexOfFirst { it.endpointId == result.endpointId }
        if (index >= 0) current[index] = result else current += result
        _results.value = current
    }
}
