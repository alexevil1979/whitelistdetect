package ru.whitelist.pulse.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.whitelist.pulse.data.local.AssetEndpointRepository
import ru.whitelist.pulse.data.local.datastore.SettingsDataStore
import ru.whitelist.pulse.domain.model.CheckHistoryEntry
import ru.whitelist.pulse.domain.model.ComparisonDelta
import ru.whitelist.pulse.domain.model.DnsLookupResult
import ru.whitelist.pulse.domain.model.NetworkSnapshot
import ru.whitelist.pulse.domain.model.ProbeProgress
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.Verdict
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.domain.repository.CustomSiteRepository
import ru.whitelist.pulse.domain.repository.HistoryRepository
import ru.whitelist.pulse.domain.repository.NetworkRepository
import ru.whitelist.pulse.domain.usecase.ComputeVerdict
import ru.whitelist.pulse.domain.usecase.RunSiteGroupChecks
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.whitelist.pulse.widget.VerdictWidget
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ProbeUiState(
    val settings: ProbeSettings = ProbeSettings(),
    val snapshot: NetworkSnapshot? = null,
    val endpoints: List<SiteEndpoint> = emptyList(),
    val customSites: List<SiteEndpoint> = emptyList(),
    val results: List<SiteCheckResult> = emptyList(),
    val verdict: Verdict? = null,
    val progress: ProbeProgress = ProbeProgress(0, 0, emptyMap()),
    val scanning: Boolean = false,
    val lastCheckedAt: Long? = null,
    val history: List<CheckHistoryEntry> = emptyList(),
    val comparison: ComparisonDelta = ComparisonDelta.NONE,
    val dnsLookups: List<DnsLookupResult> = emptyList(),
    val error: String? = null,
)

@Singleton
class ProbeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runChecks: RunSiteGroupChecks,
    private val computeVerdict: ComputeVerdict,
    private val networkRepository: NetworkRepository,
    private val endpointRepository: AssetEndpointRepository,
    private val customSiteRepository: CustomSiteRepository,
    private val historyRepository: HistoryRepository,
    private val settingsStore: SettingsDataStore,
) {
    private val _state = MutableStateFlow(ProbeUiState())
    val state: StateFlow<ProbeUiState> = _state.asStateFlow()
    private var intervalJob: Job? = null

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                settingsStore.settings,
                networkRepository.observeSnapshot(),
                endpointRepository.observeEndpoints(),
                customSiteRepository.observe(),
                historyRepository.observe(20),
            ) { settings, snapshot, endpoints, custom, history ->
                _state.update {
                    it.copy(
                        settings = settings,
                        snapshot = snapshot,
                        endpoints = endpoints,
                        customSites = custom,
                        history = history,
                    )
                }
            }.collect { }
        }
        scope.launch {
            settingsStore.settings.collect { settings ->
                intervalJob?.cancel()
                if (settings.foregroundIntervalSec > 0) {
                    intervalJob = scope.launch {
                        while (true) {
                            delay(settings.foregroundIntervalSec * 1000L)
                            run(scope)
                        }
                    }
                }
            }
        }
    }

    fun run(scope: CoroutineScope, groups: Set<SiteGroup>? = null) {
        if (_state.value.scanning) return
        scope.launch {
            _state.update { it.copy(scanning = true, error = null, verdict = it.verdict?.copy(kind = VerdictKind.SCANNING) ?: idleVerdict()) }
            runCatching {
                val snapshot = networkRepository.currentSnapshot()
                val settings = settingsStore.current()
                val all = endpointRepository.endpoints() + customSiteRepository.observe().let { _state.value.customSites }
                val results = runChecks(all.ifEmpty { endpointRepository.endpoints() + _state.value.customSites }, settings, groups)
                val vpnActive = snapshot.vpn.isActive
                val verdict = computeVerdict.fromResults(
                    results = results,
                    vpnActive = vpnActive,
                    includeWhitelist = settings.includeWhitelist,
                    includeRegular = settings.includeRegular && !settings.whitelistOnlyMode,
                    includeRestricted = settings.includeRestricted && !settings.whitelistOnlyMode,
                )
                val dnsHosts = endpointRepository.dnsControls().ifEmpty {
                    listOf("ya.ru", "gosuslugi.ru", "youtube.com", "instagram.com")
                }
                val dns = networkRepository.lookupDns(dnsHosts)
                val previous = historyRepository.latest(1).firstOrNull()
                val comparison = compare(previous, verdict)
                val entry = CheckHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    timestampEpochMs = System.currentTimeMillis(),
                    verdictKind = verdict.kind,
                    confidence = verdict.confidence,
                    networkType = snapshot.transport,
                    vpnActive = vpnActive,
                    groupARate = verdict.groupStats.find { it.group == SiteGroup.WHITELIST }?.rate ?: 0f,
                    groupBRate = verdict.groupStats.find { it.group == SiteGroup.REGULAR }?.rate ?: 0f,
                    groupCRate = verdict.groupStats.find { it.group == SiteGroup.RESTRICTED }?.rate ?: 0f,
                    publicIp = snapshot.geo?.ipv4,
                    country = snapshot.geo?.countryCode,
                )
                historyRepository.add(entry)
                settingsStore.saveLastVerdict(verdict.kind, vpnActive)
                runCatching { VerdictWidget.push(context, verdict.kind, vpnActive) }
                _state.update {
                    it.copy(
                        snapshot = snapshot,
                        results = mergeResults(it.results, results),
                        verdict = verdict,
                        scanning = false,
                        lastCheckedAt = System.currentTimeMillis(),
                        comparison = comparison,
                        dnsLookups = dns,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(scanning = false, error = error.message) }
            }
        }
    }

    private fun mergeResults(old: List<SiteCheckResult>, incoming: List<SiteCheckResult>): List<SiteCheckResult> {
        if (incoming.isEmpty()) return old
        val map = old.associateBy { it.endpointId }.toMutableMap()
        incoming.forEach { map[it.endpointId] = it }
        return map.values.toList()
    }

    private fun compare(previous: CheckHistoryEntry?, verdict: Verdict): ComparisonDelta {
        if (previous == null) return ComparisonDelta.NONE
        val prevScore = previous.groupARate + previous.groupBRate
        val nextA = verdict.groupStats.find { it.group == SiteGroup.WHITELIST }?.rate ?: 0f
        val nextB = verdict.groupStats.find { it.group == SiteGroup.REGULAR }?.rate ?: 0f
        val nextScore = nextA + nextB
        val delta = nextScore - prevScore
        return when {
            delta > 0.12f -> ComparisonDelta.BETTER
            delta < -0.12f -> ComparisonDelta.WORSE
            else -> ComparisonDelta.SAME
        }
    }

    private fun idleVerdict() = Verdict(
        kind = VerdictKind.IDLE,
        underlyingKind = VerdictKind.IDLE,
        confidence = 0f,
        reasons = emptyList(),
        hintKey = "hint_partial",
        groupStats = emptyList(),
        vpnDistorts = false,
    )
}
