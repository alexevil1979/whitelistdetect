package ru.whitelist.pulse.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ru.whitelist.pulse.data.local.dao.BundledOverrideDao
import ru.whitelist.pulse.data.local.entity.BundledOverrideEntity
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.repository.EndpointRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetEndpointRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val bundledDao: BundledOverrideDao,
) : EndpointRepository {

    private val refresh = MutableStateFlow(0)

    override fun observeEndpoints(): Flow<List<SiteEndpoint>> =
        refresh.map { load() }

    override suspend fun endpoints(): List<SiteEndpoint> = load()

    override suspend fun dnsControls(): List<String> {
        val override = bundledDao.get()?.json
        val raw = override ?: context.assets.open("endpoints.json").bufferedReader().use { it.readText() }
        return runCatching {
            json.decodeFromString(EndpointsFile.serializer(), raw).dnsControls
        }.getOrDefault(emptyList())
    }

    override suspend fun replaceBundledFromJson(jsonText: String) {
        json.decodeFromString(EndpointsFile.serializer(), jsonText).toEndpoints()
        bundledDao.upsert(
            BundledOverrideEntity(json = jsonText, updatedAt = System.currentTimeMillis()),
        )
        refresh.value = refresh.value + 1
    }

    private suspend fun load(): List<SiteEndpoint> {
        val override = bundledDao.get()?.json
        val raw = override ?: runCatching {
            context.assets.open("endpoints.json").bufferedReader().use { it.readText() }
        }.getOrElse {
            Timber.e(it, "Cannot read endpoints.json")
            return emptyList()
        }
        return runCatching {
            json.decodeFromString(EndpointsFile.serializer(), raw).toEndpoints()
        }.onFailure { Timber.e(it, "Cannot parse endpoints") }.getOrDefault(emptyList())
    }
}
