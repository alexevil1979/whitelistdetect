package ru.whitelist.pulse.monitor

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

object MonitorController {
    fun sync(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val intent = Intent(app, PulseMonitorService::class.java)
        if (enabled) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(app, intent)
                } else {
                    app.startService(intent)
                }
            }
        } else {
            app.stopService(intent)
        }
    }
}
