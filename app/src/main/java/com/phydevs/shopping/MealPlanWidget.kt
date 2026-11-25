package com.phydevs.shopping

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

class MealPlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MealPlanWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        signalMealDateChange()
        scheduleMealRefresh(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        cancelMealRefresh(context)
        super.onDisabled(context)
    }
}

class MealPlanWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val changes = MealDatabase.dao(context).plans()
        val initial = changes.first()
        val initialDay = mealCalendarDay.first()
        val fontScale = context.resources.configuration.fontScale
        provideContent {
            // Updates can reach an active Glance session without restarting provideGlance.
            val day by mealCalendarDay.collectAsState(initialDay)
            val plan = day.plan
            if (plan == null) {
                Image(
                    ImageProvider(R.drawable.meal_rest_art), null,
                    GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                val meals by changes.collectAsState(initial)
                MealDayCard(plan, meals.filter { it.plan == plan }, fontScale)
            }
        }
    }
}

private val mealInk = DayNightColor(Color(0xFF181818), Color(0xFFF8F7F4))
private val mealMuted = DayNightColor(Color(0xFF55524E), Color(0xFFBDBBB7))
private val mealLine = DayNightColor(Color(0xFFD4D0C8), Color(0xFF414141))
private val mealAccent = DayNightColor(Color(0xFFB7002A), Color(0xFFFF536E))

@Composable
private fun MealDayCard(plan: Int, meals: List<PlannedMeal>, fontScale: Float) {
    val compact = LocalSize.current.width < 205.dp
    val scroll = LocalSize.current.height.value < (if (meals.size == 5) 270 else 229) * fontScale.coerceAtLeast(1f)
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(ImageProvider(R.drawable.meal_panel))
            .padding(horizontal = if (compact) 10.dp else 15.dp, vertical = 8.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                val headingSize = if (compact) GlanceModifier.fillMaxWidth() else GlanceModifier.width(104.dp)
                Image(
                    ImageProvider(R.drawable.meal_heading), "MEAL PLAN", headingSize.height(16.dp),
                    colorFilter = ColorFilter.tint(mealInk),
                )
            }
            Text("0$plan/03", style = TextStyle(color = mealInk, fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Row(GlanceModifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp)) {
            for (segment in 1..3) {
                if (segment > 1) Spacer(GlanceModifier.width(4.dp))
                Spacer(GlanceModifier.defaultWeight().height(2.dp)
                    .background(if (segment == plan) mealAccent else mealLine))
            }
        }
        if (scroll) {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(meals, itemId = { it.id }) { MealWidgetRow(it) }
            }
        } else {
            meals.forEach { MealWidgetRow(it) }
        }
    }
}

@Composable
private fun MealWidgetRow(meal: PlannedMeal) {
    Column(GlanceModifier.fillMaxWidth()) {
        Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(mealLine))
        Column(GlanceModifier.fillMaxWidth().padding(top = 3.dp, bottom = 4.dp)) {
            Text(
                "${mealTime(meal.minuteOfDay)}  ·  ${meal.category}",
                style = TextStyle(color = mealMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
            Text(
                meal.description,
                style = TextStyle(color = mealInk, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

internal fun mealTime(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
