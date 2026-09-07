package ru.whitelist.pulse.monitor

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.notify.StatusIndicator
import ru.whitelist.pulse.ui.ProbeCoordinator
import javax.inject.Inject

@AndroidEntryPoint
class PulseMonitorService : Service() {

    @Inject
    lateinit var coordinator: ProbeCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        StatusIndicator.ensureChannel(this)
        coordinator.start()
        startAsForeground(StatusIndicator.displayedKind(coordinator.state.value.verdict), scanning = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scanning = coordinator.state.value.scanning
        startAsForeground(StatusIndicator.displayedKind(coordinator.state.value.verdict), scanning)
        if (!loopStarted) {
            loopStarted = true
            scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private fun startAsForeground(kind: VerdictKind, scanning: Boolean) {
        val notification = StatusIndicator.build(this, kind, scanning)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                StatusIndicator.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(StatusIndicator.NOTIFICATION_ID, notification)
        }
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            val startedAt = SystemClock.elapsedRealtime()
            val shownKind = StatusIndicator.displayedKind(coordinator.state.value.verdict)
            StatusIndicator.update(this, shownKind, scanning = true)
            coordinator.run(compact = true)?.join()
            val resultKind = StatusIndicator.displayedKind(coordinator.state.value.verdict)
            StatusIndicator.update(this, resultKind, scanning = false)
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            delay((INTERVAL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    override fun onDestroy() {
        loopStarted = false
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    private companion object {
        const val INTERVAL_MS = 60_000L
    }
}
