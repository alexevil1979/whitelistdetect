package ru.whitelist.pulse.domain.usecase

import ru.whitelist.pulse.domain.model.GeoInfo
import ru.whitelist.pulse.domain.repository.GeoRepository
import javax.inject.Inject

class LookupGeoIp @Inject constructor(
    private val geoRepository: GeoRepository,
) {
    suspend operator fun invoke(): GeoInfo = geoRepository.lookup()
}
