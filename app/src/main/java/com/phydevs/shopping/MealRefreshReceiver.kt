package com.phydevs.shopping

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class MealRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in refreshActions) return
        signalMealDateChange()
        if (!scheduleMealRefresh(context)) return
        val result = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                MealPlanWidget().updateAll(appContext)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val refreshActions = setOf(
            MIDNIGHT, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

private val calendarRevision = MutableStateFlow(0L)
internal val mealCalendarDay = calendarRevision.map { mealDay(Instant.now(), ZoneId.systemDefault()) }

internal fun signalMealDateChange() = calendarRevision.update { it + 1 }

private const val MIDNIGHT = "com.phydevs.shopping.MEAL_MIDNIGHT"

private fun mealAlarm(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
    context, 0, Intent(context, MealRefreshReceiver::class.java).setAction(MIDNIGHT),
    flags or PendingIntent.FLAG_IMMUTABLE,
)

internal fun cancelMealRefresh(context: Context) {
    val alarm = mealAlarm(context, PendingIntent.FLAG_NO_CREATE) ?: return
    context.getSystemService(AlarmManager::class.java).cancel(alarm)
    alarm.cancel()
}

internal fun scheduleMealRefresh(context: Context): Boolean {
    val receiver = ComponentName(context, MealPlanWidgetReceiver::class.java)
    if (AppWidgetManager.getInstance(context).getAppWidgetIds(receiver).isEmpty()) {
        cancelMealRefresh(context)
        return false
    }
    val midnight = nextMealMidnight(Instant.now(), ZoneId.systemDefault())
    val alarm = checkNotNull(mealAlarm(context, PendingIntent.FLAG_UPDATE_CURRENT))
    // One inexact alarm per local day. The same PendingIntent replaces a previous schedule.
    context.getSystemService(AlarmManager::class.java)
        .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight.toEpochMilli(), alarm)
    return true
}
