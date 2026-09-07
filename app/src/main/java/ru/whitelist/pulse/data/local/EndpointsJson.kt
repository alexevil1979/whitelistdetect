package ru.whitelist.pulse.data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup

@Serializable
data class EndpointsFile(
    val version: String = "",
    val disclaimer: String = "",
    val groups: List<EndpointGroupDto> = emptyList(),
    @SerialName("dns_controls") val dnsControls: List<String> = emptyList(),
)

@Serializable
data class EndpointGroupDto(
    val id: String,
    val endpoints: List<EndpointDto> = emptyList(),
)

@Serializable
data class EndpointDto(
    val id: String,
    val host: String,
    val url: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class KnownVpnFile(
    val packages: List<KnownVpnDto> = emptyList(),
)

@Serializable
data class KnownVpnDto(
    val id: String,
    val label: String,
    val packageName: String,
)

fun EndpointsFile.toEndpoints(): List<SiteEndpoint> = groups.flatMap { group ->
    val siteGroup = when (group.id) {
        "whitelist" -> SiteGroup.WHITELIST
        "regular" -> SiteGroup.REGULAR
        "restricted" -> SiteGroup.RESTRICTED
        else -> SiteGroup.CUSTOM
    }
    group.endpoints.map { dto ->
        SiteEndpoint(
            id = dto.id,
            host = dto.host,
            url = dto.url,
            group = siteGroup,
            tags = dto.tags,
        )
    }
}
