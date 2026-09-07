package ru.whitelist.pulse.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ru.whitelist.pulse.data.geo.GeoIpDataSource
import ru.whitelist.pulse.data.local.AppDatabase
import ru.whitelist.pulse.data.local.AssetEndpointRepository
import ru.whitelist.pulse.data.local.RoomCustomSiteRepository
import ru.whitelist.pulse.data.local.RoomHistoryRepository
import ru.whitelist.pulse.data.local.dao.BundledOverrideDao
import ru.whitelist.pulse.data.local.dao.CustomSiteDao
import ru.whitelist.pulse.data.local.dao.HistoryDao
import ru.whitelist.pulse.data.local.datastore.SettingsDataStore
import ru.whitelist.pulse.data.network.AndroidNetworkRepository
import ru.whitelist.pulse.data.network.OkHttpSiteProbe
import ru.whitelist.pulse.data.network.SiteProbe
import ru.whitelist.pulse.data.network.SiteProbeRepository
import ru.whitelist.pulse.data.vpn.VpnDetector
import ru.whitelist.pulse.domain.repository.CustomSiteRepository
import ru.whitelist.pulse.domain.repository.EndpointRepository
import ru.whitelist.pulse.domain.repository.GeoRepository
import ru.whitelist.pulse.domain.repository.HistoryRepository
import ru.whitelist.pulse.domain.repository.NetworkRepository
import ru.whitelist.pulse.domain.repository.ProbeRepository
import ru.whitelist.pulse.domain.repository.SettingsRepository
import ru.whitelist.pulse.domain.repository.VpnRepository
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pulse.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun customDao(db: AppDatabase): CustomSiteDao = db.customSites()

    @Provides
    fun historyDao(db: AppDatabase): HistoryDao = db.history()

    @Provides
    fun bundledDao(db: AppDatabase): BundledOverrideDao = db.bundled()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindModule {

    @Binds
    @Singleton
    abstract fun endpoints(impl: AssetEndpointRepository): EndpointRepository

    @Binds
    @Singleton
    abstract fun custom(impl: RoomCustomSiteRepository): CustomSiteRepository

    @Binds
    @Singleton
    abstract fun history(impl: RoomHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun settings(impl: SettingsDataStore): SettingsRepository

    @Binds
    @Singleton
    abstract fun network(impl: AndroidNetworkRepository): NetworkRepository

    @Binds
    @Singleton
    abstract fun geo(impl: GeoIpDataSource): GeoRepository

    @Binds
    @Singleton
    abstract fun vpn(impl: VpnDetector): VpnRepository

    @Binds
    @Singleton
    abstract fun probe(impl: OkHttpSiteProbe): SiteProbe

    @Binds
    @Singleton
    abstract fun probeRepo(impl: SiteProbeRepository): ProbeRepository
}
