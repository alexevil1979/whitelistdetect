package ru.whitelist.pulse.domain.usecase

import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.repository.ProbeRepository
import javax.inject.Inject

class RunSiteGroupChecks @Inject constructor(
    private val probeRepository: ProbeRepository,
) {
    suspend operator fun invoke(
        endpoints: List<SiteEndpoint>,
        settings: ProbeSettings,
        groups: Set<SiteGroup>? = null,
    ): List<SiteCheckResult> {
        val selected = endpoints.filter { endpoint ->
            endpoint.enabled && (groups == null || endpoint.group in groups) && groupAllowed(endpoint.group, settings)
        }
        return probeRepository.probe(
            endpoints = selected,
            timeoutSec = settings.timeoutSec.coerceIn(2, 8),
            parallel = settings.parallelLimit.coerceIn(2, 8),
        )
    }

    private fun groupAllowed(group: SiteGroup, settings: ProbeSettings): Boolean = when (group) {
        SiteGroup.WHITELIST -> settings.includeWhitelist
        SiteGroup.REGULAR -> settings.includeRegular && !settings.whitelistOnlyMode
        SiteGroup.RESTRICTED -> settings.includeRestricted && !settings.whitelistOnlyMode
        SiteGroup.CUSTOM -> settings.includeCustom
    }
}
