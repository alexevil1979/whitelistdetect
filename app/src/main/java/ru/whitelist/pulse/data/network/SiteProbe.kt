package ru.whitelist.pulse.data.network

import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint

interface SiteProbe {
    suspend fun probe(endpoint: SiteEndpoint, timeoutSec: Int): SiteCheckResult
}

data class ProbeInternals(
    val dnsMs: Long?,
    val connectMs: Long?,
    val httpMs: Long?,
    val status: ProbeStatus,
    val ip: String?,
    val code: Int?,
    val note: String?,
)
