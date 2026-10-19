package com.example.goal_tracker   // <-- must match your app's package (applicationId)

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import es.antonborri.home_widget.HomeWidgetPlugin
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GoalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = HomeWidgetPlugin.getData(context)
        val name = prefs.getString("goal_name", "") ?: ""
        val deadline = prefs.getString("goal_deadline", "") ?: ""
        val lists = prefs.getInt("goal_lists", 0)

        val days = daysLeft(deadline)
        val daysText = when {
            days == null -> "–"
            days < 0 -> "Passed"
            else -> days.toString()
        }
        val labelText = when {
            days == null || days < 0 -> ""
            days == 1L -> "day left"
            else -> "days left"
        }

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.goal_widget)
            v.setTextViewText(R.id.w_name, if (name.isEmpty()) "No goal yet" else name)
            v.setTextViewText(R.id.w_days, daysText)
            v.setTextViewText(R.id.w_label, labelText)
            v.setTextViewText(R.id.w_lists, if (lists == 1) "1 list" else "$lists lists")
            v.setOnClickPendingIntent(R.id.w_root, pending)
            manager.updateAppWidget(id, v)
        }
    }

    /** Whole days from today to the deadline (null if no deadline). */
    private fun daysLeft(deadline: String): Long? {
        if (deadline.isEmpty()) return null
        return try {
            val target = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(deadline)!!
            }
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            Math.round((target.timeInMillis - today.timeInMillis) / 86_400_000.0)
        } catch (e: Exception) {
            null
        }
    }
}
