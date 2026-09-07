package ru.whitelist.pulse.ui.network

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.CheckHistoryEntry
import ru.whitelist.pulse.domain.model.DnsLookupResult
import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.ui.ProbeUiState
import ru.whitelist.pulse.ui.buildShareReport
import ru.whitelist.pulse.ui.components.PulseCard
import ru.whitelist.pulse.ui.labelRes
import ru.whitelist.pulse.ui.titleRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkScreen(
    state: ProbeUiState,
    onShare: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snapshot = state.snapshot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(stringResource(R.string.network_interface)) {
            KeyValue(stringResource(R.string.nav_network), stringResource((snapshot?.transport ?: ru.whitelist.pulse.domain.model.TransportKind.UNKNOWN).labelRes()))
            KeyValue(stringResource(if (snapshot?.hasValidatedInternet == true) R.string.validated_yes else R.string.validated_no), "")
            if (snapshot?.metered == true) KeyValue(stringResource(R.string.metered), "")
            if (snapshot?.captivePortal == true) KeyValue(stringResource(R.string.captive), "")
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.change_network)) }
        }
        Section(stringResource(R.string.network_vpn_tunnel)) {
            KeyValue("TRANSPORT_VPN", if (snapshot?.vpn?.transportVpn == true) "yes" else "no")
            Text(
                if (snapshot?.vpn?.tunnelInterfaces.isNullOrEmpty()) stringResource(R.string.tun_none)
                else stringResource(R.string.tun_found, snapshot?.vpn?.tunnelInterfaces?.joinToString().orEmpty()),
            )
            Text(
                if (snapshot?.vpn?.httpProxy.isNullOrBlank()) stringResource(R.string.proxy_none)
                else stringResource(R.string.proxy_set, snapshot?.vpn?.httpProxy.orEmpty()),
            )
            snapshot?.vpn?.knownApps?.forEach { app ->
                KeyValue(app.label, stringResource(if (app.installed) R.string.installed else R.string.not_installed))
            }
            if (snapshot?.vpn?.isActive == true) {
                Text(stringResource(R.string.vpn_warning), color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Section(stringResource(R.string.network_geo)) {
            val geo = snapshot?.geo
            KeyValue(stringResource(R.string.public_ipv4), geo?.ipv4 ?: "—")
            KeyValue(stringResource(R.string.public_ipv6), geo?.ipv6 ?: "—")
            KeyValue(stringResource(R.string.geo_country), listOfNotNull(geo?.countryName, geo?.countryCode).joinToString(" · ").ifBlank { "—" })
            KeyValue(stringResource(R.string.geo_city), listOfNotNull(geo?.region, geo?.city).joinToString(", ").ifBlank { "—" })
            KeyValue(stringResource(R.string.geo_isp), listOfNotNull(geo?.org, geo?.asn).joinToString(" · ").ifBlank { "—" })
            KeyValue(stringResource(R.string.sim_country), snapshot?.simCountryIso ?: "—")
            KeyValue(stringResource(R.string.network_country), snapshot?.networkCountryIso ?: "—")
            val sim = snapshot?.simCountryIso.orEmpty()
            val ipCc = geo?.countryCode.orEmpty()
            if (sim.equals("RU", true) && ipCc.isNotBlank() && !ipCc.equals("RU", true)) {
                Text(stringResource(R.string.ip_not_ru), color = MaterialTheme.colorScheme.error)
            }
            if (sim.isNotBlank() && !sim.equals("RU", true)) {
                Text(stringResource(R.string.sim_not_ru), color = MaterialTheme.colorScheme.tertiary)
            }
            OutlinedButton(onClick = {
                geo?.ipv4?.let { clipboard.setText(AnnotatedString(it)) }
            }) { Text(stringResource(R.string.action_copy_ip)) }
        }
        Section(stringResource(R.string.network_dns)) {
            val servers = snapshot?.dns?.servers.orEmpty()
            Text(if (servers.isEmpty()) "—" else servers.joinToString())
            snapshot?.dns?.privateDns?.let { Text("Private DNS: $it") }
            state.dnsLookups.forEach { DnsRow(it) }
        }
        Section(stringResource(R.string.network_quality)) {
            listOf(SiteGroup.WHITELIST, SiteGroup.REGULAR, SiteGroup.RESTRICTED).forEach { group ->
                val stats = state.verdict?.groupStats?.find { it.group == group }
                val quality = when {
                    stats == null || stats.total == 0 -> "—"
                    stats.rate >= 0.6f && (stats.avgLatencyMs ?: 0) < 2000 -> stringResource(R.string.page_opens)
                    stats.rate >= 0.3f -> stringResource(R.string.page_hangs)
                    else -> stringResource(R.string.page_fails)
                }
                KeyValue(stringResource(group.labelRes()), "${stats?.avgLatencyMs ?: "—"} ms · $quality")
            }
        }
        Section(stringResource(R.string.network_history)) {
            if (state.history.isEmpty()) {
                Text(stringResource(R.string.empty_history_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.empty_history_body))
            } else {
                state.history.take(20).forEach { HistoryRow(it) }
            }
        }
        Section(stringResource(R.string.network_export)) {
            Button(onClick = { onShare(buildShareReport(context, state)) }) {
                Text(stringResource(R.string.action_share_report))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PulseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DnsRow(result: DnsLookupResult) {
    val note = when (result.error) {
        "NXDOMAIN" -> stringResource(R.string.dns_nxdomain)
        "TIMEOUT" -> stringResource(R.string.dns_timeout)
        null -> if (result.addresses.isEmpty()) stringResource(R.string.dns_nxdomain) else stringResource(R.string.dns_ok)
        else -> result.error
    }
    val spoof = result.addresses.any { it.startsWith("127.") || it.startsWith("0.0.0.0") || it == "::1" }
    KeyValue(result.host, "${result.addresses.joinToString().ifBlank { "—" }} · ${result.elapsedMs} ms · $note")
    if (spoof) Text(stringResource(R.string.dns_spoof), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun HistoryRow(entry: CheckHistoryEntry) {
    val time = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(entry.timestampEpochMs))
    KeyValue(
        time,
        "${stringResource(entry.verdictKind.titleRes())} · ${entry.networkType.name}${if (entry.vpnActive) " · VPN" else ""}",
    )
}
