package com.phydevs.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemSelectionTest {
    private fun row(
        id: Long,
        priority: Priority = Priority.MEDIUM,
        time: Long = 1,
        store: Long? = null,
        purchased: Boolean = false,
        removed: Boolean = false,
    ) = ShoppingRow(ShoppingItem(id, "Milk", store, priority, purchased, time, removed), null)

    @Test fun sortsPriorityThenOldestThenIdWithoutMergingDuplicateNames() {
        val rows = listOf(
            row(1, Priority.LOW), row(2, Priority.HIGH, 2),
            row(4, Priority.HIGH), row(3, Priority.HIGH), row(5),
        )
        assertEquals(listOf(3L, 4L, 2L, 5L, 1L), selectItems(rows, ItemView.ACTIVE).map { it.item.id })
    }

    @Test fun filtersByStorePriorityAndPurchasedStatus() {
        val rows = listOf(
            row(1, Priority.HIGH, store = 2), row(2, store = 2),
            row(3, Priority.HIGH, store = 3), row(4, Priority.HIGH, store = 2, purchased = true),
            row(5, Priority.HIGH, store = 2, removed = true), row(6, Priority.HIGH),
        )
        assertEquals(listOf(1L), selectItems(rows, ItemView.ACTIVE, 2, Priority.HIGH).map { it.item.id })
        assertEquals(listOf(4L), selectItems(rows, ItemView.PURCHASED).map { it.item.id })
        assertEquals(listOf(1L, 3L, 4L, 6L, 2L), selectItems(rows, ItemView.ALL).map { it.item.id })
        assertEquals(emptyList<ShoppingRow>(), selectItems(rows, ItemView.ACTIVE, 99))
    }

    @Test fun validatesNamesAndMatchesStoresCaseInsensitively() {
        assertNull(cleanItemName("  "))
        assertNull(cleanItemName("a".repeat(101)))
        assertNull(cleanStoreName("a".repeat(61)))
        assertEquals("Milk", cleanItemName("  Milk  "))
        assertEquals("market", storeKey("MARKET"))
    }
}
