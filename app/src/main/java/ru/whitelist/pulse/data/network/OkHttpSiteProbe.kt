package ru.whitelist.pulse.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import timber.log.Timber
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.math.roundToLong

@Singleton
class OkHttpSiteProbe @Inject constructor(
    private val client: OkHttpClient,
) : SiteProbe {

    override suspend fun probe(endpoint: SiteEndpoint, timeoutSec: Int): SiteCheckResult {
        val started = System.currentTimeMillis()
        val internals = withContext(Dispatchers.IO) {
            runCatching { probeOnce(endpoint, timeoutSec) }
                .recoverCatching { probeOnce(endpoint, timeoutSec) }
                .getOrElse { classifyFailure(it, started) }
        }
        val total = internals.httpMs ?: internals.connectMs ?: internals.dnsMs
        val status = when {
            internals.status == ProbeStatus.AVAILABLE && (total ?: 0L) >= SLOW_MS -> ProbeStatus.SLOW
            else -> internals.status
        }
        return SiteCheckResult(
            endpointId = endpoint.id,
            host = endpoint.host,
            url = endpoint.url,
            group = endpoint.group,
            status = status,
            latencyMs = total,
            resolvedIp = internals.ip,
            httpCode = internals.code,
            checkedAtEpochMs = System.currentTimeMillis(),
            errorNote = internals.note,
        )
    }

    private fun probeOnce(endpoint: SiteEndpoint, timeoutSec: Int): ProbeInternals {
        val timeoutMs = timeoutSec.coerceIn(2, 8) * 1000L
        val dnsStart = System.nanoTime()
        val addresses = try {
            InetAddress.getAllByName(endpoint.host).toList()
        } catch (e: UnknownHostException) {
            return ProbeInternals(null, null, null, ProbeStatus.DNS_ERROR, null, null, e.message)
        } catch (e: SocketTimeoutException) {
            return ProbeInternals(elapsedMs(dnsStart), null, null, ProbeStatus.TIMEOUT, null, null, e.message)
        }
        val dnsMs = elapsedMs(dnsStart)
        val ip = addresses.firstOrNull()?.hostAddress

        val scoped = client.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs + 500, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val head = execute(scoped, endpoint.url, "HEAD")
        if (head.status == ProbeStatus.AVAILABLE || head.status == ProbeStatus.HTTP_ERROR) {
            return head.copy(dnsMs = dnsMs, ip = ip ?: head.ip)
        }
        val get = execute(scoped, endpoint.url, "GET")
        return get.copy(dnsMs = dnsMs, ip = ip ?: get.ip)
    }

    private fun execute(client: OkHttpClient, url: String, method: String): ProbeInternals {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .method(method, null)
            .build()
        val start = System.nanoTime()
        return try {
            client.newCall(request).execute().use { response ->
                if (method == "GET") {
                    response.body?.source()?.let { source ->
                        source.request(MAX_BODY)
                        source.buffer.clone().readByteArray()
                    }
                }
                val code = response.code
                val httpMs = elapsedMs(start)
                val ok = code in 200..399
                ProbeInternals(
                    dnsMs = null,
                    connectMs = null,
                    httpMs = httpMs,
                    status = if (ok) ProbeStatus.AVAILABLE else ProbeStatus.HTTP_ERROR,
                    ip = null,
                    code = code,
                    note = if (ok) null else "HTTP $code",
                )
            }
        } catch (t: Throwable) {
            classifyFailure(t, start)
        }
    }

    private fun classifyFailure(error: Throwable, startNanosOrMs: Long): ProbeInternals {
        val elapsed = if (startNanosOrMs > 1_000_000_000_000L) elapsedMs(startNanosOrMs) else {
            (System.currentTimeMillis() - startNanosOrMs).coerceAtLeast(0)
        }
        val status = when (error) {
            is UnknownHostException -> ProbeStatus.DNS_ERROR
            is SocketTimeoutException -> ProbeStatus.TIMEOUT
            is SSLException -> ProbeStatus.TLS_ERROR
            else -> {
                val message = error.message.orEmpty().lowercase()
                when {
                    "reset" in message || "connection refused" in message -> ProbeStatus.RESET
                    "timeout" in message || "timed out" in message -> ProbeStatus.TIMEOUT
                    "unable to resolve" in message || "unknown host" in message -> ProbeStatus.DNS_ERROR
                    else -> ProbeStatus.UNAVAILABLE
                }
            }
        }
        Timber.d(error, "Probe failed: %s", error.message)
        return ProbeInternals(null, elapsed, null, status, null, null, error.message)
    }

    private fun elapsedMs(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / 1_000_000.0).roundToLong().coerceAtLeast(1)

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val MAX_BODY = 8 * 1024L
        private const val SLOW_MS = 2000L
    }
}
