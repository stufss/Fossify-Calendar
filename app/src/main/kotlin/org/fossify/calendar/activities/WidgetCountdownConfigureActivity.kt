package org.fossify.calendar.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ItemCountdownEventPickerBinding
import org.fossify.calendar.databinding.WidgetConfigCountdownBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.MyWidgetCountdownProvider
import org.fossify.calendar.helpers.YEAR
import org.fossify.calendar.helpers.getNowSeconds
import org.fossify.calendar.models.Event
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.IS_CUSTOMIZING_COLORS

class WidgetCountdownConfigureActivity : SimpleActivity() {
    private var mBgAlpha = 0f
    private var mWidgetId = 0
    private var mBgColorWithoutTransparency = 0
    private var mBgColor = 0
    private var mTextColor = 0
    private var mSelectedEvent: Event? = null

    private val binding by viewBinding(WidgetConfigCountdownBinding::inflate)

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(binding.root)
        setupEdgeToEdge(padTopSystem = listOf(binding.configCountdownHolder), padBottomSystem = listOf(binding.root))
        initVariables()

        val isCustomizingColors = intent.extras?.getBoolean(IS_CUSTOMIZING_COLORS) ?: false
        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isCustomizingColors) {
            finish()
            return
        }

        val existingEventId = config.getCountdownWidgetEventId(mWidgetId)
        if (existingEventId != -1L) {
            loadSelectedEvent(existingEventId)
        } else {
            updatePreview()
        }

        val primaryColor = getProperPrimaryColor()
        binding.apply {
            configSave.setOnClickListener { saveConfig() }
            configBgColor.setOnClickListener { pickBackgroundColor() }
            configTextColor.setOnClickListener { pickTextColor() }
            configSelectEvent.setOnClickListener { showEventPicker() }
            configBgSeekbar.setColors(mTextColor, primaryColor, primaryColor)
        }
    }

    private fun initVariables() {
        mBgColor = config.widgetBgColor
        mBgAlpha = Color.alpha(mBgColor) / 255f

        mBgColorWithoutTransparency = Color.rgb(Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor))
        binding.configBgSeekbar.apply {
            progress = (mBgAlpha * 100).toInt()

            onSeekBarChangeListener { progress ->
                mBgAlpha = progress / 100f
                updateBackgroundColor()
            }
        }
        updateBackgroundColor()

        mTextColor = config.widgetTextColor
        if (mTextColor == resources.getColor(org.fossify.commons.R.color.default_widget_text_color) && isDynamicTheme()) {
            mTextColor = resources.getColor(org.fossify.commons.R.color.you_primary_color, theme)
        }

        updateTextColor()
    }

    private fun loadSelectedEvent(eventId: Long) {
        val now = getNowSeconds()
        eventsHelper.getEvents(now, now + 2 * YEAR, eventId, false) { events ->
            val nextEvent = events.filter { it.getEventStartTS() >= now }.minByOrNull { it.getEventStartTS() }
            runOnUiThread {
                mSelectedEvent = nextEvent
                updatePreview()
            }
        }
    }

    private fun showEventPicker() {
        val now = getNowSeconds()
        eventsHelper.getEvents(now, now + 2 * YEAR, applyTypeFilter = true) { events ->
            val upcoming = events
                .filter { it.getEventStartTS() >= now }
                .distinctBy { it.id }
                .sortedBy { it.getEventStartTS() }

            runOnUiThread {
                if (upcoming.isEmpty()) {
                    toast(R.string.no_events_found)
                    return@runOnUiThread
                }
                showEventPickerDialog(upcoming)
            }
        }
    }

    private fun showEventPickerDialog(events: List<Event>) {
        val adapter = object : BaseAdapter() {
            override fun getCount() = events.size
            override fun getItem(position: Int) = events[position]
            override fun getItemId(position: Int) = events[position].id ?: position.toLong()

            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val itemBinding = if (convertView == null) {
                    ItemCountdownEventPickerBinding.inflate(LayoutInflater.from(this@WidgetCountdownConfigureActivity), parent, false)
                } else {
                    ItemCountdownEventPickerBinding.bind(convertView)
                }

                val event = events[position]
                itemBinding.countdownEventPickerTitle.text = event.title
                itemBinding.countdownEventPickerDate.text = Formatter.getDayTitle(
                    this@WidgetCountdownConfigureActivity,
                    Formatter.getDayCodeFromTS(event.getEventStartTS())
                )
                return itemBinding.root
            }
        }

        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_countdown_event)
            .setAdapter(adapter) { _, which ->
                mSelectedEvent = events[which]
                updatePreview()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun updatePreview() {
        binding.apply {
            val event = mSelectedEvent
            if (event == null) {
                widgetCountdownTitleLabel.text = getString(R.string.tap_to_select_event)
                widgetCountdownDaysLabel.text = ""
                widgetCountdownLabelLabel.text = ""
            } else {
                val today = Formatter.getDateFromTS(getNowSeconds())
                val eventDate = Formatter.getDateFromTS(event.getEventStartTS())
                val daysLeft = org.joda.time.Days.daysBetween(today, eventDate).days

                widgetCountdownTitleLabel.text = event.title
                when (daysLeft) {
                    0 -> {
                        widgetCountdownDaysLabel.text = ""
                        widgetCountdownLabelLabel.text = getString(R.string.countdown_today)
                    }
                    1 -> {
                        widgetCountdownDaysLabel.text = ""
                        widgetCountdownLabelLabel.text = getString(R.string.countdown_tomorrow)
                    }
                    else -> {
                        widgetCountdownDaysLabel.text = daysLeft.toString()
                        widgetCountdownLabelLabel.text = resources.getQuantityString(R.plurals.countdown_days_left, daysLeft, daysLeft)
                    }
                }
            }
        }
    }

    private fun saveConfig() {
        storeWidgetColors()

        val event = mSelectedEvent
        if (event?.id != null) {
            config.saveCountdownWidgetEventId(mWidgetId, event.id!!)
        }

        requestWidgetUpdate()

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun storeWidgetColors() {
        config.apply {
            widgetBgColor = mBgColor
            widgetTextColor = mTextColor
        }
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mBgColorWithoutTransparency) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mBgColorWithoutTransparency = color
                updateBackgroundColor()
            }
        }
    }

    private fun pickTextColor() {
        ColorPickerDialog(this, mTextColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mTextColor = color
                updateTextColor()
            }
        }
    }

    private fun requestWidgetUpdate() {
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetCountdownProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateTextColor() {
        binding.apply {
            configTextColor.setFillWithStroke(mTextColor, mTextColor)
            widgetCountdownTitleLabel.setTextColor(mTextColor)
            widgetCountdownDaysLabel.setTextColor(mTextColor)
            widgetCountdownLabelLabel.setTextColor(mTextColor)
            configSave.setTextColor(getProperPrimaryColor().getContrastColor())
            configSelectEvent.setTextColor(getProperPrimaryColor().getContrastColor())
        }
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        binding.apply {
            configCountdownWrapper.background.applyColorFilter(mBgColor)
            configBgColor.setFillWithStroke(mBgColor, mBgColor)
            configSave.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor())
            configSelectEvent.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor())
        }
    }
}
