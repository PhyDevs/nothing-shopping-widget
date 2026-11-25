package com.phydevs.shopping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun MealPlansScreen(
    dao: MealDao,
    canPin: Boolean,
    pin: () -> Unit,
    save: (Int, List<PlannedMeal>, (Boolean) -> Unit) -> Unit,
) {
    val allMeals by produceState<List<PlannedMeal>>(emptyList(), dao) {
        dao.plans().collect { value = it }
    }
    val context = LocalContext.current
    val today by mealCalendarDay.collectAsStateWithLifecycle(
        remember { mealDay(Instant.now(), ZoneId.systemDefault()) },
    )
    LifecycleResumeEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = signalMealDateChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        signalMealDateChange()
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }

    var plan by rememberSaveable { mutableIntStateOf(1) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val meals = allMeals.filter { it.plan == plan }

    fun store(entries: List<PlannedMeal>) {
        saving = true
        saveError = false
        save(plan, entries.sortedBy { it.minuteOfDay }) { saved ->
            saving = false
            saveError = !saved
            if (saved) editingId = null
        }
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Meal Planner", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (canPin) Button(onClick = pin) { Text("Pin widget") }
            }
            if (!canPin) Text("Add Meal Planner from your launcher's widget picker.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { preview = "today" }) { Text("Preview today") }
                TextButton(onClick = { preview = "saturday" }) { Text("Preview Saturday") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { day ->
                    FilterChip(selected = plan == day, onClick = { plan = day }, enabled = !saving,
                        label = { Text("Day $day") })
                }
            }
            Text(
                when (plan) {
                    1 -> "Monday and Thursday"
                    2 -> "Tuesday and Friday"
                    else -> "Wednesday and Sunday"
                },
                Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            if (allMeals.isEmpty()) Text("Loading plans…", Modifier.padding(top = 12.dp))
        }
        items(meals, key = { it.id }) { meal ->
            MealDetails(meal, Modifier.clickable(enabled = !saving) { editingId = meal.id })
            HorizontalDivider()
        }
        if (meals.size == 4) {
            item {
                TextButton(
                    onClick = { editingId = 0 },
                    enabled = !saving,
                ) { Text("Add fifth meal") }
            }
        }
    }

    val id = editingId
    val existing = meals.find { it.id == id }
    if (id != null && (id == 0L || existing != null)) {
        MealEditDialog(
            meal = existing,
            canRemove = existing != null && meals.size > 4,
            saving = saving,
            saveError = saveError,
            close = { if (!saving) { editingId = null; saveError = false } },
            submit = { updated ->
                store(if (existing == null) meals + updated else meals.map { if (it.id == id) updated else it })
            },
            remove = { store(meals.filterNot { it.id == id }) },
        )
    }

    if (preview != null) {
        val day = if (preview == "saturday") MealDay.REST else today
        val previewPlan = day.plan
        if (previewPlan == null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { preview = null }) {
                Image(
                    painterResource(R.drawable.meal_rest_art), "Saturday artwork",
                    Modifier.fillMaxWidth().aspectRatio(250f / 290f),
                )
            }
        } else {
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text("Meal plan · 0$previewPlan/03", fontFamily = FontFamily.Monospace) },
                text = {
                    LazyColumn {
                        items(allMeals.filter { it.plan == previewPlan }, key = { it.id }) { meal ->
                            MealDetails(meal)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun MealDetails(meal: PlannedMeal, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(mealTime(meal.minuteOfDay), Modifier.width(60.dp), fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(1f)) {
            Text(meal.category, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(meal.description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

internal fun parseMealTime(value: String): Int? {
    val parts = value.trim().split(':')
    if (parts.size != 2 || parts[0].length !in 1..2 || parts[1].length != 2 ||
        parts.any { digits -> digits.any { it !in '0'..'9' } }
    ) return null
    val hour = parts[0].toInt()
    val minute = parts[1].toInt()
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

@Composable
private fun MealEditDialog(
    meal: PlannedMeal?,
    canRemove: Boolean,
    saving: Boolean,
    saveError: Boolean,
    close: () -> Unit,
    submit: (PlannedMeal) -> Unit,
    remove: () -> Unit,
) {
    var category by rememberSaveable(meal?.id) { mutableStateOf(meal?.category.orEmpty()) }
    var description by rememberSaveable(meal?.id) { mutableStateOf(meal?.description.orEmpty()) }
    var time by rememberSaveable(meal?.id) { mutableStateOf(meal?.let { mealTime(it.minuteOfDay) }.orEmpty()) }
    val minute = parseMealTime(time)
    val draft = PlannedMeal(
        plan = meal?.plan ?: 0, category = category, minuteOfDay = minute ?: -1,
        description = description, displayOrder = meal?.displayOrder ?: 0,
    )
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (meal == null) "Add meal" else "Edit meal") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    OutlinedTextField(category, { category = it }, label = { Text("Category") },
                        isError = category.trim().length > 24, singleLine = true)
                    OutlinedTextField(time, { time = it }, label = { Text("Time (24-hour)") },
                        placeholder = { Text("18:30") },
                        isError = time.isNotBlank() && minute == null, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Description") },
                        isError = description.trim().length > 120, maxLines = 3)
                    if (canRemove) TextButton(onClick = remove, enabled = !saving) { Text("Remove meal") }
                    if (saveError) Text("Could not save this plan.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit(draft) }, enabled = !saving && validMeal(draft)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close, enabled = !saving) { Text("Cancel") } },
    )
}
