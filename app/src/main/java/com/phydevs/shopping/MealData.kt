package com.phydevs.shopping

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class MealDay(val plan: Int?) {
    DAY_1(1), DAY_2(2), DAY_3(3), REST(null),
}

fun mealDay(date: LocalDate): MealDay = when (date.dayOfWeek) {
    DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> MealDay.DAY_1
    DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> MealDay.DAY_2
    DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY -> MealDay.DAY_3
    DayOfWeek.SATURDAY -> MealDay.REST
}

fun mealDay(now: Instant, zone: ZoneId): MealDay = mealDay(now.atZone(zone).toLocalDate())

fun nextMealMidnight(now: Instant, zone: ZoneId): Instant =
    now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

@Entity
data class PlannedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plan: Int,
    val category: String,
    val minuteOfDay: Int,
    val description: String,
    val displayOrder: Int,
)

@Dao
abstract class MealDao {
    @Query("SELECT * FROM PlannedMeal ORDER BY plan, displayOrder, id")
    protected abstract fun observeMeals(): Flow<List<PlannedMeal>>

    @Query("SELECT COUNT(*) FROM PlannedMeal")
    protected abstract suspend fun total(): Int

    @Query("SELECT * FROM PlannedMeal WHERE plan = :plan ORDER BY displayOrder, id")
    protected abstract suspend fun readPlan(plan: Int): List<PlannedMeal>

    @Insert
    protected abstract suspend fun insertMeals(meals: List<PlannedMeal>)

    @Query("DELETE FROM PlannedMeal WHERE plan = :plan")
    protected abstract suspend fun deletePlan(plan: Int)

    @Transaction
    open suspend fun seedIfEmpty() {
        if (total() == 0) insertMeals(defaultMeals)
    }

    suspend fun plans(): Flow<List<PlannedMeal>> {
        seedIfEmpty()
        return observeMeals()
    }

    @Transaction
    open suspend fun mealsFor(plan: Int): List<PlannedMeal> {
        seedIfEmpty()
        return readPlan(plan)
    }

    @Transaction
    open suspend fun savePlan(plan: Int, meals: List<PlannedMeal>): Boolean {
        if (plan !in 1..3 || meals.size !in 4..5 || meals.any { !validMeal(it) }) return false
        seedIfEmpty()
        deletePlan(plan)
        insertMeals(meals.mapIndexed { index, meal ->
            meal.copy(
                id = 0, plan = plan, category = meal.category.trim(),
                description = meal.description.trim(), displayOrder = index,
            )
        })
        return true
    }
}

fun validMeal(meal: PlannedMeal): Boolean =
    meal.category.trim().length in 1..24 && meal.description.trim().length in 1..120 &&
        meal.minuteOfDay in 0..1439

private val defaultMeals = listOf(
    listOf("Greek yogurt, berries & oats", "Lemon chicken with couscous", "Apple & almond butter", "Tomato and white bean stew"),
    listOf("Eggs on sourdough", "Roasted vegetable grain bowl", "Carrots & hummus", "Ginger salmon with greens"),
    listOf("Banana cinnamon porridge", "Spinach & chickpea wrap", "Yogurt with walnuts", "Mushroom and pea risotto"),
).flatMapIndexed { day, descriptions ->
    val categories = listOf("Breakfast", "Lunch", "Snack", "Dinner")
    val times = listOf(8 * 60, 12 * 60 + 30, 16 * 60, 19 * 60)
    descriptions.mapIndexed { index, description ->
        PlannedMeal(
            plan = day + 1, category = categories[index], minuteOfDay = times[index],
            description = description, displayOrder = index,
        )
    }
}

@Database(entities = [PlannedMeal::class], version = 1)
abstract class MealDatabase : RoomDatabase() {
    abstract fun meals(): MealDao

    companion object {
        @Volatile private var instance: MealDatabase? = null

        fun dao(context: Context): MealDao = (instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, MealDatabase::class.java, "meal-planner.db",
            ).build().also { instance = it }
        }).meals()
    }
}
