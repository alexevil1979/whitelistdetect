package ru.whitelist.pulse.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.whitelist.pulse.data.local.datastore.SettingsDataStore
import ru.whitelist.pulse.domain.model.ProbeSettings
import ru.whitelist.pulse.domain.model.VerdictKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistAlarmController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var alarmJob: Job? = null
    private var alarming = false
    private var settingsWatchStarted = false

    fun start() {
        if (settingsWatchStarted) return
        settingsWatchStarted = true
        scope.launch {
            settingsStore.settings.collect { settings ->
                if (!settings.whitelistAlarmEnabled) stop()
            }
        }
    }

    fun onVerdict(kind: VerdictKind, settings: ProbeSettings) {
        scope.launch {
            mutex.withLock {
                val whitelist = kind == VerdictKind.WHITELIST_MODE
                if (!settings.whitelistAlarmEnabled || !whitelist) {
                    stopLocked()
                    return@withLock
                }
                if (alarming) return@withLock
                alarming = true
                val minutes = settings.whitelistAlarmMinutes.coerceIn(1, 60)
                alarmJob = scope.launch {
                    try {
                        repeat(minutes) { index ->
                            if (!isActive) return@launch
                            playSignal()
                            if (index < minutes - 1) delay(60_000L)
                        }
                    } finally {
                        mutex.withLock {
                            alarming = false
                            alarmJob = null
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        scope.launch { mutex.withLock { stopLocked() } }
    }

    private fun stopLocked() {
        alarmJob?.cancel()
        alarmJob = null
        alarming = false
    }

    private fun playSignal() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = false
            }
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            scope.launch {
                delay(2_500)
                runCatching { if (ringtone.isPlaying) ringtone.stop() }
            }
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.takeIf { it.hasVibrator() }?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 400, 200, 400), -1)
                }
            }
        }
    }
}
