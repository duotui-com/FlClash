package com.follow.clash.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.follow.clash.R
import com.follow.clash.RunState
import com.follow.clash.ServiceState
import com.follow.clash.common.Components
import com.follow.clash.common.GlobalState
import com.follow.clash.common.QuickAction
import com.follow.clash.common.quickIntent
import com.follow.clash.common.toPendingIntent
import com.follow.clash.core.Core
import com.follow.clash.service.models.getSpeedTrafficText
import com.follow.clash.sharedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val RunState.labelRes: Int
    get() = when (this) {
        RunState.STARTED -> R.string.widget_status_connected
        RunState.STARTING -> R.string.widget_status_connecting
        RunState.STOPPING -> R.string.widget_status_disconnecting
        RunState.STOPPED -> R.string.widget_status_disconnected
    }

// Started/stopped by VpnWidgetProvider's onEnabled/onDisabled, so the per-second tick
// only runs while a widget instance actually exists.
internal object WidgetUpdater {
    private val provider = ComponentName(GlobalState.application, VpnWidgetProvider::class.java)

    @Volatile
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job != null) return
        job = GlobalState.launch {
            ServiceState.runState.collectLatest { state ->
                if (state == RunState.STARTED) {
                    while (true) {
                        render(state)
                        delay(1_000)
                    }
                } else {
                    render(state)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    fun refresh() = render(ServiceState.runState.value)

    private fun render(state: RunState) {
        runCatching {
            val manager = AppWidgetManager.getInstance(GlobalState.application)
            if (manager.getAppWidgetIds(provider).isEmpty()) return@runCatching
            manager.updateAppWidget(provider, buildRemoteViews(state))
        }.onFailure { error ->
            GlobalState.log("Unable to update VPN widget: $error")
        }
    }

    private fun buildRemoteViews(state: RunState): RemoteViews {
        val application = GlobalState.application
        val shared = application.sharedState
        val connected = state == RunState.STARTED
        val views = RemoteViews(application.packageName, R.layout.widget_vpn_status)

        views.setTextViewText(R.id.widget_status, application.getString(state.labelRes))
        views.setInt(
            R.id.widget_dot,
            "setColorFilter",
            application.getColor(
                if (connected) R.color.widget_dot_connected else R.color.widget_dot_disconnected,
            ),
        )
        views.setTextViewText(R.id.widget_profile_name, shared.currentProfileName)
        views.setViewVisibility(R.id.widget_speed, if (connected) View.VISIBLE else View.GONE)
        if (connected) {
            views.setTextViewText(R.id.widget_speed, Core.getSpeedTrafficText(shared.onlyStatisticsProxy))
        }

        views.setContentDescription(
            R.id.widget_toggle,
            application.getString(R.string.widget_toggle_content_description),
        )
        views.setOnClickPendingIntent(R.id.widget_toggle, QuickAction.TOGGLE.quickIntent.toPendingIntent)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            Intent().setComponent(Components.mainActivity).toPendingIntent,
        )
        return views
    }
}
