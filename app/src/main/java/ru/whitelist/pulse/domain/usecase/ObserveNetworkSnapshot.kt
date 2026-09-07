package ru.whitelist.pulse.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.whitelist.pulse.domain.model.NetworkSnapshot
import ru.whitelist.pulse.domain.repository.NetworkRepository
import javax.inject.Inject

class ObserveNetworkSnapshot @Inject constructor(
    private val networkRepository: NetworkRepository,
) {
    operator fun invoke(): Flow<NetworkSnapshot> = networkRepository.observeSnapshot()
}
