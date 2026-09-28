package com.follow.clash.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class VpnWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.start()
        WidgetUpdater.refresh()
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.start()
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.stop()
    }
}
