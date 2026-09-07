package ru.whitelist.pulse.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import ru.whitelist.pulse.domain.repository.EndpointRepository
import ru.whitelist.pulse.domain.repository.HistoryRepository
import ru.whitelist.pulse.domain.repository.NetworkRepository
import ru.whitelist.pulse.domain.repository.ProbeRepository
import ru.whitelist.pulse.domain.usecase.ComputeVerdict
import ru.whitelist.pulse.domain.usecase.LookupGeoIp
import ru.whitelist.pulse.domain.usecase.ObserveNetworkSnapshot
import ru.whitelist.pulse.domain.usecase.RunSiteGroupChecks
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
    private val observeNetworkSnapshot: ObserveNetworkSnapshot,
    private val lookupGeoIp: LookupGeoIp,
    private val networkRepository: NetworkRepository,
    private val endpointRepository: EndpointRepository,
    private val customSiteRepository: CustomSiteRepository,
    private val historyRepository: HistoryRepository,
    private val probeRepository: ProbeRepository,
    private val settingsStore: SettingsDataStore,
) {
    private val _state = MutableStateFlow(ProbeUiState())
    val state: StateFlow<ProbeUiState> = _state.asStateFlow()
    private var intervalJob: Job? = null
    private var probeJob: Job? = null
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            probeRepository.progress.collect { progress ->
                _state.update { it.copy(progress = progress) }
            }
        }
        scope.launch {
            probeRepository.lastResults.collect { results ->
                if (results.isNotEmpty()) {
                    _state.update { it.copy(results = mergeResults(it.results, results)) }
                }
            }
        }
        scope.launch {
            combine(
                settingsStore.settings,
                observeNetworkSnapshot(),
                endpointRepository.observeEndpoints(),
                customSiteRepository.observe(),
                historyRepository.observe(20),
            ) { settings, snapshot, endpoints, custom, history ->
                _state.update {
                    it.copy(
                        settings = settings,
                        snapshot = snapshot.copy(geo = snapshot.geo ?: it.snapshot?.geo),
                        endpoints = endpoints,
                        customSites = custom,
                        history = history,
                    )
                }
            }.collect { }
        }
        scope.launch {
            runCatching { lookupGeoIp() }.getOrNull()?.let { geo ->
                _state.update { st ->
                    st.copy(snapshot = st.snapshot?.copy(geo = geo) ?: st.snapshot)
                }
            }
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
        probeJob?.cancel()
        probeJob = scope.launch {
            _state.update {
                it.copy(
                    scanning = true,
                    error = null,
                    verdict = it.verdict?.copy(kind = VerdictKind.SCANNING) ?: idleVerdict(),
                )
            }
            try {
                val snapshot = networkRepository.currentSnapshot()
                val settings = settingsStore.current()
                val all = endpointRepository.endpoints() + _state.value.customSites
                val results = runChecks(all, settings, groups)
                val vpnActive = snapshot.vpn.isActive
                val verdict = computeVerdict.fromResults(
                    results = results,
                    vpnActive = vpnActive,
                    includeWhitelist = settings.includeWhitelist,
                    includeRegular = settings.includeRegular && !settings.whitelistOnlyMode,
                    includeRestricted = settings.includeRestricted && !settings.whitelistOnlyMode,
                )
                val dns = networkRepository.lookupDns(endpointRepository.dnsControls())
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
                        snapshot = snapshot.copy(geo = snapshot.geo ?: it.snapshot?.geo),
                        results = mergeResults(it.results, results),
                        verdict = verdict,
                        scanning = false,
                        lastCheckedAt = System.currentTimeMillis(),
                        comparison = comparison,
                        dnsLookups = dns,
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(scanning = false) }
                throw cancelled
            } catch (error: Throwable) {
                _state.update { it.copy(scanning = false, error = error.message) }
            }
        }
    }

    fun cancelProbe() {
        probeJob?.cancel()
        _state.update { it.copy(scanning = false) }
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
