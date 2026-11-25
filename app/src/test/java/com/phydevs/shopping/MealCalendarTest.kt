package com.phydevs.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class MealCalendarTest {
    @Test fun choosesTheFixedPlanForEveryWeekday() {
        val monday = LocalDate.of(2026, 11, 23)
        val expected = listOf(
            MealDay.DAY_1, MealDay.DAY_2, MealDay.DAY_3, MealDay.DAY_1,
            MealDay.DAY_2, MealDay.REST, MealDay.DAY_3,
        )
        assertEquals(expected, (0L..6L).map { mealDay(monday.plusDays(it)) })
    }

    @Test fun switchesFromSaturdayArtworkToDayThreeAtLocalMidnight() {
        val berlin = ZoneId.of("Europe/Berlin")
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val beforeMidnight = Instant.parse("2026-11-28T22:59:59Z")
        val midnight = Instant.parse("2026-11-28T23:00:00Z")
        assertNull(mealDay(beforeMidnight, berlin).plan)
        assertEquals(MealDay.DAY_3, mealDay(midnight, berlin))
        assertNull(mealDay(midnight, losAngeles).plan)
        assertEquals(midnight, nextMealMidnight(beforeMidnight, berlin))
    }

    @Test fun schedulesCalendarMidnightAcrossDaylightSavingChanges() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals(
            Instant.parse("2026-03-29T22:00:00Z"),
            nextMealMidnight(Instant.parse("2026-03-28T23:00:00Z"), berlin),
        )
        assertEquals(
            Instant.parse("2026-10-25T23:00:00Z"),
            nextMealMidnight(Instant.parse("2026-10-24T22:00:00Z"), berlin),
        )
        assertEquals(
            Instant.parse("2027-01-01T00:00:00Z"),
            nextMealMidnight(Instant.parse("2026-12-31T23:55:00Z"), ZoneOffset.UTC),
        )
    }

    @Test fun validates24HourMealTime() {
        assertEquals(0, parseMealTime("00:00"))
        assertEquals(8 * 60 + 5, parseMealTime("8:05"))
        assertEquals(1439, parseMealTime("23:59"))
        listOf("", "24:00", "10:60", "10:1", "2pm", "10:05:00", "+1:05").forEach {
            assertNull(parseMealTime(it))
        }
    }
}
