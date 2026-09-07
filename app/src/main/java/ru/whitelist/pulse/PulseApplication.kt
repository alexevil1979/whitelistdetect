package ru.whitelist.pulse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.whitelist.pulse.domain.model.AppLanguage
import ru.whitelist.pulse.domain.repository.SettingsRepository
import ru.whitelist.pulse.monitor.MonitorController
import ru.whitelist.pulse.notify.StatusIndicator
import ru.whitelist.pulse.notify.WhitelistAlarmController
import ru.whitelist.pulse.ui.ProbeCoordinator
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PulseApplication : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var coordinator: ProbeCoordinator

    @Inject
    lateinit var whitelistAlarm: WhitelistAlarmController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannel()
        StatusIndicator.ensureChannel(this)
        coordinator.start()
        whitelistAlarm.start()
        scope.launch {
            settingsRepository.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect { language ->
                    val tag = when (language) {
                        AppLanguage.EN -> "en"
                        AppLanguage.TH -> "th"
                        AppLanguage.FA -> "fa"
                        AppLanguage.ZH -> "zh-CN"
                        AppLanguage.RU -> "ru"
                    }
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
        }
        scope.launch {
            settingsRepository.settings
                .map { it.backgroundMonitor }
                .distinctUntilChanged()
                .collect { enabled ->
                    MonitorController.sync(this@PulseApplication, enabled)
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        const val CHANNEL_RESULTS = "check_results"
    }
}
