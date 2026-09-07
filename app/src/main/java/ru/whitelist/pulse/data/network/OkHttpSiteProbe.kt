package ru.whitelist.pulse.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.whitelist.pulse.domain.model.ProbeStatus
import ru.whitelist.pulse.domain.model.SiteCheckResult
import ru.whitelist.pulse.domain.model.SiteEndpoint
import ru.whitelist.pulse.domain.usecase.BlockPageClassifier
import timber.log.Timber
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.math.roundToLong

@Singleton
class OkHttpSiteProbe @Inject constructor(
    private val client: OkHttpClient,
) : SiteProbe {

    override suspend fun probe(endpoint: SiteEndpoint, timeoutSec: Int): SiteCheckResult {
        val started = System.currentTimeMillis()
        val internals = withContext(Dispatchers.IO) {
            try {
                runCatching { probeOnce(endpoint, timeoutSec) }
                    .recoverCatching { probeOnce(endpoint, timeoutSec) }
                    .getOrElse { classifyFailure(it, started) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
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

    private suspend fun probeOnce(endpoint: SiteEndpoint, timeoutSec: Int): ProbeInternals {
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

    private suspend fun execute(client: OkHttpClient, url: String, method: String): ProbeInternals {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .method(method, null)
            .build()
        val start = System.nanoTime()
        return try {
            val call = client.newCall(request)
            val response = suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { call.cancel() }
                try {
                    cont.resume(call.execute())
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWith(Result.failure(t))
                }
            }
            response.use { http ->
                val snippet = if (method == "GET") {
                    http.body?.source()?.let { source ->
                        source.request(MAX_BODY)
                        runCatching { source.buffer.clone().readUtf8() }.getOrNull()
                    }
                } else {
                    null
                }
                val code = http.code
                val httpMs = elapsedMs(start)
                val blocked = BlockPageClassifier.isBlockPage(snippet, code)
                val ok = code in 200..399 && !blocked
                ProbeInternals(
                    dnsMs = null,
                    connectMs = null,
                    httpMs = httpMs,
                    status = when {
                        blocked -> ProbeStatus.UNAVAILABLE
                        ok -> ProbeStatus.AVAILABLE
                        else -> ProbeStatus.HTTP_ERROR
                    },
                    ip = null,
                    code = code,
                    note = when {
                        blocked -> "block-page"
                        ok -> null
                        else -> "HTTP $code"
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    "canceled" in message || "cancelled" in message -> ProbeStatus.UNAVAILABLE
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
