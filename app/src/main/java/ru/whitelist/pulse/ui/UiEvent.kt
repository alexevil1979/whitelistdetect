package ru.whitelist.pulse.ui

sealed interface UiEvent {
    data object SiteAdded : UiEvent
    data object InvalidDomain : UiEvent
    data object SiteRemoved : UiEvent
    data class SitesImported(val accepted: Int, val rejected: Int) : UiEvent
    data object HistoryCleared : UiEvent
    data object ListsUpdated : UiEvent
    data object ListsFailed : UiEvent
}
