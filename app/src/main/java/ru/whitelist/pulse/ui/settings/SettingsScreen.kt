package ru.whitelist.pulse.ui.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.whitelist.pulse.BuildConfig
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.AppLanguage
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.ThemeMode
import ru.whitelist.pulse.ui.components.PulseCard

@Composable
fun SettingsScreen(
    settings: ProbeSettings,
    onChange: (ProbeSettings) -> Unit,
    onClearHistory: () -> Unit,
    onExport: () -> Unit,
    onPickLists: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardBlock(stringResource(R.string.settings_theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { onChange(settings.copy(theme = mode)) },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        CardBlock(stringResource(R.string.settings_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.language == AppLanguage.RU,
                    onClick = { onChange(settings.copy(language = AppLanguage.RU)) },
                    label = { Text(stringResource(R.string.lang_ru)) },
                )
                FilterChip(
                    selected = settings.language == AppLanguage.EN,
                    onClick = { onChange(settings.copy(language = AppLanguage.EN)) },
                    label = { Text(stringResource(R.string.lang_en)) },
                )
            }
        }
        CardBlock(stringResource(R.string.settings_autocheck)) {
            SwitchRow(stringResource(R.string.settings_autocheck), settings.autoCheckOnLaunch) {
                onChange(settings.copy(autoCheckOnLaunch = it))
            }
            SwitchRow(stringResource(R.string.settings_whitelist_only), settings.whitelistOnlyMode) {
                onChange(settings.copy(whitelistOnlyMode = it))
            }
            SwitchRow(stringResource(R.string.settings_notify), settings.notifyOnResult) {
                onChange(settings.copy(notifyOnResult = it))
            }
            Text(stringResource(R.string.settings_interval), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to R.string.interval_off, 30 to R.string.interval_30s, 60 to R.string.interval_1m, 300 to R.string.interval_5m).forEach { (sec, res) ->
                    FilterChip(
                        selected = settings.foregroundIntervalSec == sec,
                        onClick = { onChange(settings.copy(foregroundIntervalSec = sec)) },
                        label = { Text(stringResource(res)) },
                    )
                }
            }
        }
        CardBlock(stringResource(R.string.settings_timeout)) {
            Text("${settings.timeoutSec}")
            Slider(
                value = settings.timeoutSec.toFloat(),
                onValueChange = { onChange(settings.copy(timeoutSec = it.toInt().coerceIn(2, 8))) },
                valueRange = 2f..8f,
                steps = 5,
            )
            Text(stringResource(R.string.settings_parallel))
            Text("${settings.parallelLimit}")
            Slider(
                value = settings.parallelLimit.toFloat(),
                onValueChange = { onChange(settings.copy(parallelLimit = it.toInt().coerceIn(2, 8))) },
                valueRange = 2f..8f,
                steps = 5,
            )
        }
        CardBlock(stringResource(R.string.settings_groups)) {
            SwitchRow(stringResource(R.string.group_whitelist), settings.includeWhitelist) {
                onChange(settings.copy(includeWhitelist = it))
            }
            SwitchRow(stringResource(R.string.group_regular), settings.includeRegular) {
                onChange(settings.copy(includeRegular = it))
            }
            SwitchRow(stringResource(R.string.group_restricted), settings.includeRestricted) {
                onChange(settings.copy(includeRestricted = it))
            }
            SwitchRow(stringResource(R.string.group_custom), settings.includeCustom) {
                onChange(settings.copy(includeCustom = it))
            }
        }
        CardBlock(stringResource(R.string.settings_custom)) {
            OutlinedButton(onClick = onExport) { Text(stringResource(R.string.action_export)) }
            OutlinedButton(onClick = onClearHistory) { Text(stringResource(R.string.action_clear_history)) }
            OutlinedButton(onClick = onPickLists) { Text(stringResource(R.string.settings_update_lists)) }
            Text(stringResource(R.string.lists_unofficial_note), style = MaterialTheme.typography.bodyMedium)
        }
        CardBlock(stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.disclaimer_full))
            Text(stringResource(R.string.not_official), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CardBlock(title: String, content: @Composable () -> Unit) {
    PulseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
