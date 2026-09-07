package ru.whitelist.pulse.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.whitelist.pulse.PulseApplication
import ru.whitelist.pulse.R

object ProbeNotifier {
    fun notify(context: Context, verdict: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, PulseApplication.CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_tile_probe)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(verdict)
            .setStyle(NotificationCompat.BigTextStyle().bigText(verdict + "\n" + context.getString(R.string.disclaimer_short)))
            .setAutoCancel(true)
            .build()
        manager.notify(42, notification)
    }
}
