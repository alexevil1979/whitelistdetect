package ru.whitelist.pulse.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.ComparisonDelta
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.ui.ProbeUiState
import ru.whitelist.pulse.ui.components.ConnectionPulse
import ru.whitelist.pulse.ui.components.PulseCard
import ru.whitelist.pulse.ui.hintRes
import ru.whitelist.pulse.ui.labelRes
import ru.whitelist.pulse.ui.relativeTime
import ru.whitelist.pulse.ui.theme.PulseTheme
import ru.whitelist.pulse.ui.theme.accent
import ru.whitelist.pulse.ui.theme.soft
import ru.whitelist.pulse.ui.titleRes

@Composable
fun HomeScreen(
    state: ProbeUiState,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val kind = state.verdict?.kind ?: VerdictKind.IDLE
    var wasScanning by remember { mutableStateOf(false) }
    LaunchedEffect(state.scanning) {
        if (wasScanning && !state.scanning) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasScanning = state.scanning
    }
    PullToRefreshBox(
        isRefreshing = state.scanning,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheck()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.snapshot?.hasValidatedInternet == false && !state.scanning && kind != VerdictKind.IDLE) {
                OfflineBanner(onCheck)
            }
            VerdictHero(
                kind = kind,
                scanning = state.scanning,
                subtitle = buildSubtitle(context, state),
                hint = stringResource((state.verdict?.kind ?: VerdictKind.PARTIAL).hintRes()),
                vpnDistorts = state.verdict?.vpnDistorts == true,
            )
            ChipRow(state)
            GroupRings(state)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheck()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = context.getString(R.string.cd_check_now) },
                enabled = !state.scanning,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(if (state.scanning) R.string.verdict_scanning else R.string.action_check_now))
            }
            Text(
                text = stringResource(R.string.last_updated, relativeTime(context, state.lastCheckedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.comparison) {
                ComparisonDelta.BETTER -> Text(stringResource(R.string.comparison_better), color = kind.accent())
                ComparisonDelta.WORSE -> Text(stringResource(R.string.comparison_worse), color = MaterialTheme.colorScheme.error)
                ComparisonDelta.SAME -> Text(stringResource(R.string.comparison_same), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ComparisonDelta.NONE -> Unit
            }
            if (state.settings.whitelistOnlyMode) {
                CompactWhitelist(state)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OfflineBanner(onCheck: () -> Unit) {
    PulseCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.offline_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.offline_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCheck) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
fun VerdictHero(
    kind: VerdictKind,
    scanning: Boolean,
    subtitle: String,
    hint: String,
    vpnDistorts: Boolean,
) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shift by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    PulseCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "verdict" },
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(kind.soft(), MaterialTheme.colorScheme.surface),
                    ),
                )
                .then(
                    if (scanning) Modifier.background(kind.accent().copy(alpha = 0.05f + 0.08f * shift))
                    else Modifier,
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ConnectionPulse(
                        kind = kind,
                        scanning = scanning,
                        contentDescription = stringResource(R.string.cd_pulse),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(kind.titleRes()),
                            style = MaterialTheme.typography.headlineMedium,
                            color = kind.accent(),
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (vpnDistorts) {
                    Text(
                        text = stringResource(R.string.vpn_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = VerdictKind.VPN_ACTIVE.accent(),
                    )
                }
                Text(text = hint, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ChipRow(state: ProbeUiState) {
    val snapshot = state.snapshot
    val chips = buildList {
        add(
            if (snapshot?.vpn?.isActive == true) R.string.chip_vpn_on to null
            else R.string.chip_vpn_off to null,
        )
        val sim = snapshot?.simCountryIso?.ifBlank { "—" } ?: "—"
        val ip = snapshot?.geo?.countryCode?.ifBlank { "—" } ?: "—"
        add(R.string.chip_sim_vs_ip to arrayOf(sim, ip))
        val dns = snapshot?.dns?.servers?.firstOrNull() ?: snapshot?.dns?.privateDns ?: "—"
        add(R.string.chip_dns to arrayOf(dns))
        if (sim.equals("RU", true) && ip.isNotBlank() && ip != "—" && !ip.equals("RU", true)) {
            add(R.string.ip_not_ru to null)
        }
        if (sim.isNotBlank() && sim != "—" && !sim.equals("RU", true)) {
            add(R.string.sim_not_ru to null)
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips.size) { index ->
            val (res, args) = chips[index]
            FilterChip(
                selected = false,
                onClick = {},
                enabled = false,
                label = {
                    Text(if (args == null) stringResource(res) else stringResource(res, *args))
                },
            )
        }
    }
}

@Composable
private fun GroupRings(state: ProbeUiState) {
    val groups = listOf(SiteGroup.WHITELIST, SiteGroup.REGULAR, SiteGroup.RESTRICTED)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEach { group ->
            val stats = state.verdict?.groupStats?.find { it.group == group }
            val live = state.progress.groupCompleted[group]
            val progress = if (state.scanning && live != null && live.second > 0) {
                live.first.toFloat() / live.second.toFloat()
            } else {
                stats?.rate ?: 0f
            }
            PulseCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(56.dp),
                            color = (state.verdict?.kind ?: VerdictKind.IDLE).accent(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 6.dp,
                        )
                        Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        text = stringResource(group.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactWhitelist(state: ProbeUiState) {
    val stats = state.verdict?.groupStats?.find { it.group == SiteGroup.WHITELIST }
    PulseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.compact_dashboard), style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${stats?.success ?: 0}/${stats?.total ?: 0}",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

private fun buildSubtitle(context: android.content.Context, state: ProbeUiState): String {
    val snapshot = state.snapshot ?: return ""
    val parts = buildList {
        snapshot.operatorName?.let { add(it) }
        snapshot.ssid?.let { add(it) }
        add(context.getString(snapshot.transport.labelRes()))
        snapshot.geo?.ipv4?.let { add(it) }
        snapshot.geo?.countryName?.let { add(it) } ?: snapshot.geo?.countryCode?.let { add(it) }
        snapshot.geo?.city?.let { add(it) }
        snapshot.geo?.org?.let { add(it) } ?: snapshot.geo?.asn?.let { add(it) }
    }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

@Preview(showBackground = true, name = "Verdict card")
@Composable
private fun VerdictHeroPreview() {
    PulseTheme(darkTheme = false, dynamicColor = false) {
        VerdictHero(
            kind = VerdictKind.WHITELIST_MODE,
            scanning = false,
            subtitle = "MTS · CELLULAR · 85.26.1.2 · RU · Moscow",
            hint = "Белый список работает, обычные сайты нет — типичная картина ограничений на мобильной сети",
            vpnDistorts = false,
        )
    }
}

@Preview(showBackground = true, name = "Normal verdict")
@Composable
private fun NormalHeroPreview() {
    PulseTheme(darkTheme = true, dynamicColor = false) {
        VerdictHero(
            kind = VerdictKind.NORMAL,
            scanning = true,
            subtitle = "Wi-Fi · 77.88.8.8 · RU",
            hint = "Обычный интернет в РФ",
            vpnDistorts = true,
        )
    }
}
