package ru.whitelist.pulse.ui

import android.content.Context
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.TransportKind
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.ui.ProbeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun VerdictKind.titleRes(): Int = when (this) {
    VerdictKind.NORMAL -> R.string.verdict_normal
    VerdictKind.WHITELIST_MODE -> R.string.verdict_whitelist
    VerdictKind.NO_INTERNET -> R.string.verdict_no_internet
    VerdictKind.VPN_ACTIVE -> R.string.verdict_vpn
    VerdictKind.ABROAD_OR_BYPASS -> R.string.verdict_abroad
    VerdictKind.PARTIAL -> R.string.verdict_partial
    VerdictKind.SCANNING -> R.string.verdict_scanning
    VerdictKind.IDLE -> R.string.verdict_idle
}

fun VerdictKind.hintRes(): Int = when (this) {
    VerdictKind.NORMAL -> R.string.hint_normal
    VerdictKind.WHITELIST_MODE -> R.string.hint_whitelist
    VerdictKind.NO_INTERNET -> R.string.hint_no_internet
    VerdictKind.VPN_ACTIVE -> R.string.hint_vpn
    VerdictKind.ABROAD_OR_BYPASS -> R.string.hint_abroad
    else -> R.string.hint_partial
}

fun SiteGroup.labelRes(): Int = when (this) {
    SiteGroup.WHITELIST -> R.string.group_whitelist
    SiteGroup.REGULAR -> R.string.group_regular
    SiteGroup.RESTRICTED -> R.string.group_restricted
    SiteGroup.CUSTOM -> R.string.group_custom
}

fun SiteGroup.noteRes(): Int = when (this) {
    SiteGroup.WHITELIST -> R.string.note_whitelist
    SiteGroup.REGULAR -> R.string.note_regular
    SiteGroup.RESTRICTED -> R.string.note_restricted
    SiteGroup.CUSTOM -> R.string.note_custom
}

fun ProbeStatus.label(context: Context, httpCode: Int?, note: String? = null): String = when {
    this == ProbeStatus.UNAVAILABLE && note == "block-page" -> context.getString(R.string.status_block_page)
    this == ProbeStatus.AVAILABLE -> context.getString(R.string.status_available)
    this == ProbeStatus.UNAVAILABLE -> context.getString(R.string.status_unavailable)
    this == ProbeStatus.SLOW -> context.getString(R.string.status_slow)
    this == ProbeStatus.DNS_ERROR -> context.getString(R.string.status_dns)
    this == ProbeStatus.TIMEOUT -> context.getString(R.string.status_timeout)
    this == ProbeStatus.HTTP_ERROR -> context.getString(R.string.status_http, httpCode ?: 0)
    this == ProbeStatus.IDLE -> context.getString(R.string.status_idle)
    this == ProbeStatus.CHECKING -> context.getString(R.string.status_checking)
    this == ProbeStatus.TLS_ERROR || this == ProbeStatus.RESET -> context.getString(R.string.status_unavailable)
    else -> context.getString(R.string.status_unavailable)
}

fun TransportKind.labelRes(): Int = when (this) {
    TransportKind.WIFI -> R.string.transport_wifi
    TransportKind.CELLULAR -> R.string.transport_cellular
    TransportKind.ETHERNET -> R.string.transport_ethernet
    TransportKind.VPN -> R.string.transport_vpn
    TransportKind.UNKNOWN -> R.string.transport_unknown
}

fun relativeTime(context: Context, epochMs: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMs == null) return context.getString(R.string.never)
    val delta = (now - epochMs).coerceAtLeast(0)
    return when {
        delta < 8_000 -> context.getString(R.string.just_now)
        delta < 60_000 -> context.getString(R.string.seconds_ago, (delta / 1000).toInt())
        delta < 3_600_000 -> context.getString(R.string.minutes_ago, (delta / 60_000).toInt())
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}

fun buildShareReport(context: Context, state: ProbeUiState): String {
    val verdict = state.verdict
    val snapshot = state.snapshot
    val kind = verdict?.kind ?: VerdictKind.IDLE
    val sb = StringBuilder()
    sb.appendLine(context.getString(R.string.app_name))
    sb.appendLine(context.getString(kind.titleRes()))
    sb.appendLine(context.getString(R.string.disclaimer_short))
    snapshot?.geo?.let { geo ->
        sb.appendLine("${context.getString(R.string.public_ipv4)}: ${geo.ipv4 ?: "—"}")
        sb.appendLine("${context.getString(R.string.geo_country)}: ${geo.countryCode ?: "—"}")
    }
    sb.appendLine("VPN: ${if (snapshot?.vpn?.isActive == true) "yes" else "no"}")
    snapshot?.let { sb.appendLine("${context.getString(it.transport.labelRes())}") }
    verdict?.groupStats?.forEach { stats ->
        val pct = if (stats.total == 0) 0 else ((stats.rate * 100).toInt())
        sb.appendLine("${context.getString(stats.group.labelRes())}: $pct% (${stats.success}/${stats.total})")
    }
    return sb.toString()
}
