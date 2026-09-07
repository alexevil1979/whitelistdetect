package ru.whitelist.pulse.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.repository.CustomSiteRepository
import javax.inject.Inject

class ManageCustomSites @Inject constructor(
    private val repository: CustomSiteRepository,
) {
    fun observe(): Flow<List<SiteEndpoint>> = repository.observe()

    suspend fun add(raw: String, tag: String = "", enabled: Boolean = true): SiteEndpoint? {
        val host = DomainListParser.normalize(raw) ?: return null
        return repository.add(host, tag, enabled)
    }

    suspend fun importList(raw: String, tag: String = ""): DomainListParser.ParseResult {
        val parsed = DomainListParser.parse(raw)
        if (parsed.hosts.isNotEmpty()) {
            repository.importHosts(parsed.hosts, tag)
        }
        return parsed
    }

    suspend fun update(site: SiteEndpoint) = repository.update(site)

    suspend fun delete(id: String) = repository.delete(id)

    suspend fun exportText(): String = repository.exportText()
}
