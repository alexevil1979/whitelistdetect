package ru.whitelist.pulse.domain.model

enum class SiteGroup {
    WHITELIST,
    REGULAR,
    RESTRICTED,
    CUSTOM,
}

enum class ProbeStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    SLOW,
    UNAVAILABLE,
    DNS_ERROR,
    TIMEOUT,
    HTTP_ERROR,
    TLS_ERROR,
    RESET,
}

enum class VerdictKind {
    IDLE,
    SCANNING,
    NORMAL,
    WHITELIST_MODE,
    NO_INTERNET,
    VPN_ACTIVE,
    ABROAD_OR_BYPASS,
    PARTIAL,
}

enum class TransportKind {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    UNKNOWN,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AppLanguage {
    RU,
    EN,
}

enum class SiteSort {
    NAME,
    STATUS,
    LATENCY,
}

data class SiteEndpoint(
    val id: String,
    val host: String,
    val url: String,
    val group: SiteGroup,
    val enabled: Boolean = true,
    val tags: List<String> = emptyList(),
    val folder: String = "",
)

data class SiteCheckResult(
    val endpointId: String,
    val host: String,
    val url: String,
    val group: SiteGroup,
    val status: ProbeStatus,
    val latencyMs: Long?,
    val resolvedIp: String?,
    val httpCode: Int?,
    val checkedAtEpochMs: Long,
    val errorNote: String? = null,
) {
    val isSuccess: Boolean
        get() = status == ProbeStatus.AVAILABLE || status == ProbeStatus.SLOW
}

data class GroupStats(
    val group: SiteGroup,
    val total: Int,
    val success: Int,
    val avgLatencyMs: Long?,
) {
    val rate: Float
        get() = if (total == 0) 0f else success.toFloat() / total.toFloat()
}

data class Verdict(
    val kind: VerdictKind,
    val underlyingKind: VerdictKind,
    val confidence: Float,
    val reasons: List<String>,
    val hintKey: String,
    val groupStats: List<GroupStats>,
    val vpnDistorts: Boolean,
)

data class GeoInfo(
    val ipv4: String?,
    val ipv6: String?,
    val countryCode: String?,
    val countryName: String?,
    val region: String?,
    val city: String?,
    val asn: String?,
    val org: String?,
    val source: String?,
)

data class InstalledVpnApp(
    val id: String,
    val label: String,
    val packageName: String,
    val installed: Boolean,
)

data class VpnPresence(
    val transportVpn: Boolean,
    val tunnelInterfaces: List<String>,
    val httpProxy: String?,
    val knownApps: List<InstalledVpnApp>,
) {
    val isActive: Boolean
        get() = transportVpn || tunnelInterfaces.isNotEmpty() || !httpProxy.isNullOrBlank()
}

data class DnsServerInfo(
    val servers: List<String>,
    val privateDns: String?,
)

data class DnsLookupResult(
    val host: String,
    val addresses: List<String>,
    val elapsedMs: Long,
    val error: String?,
)

data class NetworkSnapshot(
    val transport: TransportKind,
    val hasValidatedInternet: Boolean,
    val metered: Boolean,
    val captivePortal: Boolean,
    val ssid: String?,
    val operatorName: String?,
    val simCountryIso: String?,
    val networkCountryIso: String?,
    val dns: DnsServerInfo,
    val vpn: VpnPresence,
    val geo: GeoInfo?,
    val timestampEpochMs: Long,
)

data class CheckHistoryEntry(
    val id: String,
    val timestampEpochMs: Long,
    val verdictKind: VerdictKind,
    val confidence: Float,
    val networkType: TransportKind,
    val vpnActive: Boolean,
    val groupARate: Float,
    val groupBRate: Float,
    val groupCRate: Float,
    val publicIp: String?,
    val country: String?,
)

enum class ComparisonDelta {
    BETTER,
    WORSE,
    SAME,
    NONE,
}

data class ProbeSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RU,
    val autoCheckOnLaunch: Boolean = true,
    val foregroundIntervalSec: Int = 0,
    val timeoutSec: Int = 4,
    val parallelLimit: Int = 6,
    val includeWhitelist: Boolean = true,
    val includeRegular: Boolean = true,
    val includeRestricted: Boolean = true,
    val includeCustom: Boolean = true,
    val whitelistOnlyMode: Boolean = false,
    val notifyOnResult: Boolean = false,
)

data class ProbeProgress(
    val completed: Int,
    val total: Int,
    val groupCompleted: Map<SiteGroup, Pair<Int, Int>>,
)
