package ru.whitelist.pulse.ui.checks

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.SiteSort
import ru.whitelist.pulse.ui.ProbeUiState
import ru.whitelist.pulse.ui.components.PulseCard
import ru.whitelist.pulse.ui.label
import ru.whitelist.pulse.ui.labelRes
import ru.whitelist.pulse.ui.noteRes
import ru.whitelist.pulse.ui.theme.PulseAmber
import ru.whitelist.pulse.ui.theme.PulseCoral
import ru.whitelist.pulse.ui.theme.PulseEmerald
import ru.whitelist.pulse.ui.theme.PulseGray
import ru.whitelist.pulse.ui.theme.PulseIndigo
import ru.whitelist.pulse.ui.theme.PulseViolet

@Composable
fun ChecksScreen(
    state: ProbeUiState,
    onCheckGroup: (SiteGroup) -> Unit,
    onCheckAll: () -> Unit,
    onAddSite: (String, String) -> Unit,
    onToggleSite: (SiteEndpoint) -> Unit,
    onDeleteSite: (String) -> Unit,
    onImport: (String) -> Unit,
    onImportFile: () -> Unit = {},
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SiteSort.NAME) }
    var preset by remember { mutableStateOf<String?>(null) }
    val groups = listOf(SiteGroup.WHITELIST, SiteGroup.REGULAR, SiteGroup.RESTRICTED, SiteGroup.CUSTOM)
    val currentGroup = groups[tab]
    val endpoints = (state.endpoints + state.customSites).filter { it.group == currentGroup }
    val filtered = endpoints
        .filter { preset == null || preset in it.tags || it.folder == preset }
        .filter { query.isBlank() || it.host.contains(query, true) }
        .sortedWith(sortComparator(sort, state.results))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            groups.forEachIndexed { index, group ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, groups.size),
                    label = { Text(stringResource(group.labelRes()), maxLines = 1) },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_sites)) },
            singleLine = true,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf("banks" to R.string.preset_banks, "gosuslugi" to R.string.preset_gosuslugi, "marketplaces" to R.string.preset_marketplaces, "media" to R.string.preset_media, "foreign" to R.string.preset_foreign)
            items(presets) { (id, res) ->
                FilterChip(selected = preset == id, onClick = { preset = if (preset == id) null else id }, label = { Text(stringResource(res)) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = sort == SiteSort.NAME, onClick = { sort = SiteSort.NAME }, label = { Text(stringResource(R.string.sort_name)) }) }
            item { FilterChip(selected = sort == SiteSort.STATUS, onClick = { sort = SiteSort.STATUS }, label = { Text(stringResource(R.string.sort_status)) }) }
            item { FilterChip(selected = sort == SiteSort.LATENCY, onClick = { sort = SiteSort.LATENCY }, label = { Text(stringResource(R.string.sort_latency)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCheckGroup(currentGroup) }, enabled = !state.uiScanning) {
                Text(stringResource(R.string.action_check_group))
            }
            Button(onClick = onCheckAll, enabled = !state.uiScanning) {
                Text(stringResource(R.string.action_check_all))
            }
        }
        if (currentGroup == SiteGroup.CUSTOM) {
            CustomEditor(onAddSite, onImport, onImportFile)
        }
        if (filtered.isEmpty()) {
            PulseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isFilter = query.isNotBlank() || preset != null
                    Text(
                        text = stringResource(
                            when {
                                currentGroup == SiteGroup.CUSTOM && !isFilter -> R.string.empty_custom_title
                                isFilter -> R.string.empty_filter_title
                                else -> R.string.empty_filter_title
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(
                            when {
                                currentGroup == SiteGroup.CUSTOM && !isFilter -> R.string.empty_custom_body
                                else -> R.string.empty_filter_body
                            },
                        ),
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { site ->
                    val result = state.results.find { it.endpointId == site.id }
                    SiteRow(
                        site = site,
                        result = result,
                        statusText = result?.status?.label(context, result.httpCode, result.errorNote)
                            ?: context.getString(R.string.status_idle),
                        isCustom = currentGroup == SiteGroup.CUSTOM,
                        onToggle = { onToggleSite(site.copy(enabled = !site.enabled)) },
                        onDelete = { onDeleteSite(site.id) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CustomEditor(
    onAddSite: (String, String) -> Unit,
    onImport: (String) -> Unit,
    onImportFile: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    PulseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.add_domain_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text(stringResource(R.string.folder_tag)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { onAddSite(host, tag); host = "" }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.action_add_site), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedTextField(
                value = bulk,
                onValueChange = { bulk = it },
                label = { Text(stringResource(R.string.import_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            OutlinedButton(onClick = { onImport(bulk); bulk = "" }) { Text(stringResource(R.string.action_import)) }
            OutlinedButton(onClick = onImportFile) { Text(stringResource(R.string.import_from_file)) }
        }
    }
}

@Composable
private fun SiteRow(
    site: SiteEndpoint,
    result: SiteCheckResult?,
    statusText: String,
    isCustom: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    PulseCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FaviconStub(site)
            Column(modifier = Modifier.weight(1f)) {
                Text(site.host, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        append(statusText)
                        result?.latencyMs?.let { append(" · ${it} ms") }
                        result?.resolvedIp?.let { append(" · $it") }
                        result?.checkedAtEpochMs?.let {
                            append(" · ")
                            append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)))
                        }
                        append(" · ")
                    } + stringResource(site.group.noteRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusDot(result?.status ?: ProbeStatus.IDLE)
            if (isCustom) {
                Switch(checked = site.enabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
fun FaviconStub(site: SiteEndpoint) {
    val color = when (site.group) {
        SiteGroup.WHITELIST -> PulseAmber
        SiteGroup.REGULAR -> PulseEmerald
        SiteGroup.RESTRICTED -> PulseViolet
        SiteGroup.CUSTOM -> PulseIndigo
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = site.host.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
    }
}

@Composable
private fun StatusDot(status: ProbeStatus) {
    val color = when (status) {
        ProbeStatus.AVAILABLE -> PulseEmerald
        ProbeStatus.SLOW -> PulseAmber
        ProbeStatus.CHECKING -> PulseIndigo
        ProbeStatus.IDLE -> PulseGray
        else -> PulseCoral
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = status.name },
    )
}

private fun sortComparator(sort: SiteSort, results: List<SiteCheckResult>): Comparator<SiteEndpoint> {
    val byId = results.associateBy { it.endpointId }
    return when (sort) {
        SiteSort.NAME -> compareBy { it.host }
        SiteSort.LATENCY -> compareBy { byId[it.id]?.latencyMs ?: Long.MAX_VALUE }
        SiteSort.STATUS -> compareBy { byId[it.id]?.status?.ordinal ?: 99 }
    }
}
