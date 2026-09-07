package ru.whitelist.pulse.domain.usecase

import ru.whitelist.pulse.domain.model.GroupStats
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.Verdict
import ru.whitelist.pulse.domain.model.VerdictKind
import javax.inject.Inject

class ComputeVerdict @Inject constructor() {

    fun fromResults(
        results: List<SiteCheckResult>,
        vpnActive: Boolean,
        includeWhitelist: Boolean = true,
        includeRegular: Boolean = true,
        includeRestricted: Boolean = true,
    ): Verdict {
        val a = stats(results, SiteGroup.WHITELIST)
        val b = stats(results, SiteGroup.REGULAR)
        val c = stats(results, SiteGroup.RESTRICTED)
        val groups = buildList {
            if (includeWhitelist) add(a)
            if (includeRegular) add(b)
            if (includeRestricted) add(c)
        }
        return compute(a, b, c, vpnActive, groups, includeWhitelist, includeRegular, includeRestricted)
    }

    fun compute(
        a: GroupStats,
        b: GroupStats,
        c: GroupStats,
        vpnActive: Boolean,
        groups: List<GroupStats> = listOf(a, b, c),
        includeWhitelist: Boolean = true,
        includeRegular: Boolean = true,
        includeRestricted: Boolean = true,
    ): Verdict {
        val aOk = includeWhitelist && a.rate >= OK
        val aNone = !includeWhitelist || a.rate <= NONE
        val aPartial = includeWhitelist && !aOk && !aNone

        val bOk = includeRegular && b.rate >= OK
        val bNone = !includeRegular || b.rate <= NONE_STRICT

        val cOk = includeRestricted && c.rate >= OK
        val cNone = !includeRestricted || c.rate <= NONE_STRICT

        val noInternet = aNone && bNone && cNone &&
            (!includeWhitelist || a.total > 0) &&
            (listOf(a, b, c).sumOf { it.total } > 0)

        val underlying = when {
            noInternet -> VerdictKind.NO_INTERNET
            aOk && bNone && cNone -> VerdictKind.WHITELIST_MODE
            aOk && bOk && cNone -> VerdictKind.NORMAL
            (aOk || aPartial) && cOk -> VerdictKind.ABROAD_OR_BYPASS
            else -> VerdictKind.PARTIAL
        }

        val displayed = if (vpnActive && underlying != VerdictKind.NO_INTERNET) {
            VerdictKind.VPN_ACTIVE
        } else {
            underlying
        }

        val reasons = buildReasons(a, b, c, underlying, vpnActive)
        val confidence = confidenceFor(underlying, a, b, c)
        return Verdict(
            kind = displayed,
            underlyingKind = underlying,
            confidence = confidence,
            reasons = reasons,
            hintKey = hintKey(displayed, underlying),
            groupStats = groups,
            vpnDistorts = vpnActive && underlying != VerdictKind.NO_INTERNET,
        )
    }

    private fun stats(results: List<SiteCheckResult>, group: SiteGroup): GroupStats {
        val slice = results.filter { it.group == group }
        val success = slice.count { it.isSuccess }
        val latencies = slice.mapNotNull { it.latencyMs }
        return GroupStats(
            group = group,
            total = slice.size,
            success = success,
            avgLatencyMs = if (latencies.isEmpty()) null else latencies.average().toLong(),
        )
    }

    private fun buildReasons(
        a: GroupStats,
        b: GroupStats,
        c: GroupStats,
        kind: VerdictKind,
        vpnActive: Boolean,
    ): List<String> {
        val reasons = mutableListOf<String>()
        reasons += "A ${pct(a)} · B ${pct(b)} · C ${pct(c)}"
        when (kind) {
            VerdictKind.NORMAL -> reasons += "allowlist_and_regular_open"
            VerdictKind.WHITELIST_MODE -> reasons += "allowlist_only"
            VerdictKind.NO_INTERNET -> reasons += "all_groups_down"
            VerdictKind.ABROAD_OR_BYPASS -> reasons += "restricted_group_open"
            VerdictKind.PARTIAL -> reasons += mixedReason(a, b, c)
            else -> Unit
        }
        if (vpnActive) reasons += "vpn_active"
        return reasons
    }

    private fun mixedReason(a: GroupStats, b: GroupStats, c: GroupStats): String {
        val weak = buildList {
            if (a.rate in NONE..OK) add("whitelist")
            if (b.rate in NONE_STRICT..OK) add("regular")
            if (c.rate in NONE_STRICT..OK) add("restricted")
        }
        return "mixed:" + weak.joinToString(",")
    }

    private fun confidenceFor(
        kind: VerdictKind,
        a: GroupStats,
        b: GroupStats,
        c: GroupStats,
    ): Float {
        val spread = when (kind) {
            VerdictKind.NO_INTERNET -> 1f - maxOf(a.rate, b.rate, c.rate)
            VerdictKind.WHITELIST_MODE -> (a.rate + (1f - b.rate) + (1f - c.rate)) / 3f
            VerdictKind.NORMAL -> (a.rate + b.rate + (1f - c.rate)) / 3f
            VerdictKind.ABROAD_OR_BYPASS -> (c.rate + maxOf(a.rate, 0.4f)) / 2f
            VerdictKind.PARTIAL -> 0.45f
            else -> 0.3f
        }
        return spread.coerceIn(0.2f, 0.99f)
    }

    private fun hintKey(displayed: VerdictKind, underlying: VerdictKind): String {
        return when (displayed) {
            VerdictKind.VPN_ACTIVE -> "hint_vpn"
            VerdictKind.NORMAL -> "hint_normal"
            VerdictKind.WHITELIST_MODE -> "hint_whitelist"
            VerdictKind.NO_INTERNET -> "hint_no_internet"
            VerdictKind.ABROAD_OR_BYPASS -> "hint_abroad"
            else -> "hint_partial"
        }.let { if (displayed == VerdictKind.VPN_ACTIVE) it else hintFor(underlying) }
    }

    private fun hintFor(kind: VerdictKind): String = when (kind) {
        VerdictKind.NORMAL -> "hint_normal"
        VerdictKind.WHITELIST_MODE -> "hint_whitelist"
        VerdictKind.NO_INTERNET -> "hint_no_internet"
        VerdictKind.ABROAD_OR_BYPASS -> "hint_abroad"
        VerdictKind.VPN_ACTIVE -> "hint_vpn"
        else -> "hint_partial"
    }

    private fun pct(stats: GroupStats): String {
        if (stats.total == 0) return "n/a"
        return "${(stats.rate * 100).toInt()}%"
    }

    companion object {
        const val OK = 0.6f
        const val NONE = 0.15f
        const val NONE_STRICT = 0.3f
    }
}
