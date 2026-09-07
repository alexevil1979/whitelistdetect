package ru.whitelist.pulse.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.repository.EndpointRepository
import ru.whitelist.pulse.domain.repository.HistoryRepository
import ru.whitelist.pulse.domain.repository.SettingsRepository
import ru.whitelist.pulse.domain.usecase.ManageCustomSites
import javax.inject.Inject

@HiltViewModel
class PulseViewModel @Inject constructor(
    application: Application,
    private val coordinator: ProbeCoordinator,
    private val settingsRepository: SettingsRepository,
    private val manageCustomSites: ManageCustomSites,
    private val endpointRepository: EndpointRepository,
    private val historyRepository: HistoryRepository,
) : AndroidViewModel(application) {

    val state: StateFlow<ProbeUiState> = coordinator.state
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
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
        viewModelScope.launch {
            val added = manageCustomSites.add(host, tag)
            _events.emit(if (added == null) UiEvent.InvalidDomain else UiEvent.SiteAdded)
        }
    }

    fun toggleSite(site: SiteEndpoint) {
        viewModelScope.launch { manageCustomSites.update(site) }
    }

    fun deleteSite(id: String) {
        viewModelScope.launch {
            manageCustomSites.delete(id)
            _events.emit(UiEvent.SiteRemoved)
        }
    }

    fun importSites(raw: String) {
        viewModelScope.launch {
            val parsed = manageCustomSites.importList(raw)
            _events.emit(UiEvent.SitesImported(parsed.hosts.size, parsed.rejected.size))
        }
    }

    fun importCustomFile(uri: Uri) {
        viewModelScope.launch {
            val text = readText(uri) ?: run {
                _events.emit(UiEvent.ListsFailed)
                return@launch
            }
            val parsed = manageCustomSites.importList(text)
            _events.emit(UiEvent.SitesImported(parsed.hosts.size, parsed.rejected.size))
        }
    }

    fun exportSites(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(manageCustomSites.exportText()) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
            _events.emit(UiEvent.HistoryCleared)
        }
    }

    fun importBundledLists(uri: Uri) {
        viewModelScope.launch {
            val json = readText(uri)
            if (json == null) {
                _events.emit(UiEvent.ListsFailed)
                return@launch
            }
            runCatching { endpointRepository.replaceBundledFromJson(json) }
                .onSuccess { _events.emit(UiEvent.ListsUpdated) }
                .onFailure { _events.emit(UiEvent.ListsFailed) }
        }
    }

    private fun readText(uri: Uri): String? =
        getApplication<Application>().contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }

    override fun onCleared() {
        coordinator.cancelProbe()
        super.onCleared()
    }
}
