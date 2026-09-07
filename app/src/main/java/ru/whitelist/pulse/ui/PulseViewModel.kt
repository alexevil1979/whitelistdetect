package ru.whitelist.pulse.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.repository.SettingsRepository
import ru.whitelist.pulse.domain.usecase.ManageCustomSites
import ru.whitelist.pulse.data.local.AssetEndpointRepository
import ru.whitelist.pulse.domain.repository.HistoryRepository
import javax.inject.Inject

@HiltViewModel
class PulseViewModel @Inject constructor(
    application: Application,
    private val coordinator: ProbeCoordinator,
    private val settingsRepository: SettingsRepository,
    private val manageCustomSites: ManageCustomSites,
    private val endpointRepository: AssetEndpointRepository,
    private val historyRepository: HistoryRepository,
) : AndroidViewModel(application) {

    val state: StateFlow<ProbeUiState> = coordinator.state
    private var started = false

    fun bootstrap() {
        if (started) return
        started = true
        coordinator.start(viewModelScope)
        viewModelScope.launch {
            val settings = settingsRepository.current()
            if (settings.autoCheckOnLaunch) coordinator.run(viewModelScope)
        }
    }

    fun checkAll() = coordinator.run(viewModelScope)

    fun checkGroup(group: SiteGroup) = coordinator.run(viewModelScope, setOf(group))

    fun updateSettings(settings: ProbeSettings) {
        viewModelScope.launch { settingsRepository.update { settings } }
    }

    fun addSite(host: String, tag: String) {
        viewModelScope.launch { manageCustomSites.add(host, tag) }
    }

    fun toggleSite(site: SiteEndpoint) {
        viewModelScope.launch { manageCustomSites.update(site) }
    }

    fun deleteSite(id: String) {
        viewModelScope.launch { manageCustomSites.delete(id) }
    }

    fun importSites(raw: String) {
        viewModelScope.launch { manageCustomSites.importList(raw) }
    }

    fun exportSites(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(manageCustomSites.exportText()) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }

    fun importBundledLists(uri: Uri) {
        viewModelScope.launch {
            val json = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@launch
            runCatching { endpointRepository.replaceBundledFromJson(json) }
        }
    }
}
