package ru.whitelist.pulse.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.whitelist.pulse.domain.model.AppLanguage
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.ThemeMode
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<ProbeSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun current(): ProbeSettings = settings.first()

    override suspend fun update(transform: (ProbeSettings) -> ProbeSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[THEME] = next.theme.name
            prefs[LANGUAGE] = next.language.name
            prefs[AUTO] = next.autoCheckOnLaunch
            prefs[INTERVAL] = next.foregroundIntervalSec
            prefs[TIMEOUT] = next.timeoutSec
            prefs[PARALLEL] = next.parallelLimit
            prefs[INC_A] = next.includeWhitelist
            prefs[INC_B] = next.includeRegular
            prefs[INC_C] = next.includeRestricted
            prefs[INC_CUSTOM] = next.includeCustom
            prefs[WHITELIST_ONLY] = next.whitelistOnlyMode
            prefs[NOTIFY] = next.notifyOnResult
            prefs[BACKGROUND] = next.backgroundMonitor
            prefs[WL_ALARM] = next.whitelistAlarmEnabled
            prefs[WL_ALARM_MIN] = next.whitelistAlarmMinutes.coerceIn(1, 60)
        }
    }

    suspend fun saveLastVerdict(kind: VerdictKind, vpn: Boolean) {
        dataStore.edit {
            it[LAST_VERDICT] = kind.name
            it[LAST_VPN] = vpn
        }
    }

    suspend fun lastVerdict(): Pair<VerdictKind, Boolean>? {
        val prefs = dataStore.data.first()
        val raw = prefs[LAST_VERDICT] ?: return null
        val kind = runCatching { VerdictKind.valueOf(raw) }.getOrNull() ?: return null
        return kind to (prefs[LAST_VPN] ?: false)
    }

    private fun Preferences.toSettings(): ProbeSettings = ProbeSettings(
        theme = ThemeMode.entries.find { it.name == this[THEME] } ?: ThemeMode.SYSTEM,
        language = AppLanguage.entries.find { it.name == this[LANGUAGE] } ?: AppLanguage.RU,
        autoCheckOnLaunch = this[AUTO] ?: true,
        foregroundIntervalSec = this[INTERVAL] ?: 0,
        timeoutSec = this[TIMEOUT] ?: 4,
        parallelLimit = this[PARALLEL] ?: 6,
        includeWhitelist = this[INC_A] ?: true,
        includeRegular = this[INC_B] ?: true,
        includeRestricted = this[INC_C] ?: true,
        includeCustom = this[INC_CUSTOM] ?: true,
        whitelistOnlyMode = this[WHITELIST_ONLY] ?: false,
        notifyOnResult = this[NOTIFY] ?: false,
        backgroundMonitor = this[BACKGROUND] ?: true,
        whitelistAlarmEnabled = this[WL_ALARM] ?: false,
        whitelistAlarmMinutes = this[WL_ALARM_MIN] ?: 5,
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO = booleanPreferencesKey("auto_check")
        val INTERVAL = intPreferencesKey("interval_sec")
        val TIMEOUT = intPreferencesKey("timeout_sec")
        val PARALLEL = intPreferencesKey("parallel")
        val INC_A = booleanPreferencesKey("inc_a")
        val INC_B = booleanPreferencesKey("inc_b")
        val INC_C = booleanPreferencesKey("inc_c")
        val INC_CUSTOM = booleanPreferencesKey("inc_custom")
        val WHITELIST_ONLY = booleanPreferencesKey("whitelist_only")
        val NOTIFY = booleanPreferencesKey("notify")
        val BACKGROUND = booleanPreferencesKey("background_monitor")
        val WL_ALARM = booleanPreferencesKey("whitelist_alarm")
        val WL_ALARM_MIN = intPreferencesKey("whitelist_alarm_min")
        val LAST_VERDICT = stringPreferencesKey("last_verdict")
        val LAST_VPN = booleanPreferencesKey("last_vpn")
    }
}
