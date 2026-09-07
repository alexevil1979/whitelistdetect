package ru.whitelist.pulse.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.whitelist.pulse.MainActivity
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.Verdict
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.ui.titleRes

object StatusIndicator {
    const val CHANNEL_ID = "status_indicator"
    const val NOTIFICATION_ID = 17

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            description = context.getString(R.string.settings_status_icon_hint)
        }
        manager.createNotificationChannel(channel)
    }

    fun displayedKind(verdict: Verdict?): VerdictKind {
        val kind = verdict?.kind ?: return VerdictKind.IDLE
        return if (kind == VerdictKind.SCANNING) verdict.underlyingKind else kind
    }

    fun build(context: Context, kind: VerdictKind, scanning: Boolean): Notification {
        ensureChannel(context)
        val color = kind.colorInt()
        val icon = kind.iconRes()
        val large = iconBitmap(context, icon, color, dp = 64)
        val title = context.getString(kind.titleRes())
        val text = if (scanning) {
            context.getString(R.string.verdict_scanning)
        } else {
            context.getString(R.string.status_indicator_subtitle)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setLargeIcon(large)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun update(context: Context, kind: VerdictKind, scanning: Boolean) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, build(context, kind, scanning))
    }

    @DrawableRes
    fun VerdictKind.iconRes(): Int = when (this) {
        VerdictKind.NORMAL -> R.drawable.ic_status_circle_full
        VerdictKind.NO_INTERNET -> R.drawable.ic_status_circle_empty
        else -> R.drawable.ic_status_circle_half
    }

    fun VerdictKind.colorInt(): Int = when (this) {
        VerdictKind.NORMAL -> 0xFF0F766E.toInt()
        VerdictKind.WHITELIST_MODE -> 0xFFD97706.toInt()
        VerdictKind.NO_INTERNET, VerdictKind.IDLE -> 0xFF64748B.toInt()
        VerdictKind.VPN_ACTIVE -> 0xFF4338CA.toInt()
        VerdictKind.ABROAD_OR_BYPASS -> 0xFF7C3AED.toInt()
        VerdictKind.PARTIAL, VerdictKind.SCANNING -> 0xFFE11D48.toInt()
    }

    private fun iconBitmap(context: Context, @DrawableRes res: Int, color: Int, dp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (dp * density).toInt().coerceIn(48, 192)
        val drawable = requireNotNull(ContextCompat.getDrawable(context, res)).mutate()
        drawable.setTint(color)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }
}
