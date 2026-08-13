package org.fossify.calendar.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.fossify.calendar.R
import org.fossify.calendar.activities.SplashActivity
import org.fossify.calendar.activities.WidgetCountdownConfigureActivity
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getLaunchIntent
import org.joda.time.Days

class MyWidgetCountdownProvider : AppWidgetProvider() {
    private val OPEN_APP_INTENT_ID = 1
    private val OPEN_CONFIG_INTENT_ID = 2

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        var remaining = appWidgetIds.size
        if (remaining == 0) {
            pendingResult.finish()
            return
        }

        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId) {
                remaining--
                if (remaining <= 0) {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            context.config.removeCountdownWidgetEventId(it)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId) {}
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, onDone: () -> Unit) {
        val eventId = context.config.getCountdownWidgetEventId(widgetId)
        if (eventId == -1L) {
            val views = buildEmptyViews(context, widgetId)
            appWidgetManager.updateAppWidget(widgetId, views)
            onDone()
            return
        }

        val now = getNowSeconds()
        context.eventsHelper.getEvents(now, now + 2 * YEAR, eventId, false) { events ->
            val nextEvent = events
                .filter { it.getEventStartTS() >= now }
                .minByOrNull { it.getEventStartTS() }

            val views = if (nextEvent != null) {
                buildCountdownViews(context, widgetId, nextEvent)
            } else {
                buildPassedViews(context, widgetId)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
            onDone()
        }
    }

    private fun buildCountdownViews(context: Context, widgetId: Int, event: Event): RemoteViews {
        val now = getNowSeconds()
        val today = Formatter.getDateFromTS(now)
        val eventDate = Formatter.getDateFromTS(event.getEventStartTS())
        val daysLeft = Days.daysBetween(today, eventDate).days

        val daysLabel = when (daysLeft) {
            0 -> context.getString(R.string.countdown_today)
            1 -> context.getString(R.string.countdown_tomorrow)
            else -> context.resources.getQuantityString(R.plurals.countdown_days_left, daysLeft)
        }

        return RemoteViews(context.packageName, R.layout.widget_countdown).apply {
            applyColorFilter(R.id.widget_countdown_background, context.config.widgetBgColor)
            setTextColor(R.id.widget_countdown_title, context.config.widgetTextColor)
            setTextColor(R.id.widget_countdown_days, context.config.widgetTextColor)
            setTextColor(R.id.widget_countdown_label, context.config.widgetTextColor)

            setTextViewText(R.id.widget_countdown_title, event.title)

            if (daysLeft <= 1) {
                setTextViewText(R.id.widget_countdown_days, "")
                setTextViewText(R.id.widget_countdown_label, daysLabel)
            } else {
                setTextViewText(R.id.widget_countdown_days, daysLeft.toString())
                setTextViewText(R.id.widget_countdown_label, daysLabel)
            }

            setupAppOpenIntent(context, this, widgetId)
        }
    }

    private fun buildPassedViews(context: Context, widgetId: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_countdown).apply {
            applyColorFilter(R.id.widget_countdown_background, context.config.widgetBgColor)
            setTextColor(R.id.widget_countdown_title, context.config.widgetTextColor)
            setTextColor(R.id.widget_countdown_days, context.config.widgetTextColor)
            setTextColor(R.id.widget_countdown_label, context.config.widgetTextColor)

            setTextViewText(R.id.widget_countdown_title, "")
            setTextViewText(R.id.widget_countdown_days, "")
            setTextViewText(R.id.widget_countdown_label, context.getString(R.string.countdown_event_passed))

            setupConfigureIntent(context, this, widgetId)
        }
    }

    private fun buildEmptyViews(context: Context, widgetId: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_countdown).apply {
            applyColorFilter(R.id.widget_countdown_background, context.config.widgetBgColor)
            setTextColor(R.id.widget_countdown_title, context.config.widgetTextColor)
            setTextColor(R.id.widget_countdown_days, context.config.widgetTextColor)
            setTextColor(R.id.widget_countdown_label, context.config.widgetTextColor)

            setTextViewText(R.id.widget_countdown_title, "")
            setTextViewText(R.id.widget_countdown_days, "")
            setTextViewText(R.id.widget_countdown_label, context.getString(R.string.tap_to_select_event))

            setupConfigureIntent(context, this, widgetId)
        }
    }

    private fun setupAppOpenIntent(context: Context, views: RemoteViews, widgetId: Int) {
        (context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)).apply {
            val pendingIntent = PendingIntent.getActivity(
                context,
                OPEN_APP_INTENT_ID + widgetId,
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_holder, pendingIntent)
        }
    }

    private fun setupConfigureIntent(context: Context, views: RemoteViews, widgetId: Int) {
        Intent(context, WidgetCountdownConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            val pendingIntent = PendingIntent.getActivity(
                context,
                OPEN_CONFIG_INTENT_ID + widgetId,
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_holder, pendingIntent)
        }
    }
}
