package ru.whitelist.pulse.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.whitelist.pulse.MainActivity
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Android 15+ forbids starting dataSync foreground services from BOOT_COMPLETED.
 * After reboot we only remind the user to open the app; Application/MainActivity
 * then starts [PulseMonitorService] from an allowed context.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (settingsRepository.current().backgroundMonitor) {
                    postResumeHint(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun postResumeHint(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RESUME,
                    context.getString(R.string.notification_channel_resume),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_RESUME_MONITOR, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RESUME)
            .setSmallIcon(R.drawable.ic_status_circle_half)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.boot_resume_monitor))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_RESUME, notification)
    }

    private companion object {
        const val CHANNEL_RESUME = "monitor_resume"
        const val NOTIFICATION_RESUME = 18
    }
}
