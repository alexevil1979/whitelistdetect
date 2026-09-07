package ru.whitelist.pulse.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import ru.whitelist.pulse.data.vpn.VpnDetector
import ru.whitelist.pulse.domain.model.DnsLookupResult
import ru.whitelist.pulse.domain.model.DnsServerInfo
import ru.whitelist.pulse.domain.model.GeoInfo
import ru.whitelist.pulse.domain.model.NetworkSnapshot
import ru.whitelist.pulse.domain.model.TransportKind
import ru.whitelist.pulse.domain.model.VpnPresence
import ru.whitelist.pulse.domain.repository.GeoRepository
import ru.whitelist.pulse.domain.repository.NetworkRepository
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnDetector: VpnDetector,
    private val geoRepository: GeoRepository,
) : NetworkRepository {

    @Volatile
    private var cachedGeo: GeoInfo? = null

    override fun observeSnapshot(): Flow<NetworkSnapshot> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshotBlocking(withGeo = false))
            }

            override fun onLost(network: Network) {
                trySend(snapshotBlocking(withGeo = false))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(snapshotBlocking(withGeo = false))
            }
        }
        runCatching { cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback) }
        trySend(snapshotBlocking(withGeo = false))
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    override suspend fun currentSnapshot(): NetworkSnapshot = withContext(Dispatchers.IO) {
        snapshotBlocking(withGeo = true)
    }

    override suspend fun lookupDns(hosts: List<String>): List<DnsLookupResult> = withContext(Dispatchers.IO) {
        hosts.map { host ->
            val start = System.currentTimeMillis()
            try {
                val addresses = InetAddress.getAllByName(host).mapNotNull { it.hostAddress }
                DnsLookupResult(host, addresses, System.currentTimeMillis() - start, null)
            } catch (t: Throwable) {
                val message = t.message.orEmpty()
                val error = when {
                    "Unable to resolve" in message || t is java.net.UnknownHostException -> "NXDOMAIN"
                    "timed out" in message.lowercase() -> "TIMEOUT"
                    else -> t.javaClass.simpleName
                }
                DnsLookupResult(host, emptyList(), System.currentTimeMillis() - start, error)
            }
        }
    }

    private fun snapshotBlocking(withGeo: Boolean): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val lp = network?.let { cm.getLinkProperties(it) }
        val transport = when {
            caps == null -> TransportKind.UNKNOWN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportKind.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportKind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> TransportKind.VPN
            else -> TransportKind.UNKNOWN
        }
        val vpn = runCatching {
            kotlinx.coroutines.runBlocking { vpnDetector.detect() }
        }.getOrElse { VpnPresence(false, emptyList(), null, emptyList()) }
        val geo = if (withGeo) {
            runCatching {
                kotlinx.coroutines.runBlocking { geoRepository.lookup() }
            }.getOrNull()?.also { cachedGeo = it }
        } else {
            cachedGeo
        }
        return NetworkSnapshot(
            transport = transport,
            hasValidatedInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            captivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            ssid = wifiSsid(caps),
            operatorName = runCatching { tm?.networkOperatorName }.getOrNull()?.takeIf { !it.isNullOrBlank() },
            simCountryIso = simCountry(tm),
            networkCountryIso = runCatching { tm?.networkCountryIso }.getOrNull()?.uppercase()?.ifBlank { null },
            dns = DnsServerInfo(
                servers = lp?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
                privateDns = lp?.privateDnsServerName,
            ),
            vpn = vpn,
            geo = geo,
            timestampEpochMs = System.currentTimeMillis(),
        )
    }

    private fun simCountry(tm: TelephonyManager?): String? {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                sm?.activeSubscriptionInfoList?.firstOrNull { !it.countryIso.isNullOrBlank() }
                    ?.countryIso
                    ?.uppercase()
                    ?.let { return it }
            }
        }
        return runCatching { tm?.simCountryIso }.getOrNull()?.uppercase()?.ifBlank { null }
    }

    @Suppress("DEPRECATION")
    private fun wifiSsid(caps: NetworkCapabilities?): String? {
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val raw = runCatching { wm.connectionInfo?.ssid }.getOrNull() ?: return null
        val ssid = raw.trim('"')
        return ssid.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
    }
}
