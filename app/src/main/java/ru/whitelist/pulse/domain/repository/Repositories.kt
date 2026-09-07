package ru.whitelist.pulse.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.whitelist.pulse.domain.model.CheckHistoryEntry
import ru.whitelist.pulse.domain.model.DnsLookupResult
import ru.whitelist.pulse.domain.model.GeoInfo
import ru.whitelist.pulse.domain.model.NetworkSnapshot
import ru.whitelist.pulse.domain.model.ProbeProgress
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.VpnPresence

interface NetworkRepository {
    fun observeSnapshot(): Flow<NetworkSnapshot>
    suspend fun currentSnapshot(): NetworkSnapshot
    suspend fun lookupDns(hosts: List<String>): List<DnsLookupResult>
}

interface GeoRepository {
    suspend fun lookup(): GeoInfo
}

interface VpnRepository {
    suspend fun detect(): VpnPresence
}

interface EndpointRepository {
    fun observeEndpoints(): Flow<List<SiteEndpoint>>
    suspend fun endpoints(): List<SiteEndpoint>
    suspend fun replaceBundledFromJson(json: String)
}

interface CustomSiteRepository {
    fun observe(): Flow<List<SiteEndpoint>>
    suspend fun add(host: String, tag: String, enabled: Boolean): SiteEndpoint
    suspend fun importHosts(hosts: List<String>, tag: String)
    suspend fun update(site: SiteEndpoint)
    suspend fun delete(id: String)
    suspend fun exportText(): String
}

interface HistoryRepository {
    fun observe(limit: Int = 20): Flow<List<CheckHistoryEntry>>
    suspend fun latest(limit: Int = 20): List<CheckHistoryEntry>
    suspend fun add(entry: CheckHistoryEntry)
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<ProbeSettings>
    suspend fun current(): ProbeSettings
    suspend fun update(transform: (ProbeSettings) -> ProbeSettings)
}

interface ProbeRepository {
    val progress: Flow<ProbeProgress>
    val lastResults: Flow<List<SiteCheckResult>>
    suspend fun probe(
        endpoints: List<SiteEndpoint>,
        timeoutSec: Int,
        parallel: Int,
    ): List<SiteCheckResult>
}
