package ru.whitelist.pulse.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.whitelist.pulse.data.local.dao.HistoryDao
import ru.whitelist.pulse.data.local.entity.CheckHistoryEntity
import ru.whitelist.pulse.domain.model.CheckHistoryEntry
import ru.whitelist.pulse.domain.model.TransportKind
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao,
) : HistoryRepository {

    override fun observe(limit: Int): Flow<List<CheckHistoryEntry>> =
        dao.observe(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun latest(limit: Int): List<CheckHistoryEntry> =
        dao.latest(limit).map { it.toDomain() }

    override suspend fun add(entry: CheckHistoryEntry) {
        dao.insert(entry.toEntity())
        dao.trim()
    }

    override suspend fun clear() = dao.clear()

    private fun CheckHistoryEntity.toDomain() = CheckHistoryEntry(
        id = id,
        timestampEpochMs = timestamp,
        verdictKind = runCatching { VerdictKind.valueOf(verdict) }.getOrDefault(VerdictKind.PARTIAL),
        confidence = confidence,
        networkType = runCatching { TransportKind.valueOf(networkType) }.getOrDefault(TransportKind.UNKNOWN),
        vpnActive = vpnActive,
        groupARate = groupARate,
        groupBRate = groupBRate,
        groupCRate = groupCRate,
        publicIp = publicIp,
        country = country,
    )

    private fun CheckHistoryEntry.toEntity() = CheckHistoryEntity(
        id = id,
        timestamp = timestampEpochMs,
        verdict = verdictKind.name,
        confidence = confidence,
        networkType = networkType.name,
        vpnActive = vpnActive,
        groupARate = groupARate,
        groupBRate = groupBRate,
        groupCRate = groupCRate,
        publicIp = publicIp,
        country = country,
    )
}
