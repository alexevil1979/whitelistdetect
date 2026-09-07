package ru.whitelist.pulse.data.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.whitelist.pulse.data.network.OkHttpSiteProbe
import ru.whitelist.pulse.domain.model.GeoInfo
import ru.whitelist.pulse.domain.repository.GeoRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoIpDataSource @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : GeoRepository {

    private val mutex = Mutex()
    private var cached: GeoInfo? = null
    private var cachedAt: Long = 0L

    override suspend fun lookup(): GeoInfo = mutex.withLock {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < SESSION_TTL) return it }
        val fresh = withContext(Dispatchers.IO) { fetch() }
        cached = fresh
        cachedAt = now
        fresh
    }

    private fun fetch(): GeoInfo {
        val ipv4 = getText("https://api.ipify.org?format=json")
            ?.let { json.parseToJsonElement(it).jsonObject.string("ip") }
        val ipv6 = getText("https://api6.ipify.org?format=json")
            ?.let { json.parseToJsonElement(it).jsonObject.string("ip") }
            ?.takeIf { it.contains(':') }
        val providers = listOf(
            "https://ipapi.co/json/" to ::fromIpApi,
            "https://ipinfo.io/json" to ::fromIpInfo,
            "https://ifconfig.co/json" to ::fromIfconfig,
        )
        for ((url, parser) in providers) {
            val body = getText(url) ?: continue
            runCatching { parser(json.parseToJsonElement(body).jsonObject, ipv4).copy(ipv6 = ipv6) }
                .onSuccess { return it }
                .onFailure { Timber.w(it, "GeoIP parse failed for %s", url) }
        }
        return GeoInfo(ipv4, ipv6, null, null, null, null, null, null, "ipify")
    }

    private fun fromIpApi(obj: JsonObject, fallbackIp: String?): GeoInfo = GeoInfo(
        ipv4 = obj.string("ip") ?: fallbackIp,
        ipv6 = null,
        countryCode = obj.string("country_code") ?: obj.string("country"),
        countryName = obj.string("country_name"),
        region = obj.string("region"),
        city = obj.string("city"),
        asn = obj.string("asn"),
        org = obj.string("org") ?: obj.string("org_name"),
        source = "ipapi.co",
    )

    private fun fromIpInfo(obj: JsonObject, fallbackIp: String?): GeoInfo = GeoInfo(
        ipv4 = obj.string("ip") ?: fallbackIp,
        ipv6 = null,
        countryCode = obj.string("country"),
        countryName = obj.string("country"),
        region = obj.string("region"),
        city = obj.string("city"),
        asn = obj.string("org")?.substringBefore(' '),
        org = obj.string("org"),
        source = "ipinfo.io",
    )

    private fun fromIfconfig(obj: JsonObject, fallbackIp: String?): GeoInfo = GeoInfo(
        ipv4 = obj.string("ip") ?: fallbackIp,
        ipv6 = null,
        countryCode = obj.string("country_iso"),
        countryName = obj.string("country"),
        region = obj.string("region_name"),
        city = obj.string("city"),
        asn = obj.string("asn_org") ?: obj["asn"]?.toString(),
        org = obj.string("asn_org"),
        source = "ifconfig.co",
    )

    private fun getText(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OkHttpSiteProbe.USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.onFailure { Timber.w(it, "GeoIP request failed %s", url) }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val SESSION_TTL = 30 * 60 * 1000L
    }
}
