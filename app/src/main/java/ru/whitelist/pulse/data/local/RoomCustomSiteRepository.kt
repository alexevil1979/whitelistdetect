package ru.whitelist.pulse.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.whitelist.pulse.data.local.dao.CustomSiteDao
import ru.whitelist.pulse.data.local.entity.CustomSiteEntity
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.repository.CustomSiteRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCustomSiteRepository @Inject constructor(
    private val dao: CustomSiteDao,
) : CustomSiteRepository {

    override fun observe(): Flow<List<SiteEndpoint>> = dao.observe().map { list -> list.map { it.toDomain() } }

    override suspend fun add(host: String, tag: String, enabled: Boolean): SiteEndpoint {
        val entity = CustomSiteEntity(
            id = UUID.randomUUID().toString(),
            host = host,
            url = "https://$host",
            tag = tag,
            enabled = enabled,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun importHosts(hosts: List<String>, tag: String) {
        val existing = dao.all().map { it.host }.toSet()
        val now = System.currentTimeMillis()
        val entities = hosts.filter { it !in existing }.map { host ->
            CustomSiteEntity(
                id = UUID.randomUUID().toString(),
                host = host,
                url = "https://$host",
                tag = tag,
                enabled = true,
                createdAt = now,
            )
        }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }

    override suspend fun update(site: SiteEndpoint) {
        dao.upsert(
            CustomSiteEntity(
                id = site.id,
                host = site.host,
                url = site.url,
                tag = site.folder.ifBlank { site.tags.firstOrNull().orEmpty() },
                enabled = site.enabled,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun exportText(): String =
        dao.all().joinToString("\n") { it.host }

    private fun CustomSiteEntity.toDomain() = SiteEndpoint(
        id = id,
        host = host,
        url = url,
        group = SiteGroup.CUSTOM,
        enabled = enabled,
        tags = if (tag.isBlank()) emptyList() else listOf(tag),
        folder = tag,
    )
}
