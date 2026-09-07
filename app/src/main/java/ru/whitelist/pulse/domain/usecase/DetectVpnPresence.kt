package ru.whitelist.pulse.domain.usecase

import ru.whitelist.pulse.domain.model.VpnPresence
import ru.whitelist.pulse.domain.repository.VpnRepository
import javax.inject.Inject

class DetectVpnPresence @Inject constructor(
    private val vpnRepository: VpnRepository,
) {
    suspend operator fun invoke(): VpnPresence = vpnRepository.detect()
}
