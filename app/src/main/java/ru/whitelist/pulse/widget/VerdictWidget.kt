package ru.whitelist.pulse.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.whitelist.pulse.MainActivity
import ru.whitelist.pulse.R
import ru.whitelist.pulse.domain.model.VerdictKind

class VerdictWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val kind = prefs[VERDICT]?.let { runCatching { VerdictKind.valueOf(it) }.getOrNull() }
                ?: VerdictKind.IDLE
            val vpn = prefs[VPN] ?: false
            WidgetContent(context, kind, vpn)
        }
    }

    companion object {
        val VERDICT = stringPreferencesKey("verdict")
        val VPN = booleanPreferencesKey("vpn")

        suspend fun push(context: Context, kind: VerdictKind, vpn: Boolean) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(VerdictWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[VERDICT] = kind.name
                    prefs[VPN] = vpn
                }
                VerdictWidget().update(context, id)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, kind: VerdictKind, vpn: Boolean) {
    val title = when (kind) {
        VerdictKind.NORMAL -> context.getString(R.string.verdict_normal)
        VerdictKind.WHITELIST_MODE -> context.getString(R.string.verdict_whitelist)
        VerdictKind.NO_INTERNET -> context.getString(R.string.verdict_no_internet)
        VerdictKind.VPN_ACTIVE -> context.getString(R.string.verdict_vpn)
        VerdictKind.ABROAD_OR_BYPASS -> context.getString(R.string.verdict_abroad)
        VerdictKind.PARTIAL -> context.getString(R.string.verdict_partial)
        VerdictKind.SCANNING -> context.getString(R.string.verdict_scanning)
        VerdictKind.IDLE -> context.getString(R.string.verdict_idle)
    }
    val color = when (kind) {
        VerdictKind.NORMAL -> Color(0xFF0F766E)
        VerdictKind.WHITELIST_MODE -> Color(0xFFD97706)
        VerdictKind.VPN_ACTIVE -> Color(0xFF4338CA)
        VerdictKind.ABROAD_OR_BYPASS -> Color(0xFF7C3AED)
        VerdictKind.PARTIAL, VerdictKind.SCANNING -> Color(0xFFE11D48)
        else -> Color(0xFF64748B)
    }
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .background(ColorProvider(Color(0xFFF8FAFC)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                context.getString(R.string.app_name),
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF64748B))),
            )
            Text(
                title,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorProvider(color)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    GlanceModifier.size(10.dp)
                        .background(if (vpn) ColorProvider(Color(0xFF4338CA)) else ColorProvider(Color(0xFF94A3B8))),
                )
                Text(
                    if (vpn) context.getString(R.string.chip_vpn_on) else context.getString(R.string.chip_vpn_off),
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF334155))),
                )
            }
        }
    }
}

class VerdictWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerdictWidget()
}
