package com.phydevs.shopping

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MealDatabaseTest {
    @Test fun persistsEditsInDisplayOrderWithoutChangingOtherPlans() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "meal-test.db"
        fun open() = Room.databaseBuilder(context, MealDatabase::class.java, databaseName).build()
        try {
            val original = open()
            val untouched = try {
                val dao = original.meals()
                assertEquals(12, dao.plans().first().size)
                val meals = dao.mealsFor(1).reversed().toMutableList()
                meals[0] = meals[0].copy(description = "  Garden salad  ", minuteOfDay = 18 * 60)
                meals += PlannedMeal(plan = 1, category = "Supper", minuteOfDay = 21 * 60,
                    description = "Toast", displayOrder = 25)
                assertTrue(dao.savePlan(1, meals))
                assertFalse(dao.savePlan(1, meals.dropLast(2)))
                assertFalse(dao.savePlan(1, meals + meals.first()))
                dao.mealsFor(2)
            } finally {
                original.close()
            }
            val reopened = open()
            try {
                val dao = reopened.meals()
                val meals = dao.mealsFor(1)
                assertEquals(listOf(0, 1, 2, 3, 4), meals.map { it.displayOrder })
                assertEquals(listOf("Dinner", "Snack", "Lunch", "Breakfast", "Supper"), meals.map { it.category })
                assertEquals("Garden salad", meals.first().description)
                assertEquals(18 * 60, meals.first().minuteOfDay)
                assertEquals(untouched, dao.mealsFor(2))
                assertTrue(dao.savePlan(1, meals.dropLast(1)))
                assertEquals(4, dao.mealsFor(1).size)
                assertEquals(4, dao.mealsFor(3).size)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
        Unit
    }
}
