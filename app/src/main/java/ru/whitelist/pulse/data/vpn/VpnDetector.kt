package ru.whitelist.pulse.data.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.whitelist.pulse.data.local.KnownVpnFile
import ru.whitelist.pulse.domain.model.InstalledVpnApp
import ru.whitelist.pulse.domain.model.VpnPresence
import ru.whitelist.pulse.domain.repository.VpnRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : VpnRepository {

    override suspend fun detect(): VpnPresence = withContext(Dispatchers.IO) {
        VpnPresence(
            transportVpn = hasVpnTransport(),
            tunnelInterfaces = tunnelInterfaces(),
            httpProxy = systemProxy(),
            knownApps = knownApps(),
        )
    }

    private fun hasVpnTransport(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun tunnelInterfaces(): List<String> {
        val names = mutableSetOf<String>()
        runCatching {
            File("/sys/class/net").listFiles()?.forEach { file ->
                val name = file.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("utun") ||
                    name.startsWith("ppp") || name.startsWith("wg")
                ) {
                    names += file.name
                }
            }
        }
        runCatching {
            NetworkInterfaceCompat.names().forEach { name ->
                val lower = name.lowercase()
                if (lower.startsWith("tun") || lower.startsWith("utun") ||
                    lower.startsWith("ppp") || lower.startsWith("wg")
                ) {
                    names += name
                }
            }
        }
        return names.sorted()
    }

    private fun systemProxy(): String? {
        val host = System.getProperty("http.proxyHost")?.trim().orEmpty()
        val port = System.getProperty("http.proxyPort")?.trim().orEmpty()
        if (host.isBlank()) return null
        return if (port.isBlank()) host else "$host:$port"
    }

    private fun knownApps(): List<InstalledVpnApp> {
        val raw = runCatching {
            context.assets.open("known_vpn_packages.json").bufferedReader().use { it.readText() }
        }.getOrElse {
            Timber.w(it, "Cannot read known VPN packages")
            return emptyList()
        }
        val file = runCatching { json.decodeFromString(KnownVpnFile.serializer(), raw) }.getOrNull()
            ?: return emptyList()
        val pm = context.packageManager
        return file.packages.map { dto ->
            val installed = runCatching {
                pm.getPackageInfo(dto.packageName, 0)
                true
            }.getOrDefault(false)
            InstalledVpnApp(dto.id, dto.label, dto.packageName, installed)
        }
    }
}

private object NetworkInterfaceCompat {
    fun names(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { it.name }
    }.getOrDefault(emptyList())
}
