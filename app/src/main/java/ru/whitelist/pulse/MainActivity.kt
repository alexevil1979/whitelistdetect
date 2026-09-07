package ru.whitelist.pulse

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.whitelist.pulse.domain.model.ThemeMode
import ru.whitelist.pulse.domain.repository.SettingsRepository
import ru.whitelist.pulse.monitor.MonitorController
import ru.whitelist.pulse.ui.navigation.PulseRoot
import ru.whitelist.pulse.ui.theme.PulseTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeResumeMonitor(intent?.getBooleanExtra(EXTRA_RESUME_MONITOR, false) == true)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = ru.whitelist.pulse.domain.model.ProbeSettings(),
            )
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PulseTheme(darkTheme = dark, dynamicColor = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    PulseRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeResumeMonitor(intent.getBooleanExtra(EXTRA_RESUME_MONITOR, false))
    }

    private fun maybeResumeMonitor(requested: Boolean) {
        if (!requested) return
        MonitorController.sync(applicationContext, true)
    }

    companion object {
        const val EXTRA_RESUME_MONITOR = "resume_monitor"
    }
}
