package com.phydevs.shopping

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ShoppingDaoTest {
    private lateinit var database: ShoppingDatabase
    private lateinit var dao: ShoppingDao

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ShoppingDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.shopping()
    }

    @After fun closeDatabase() {
        database.close()
    }

    @Test fun savesEditsAndRestoresPurchasedItems() = runBlocking {
        assertFalse(dao.saveItem(null, " ", "", Priority.LOW))
        assertTrue(dao.saveItem(null, " Milk ", " Market ", Priority.HIGH))
        assertTrue(dao.saveItem(null, "Milk", "MARKET", Priority.LOW))
        assertEquals(1, dao.observeStores().first().size)
        val rows = dao.observeItems().first()
        assertEquals(2, rows.size)
        val original = rows.first { it.item.priority == Priority.HIGH }.item
        dao.setPurchased(original.id, true)
        assertTrue(dao.saveItem(original.id, "Oat milk", "", Priority.MEDIUM))
        val edited = checkNotNull(dao.getItem(original.id))
        assertEquals("Oat milk", edited.name)
        assertEquals(original.createdAt, edited.createdAt)
        assertTrue(edited.purchased)
        assertNull(edited.storeId)
        dao.setPurchased(original.id, false)
        assertEquals(2, selectItems(dao.observeItems().first(), ItemView.ACTIVE).size)
    }

    @Test fun onlyLatestDeletionCanBeUndone() = runBlocking {
        dao.saveItem(null, "Tea", "", Priority.LOW)
        dao.saveItem(null, "Bread", "", Priority.LOW)
        val items = dao.observeItems().first()
        val tea = items.first { it.item.name == "Tea" }.item
        val bread = items.first { it.item.name == "Bread" }.item
        dao.remove(tea.id)
        dao.remove(bread.id)
        dao.remove(tea.id) // Stale widget action must preserve the current undo.
        assertEquals("Bread", dao.observeRemoval().first())
        assertTrue(dao.observeItems().first().isEmpty())
        dao.undoRemoval()
        assertEquals(listOf("Bread"), dao.observeItems().first().map { it.item.name })
        assertNull(dao.observeRemoval().first())
    }

    @Test fun changingStoreUpdatesItemsAndDeletingItResetsWidgetFilter() = runBlocking {
        dao.saveItem(null, "Tea", "Market", Priority.HIGH)
        val store = dao.observeStores().first().single()
        dao.setWidgetStore(store.id)
        dao.setAppearance(Appearance.DARK)
        assertTrue(dao.saveStore("Corner shop", store.id))
        assertFalse(dao.saveStore("CORNER SHOP"))
        assertEquals("Corner shop", dao.observeItems().first().single().store?.name)
        assertEquals(store.id, dao.observeSettings().first()?.storeId)
        dao.deleteStore(store.id)
        assertNull(dao.observeItems().first().single().item.storeId)
        assertEquals(WidgetSettings(appearance = Appearance.DARK), dao.observeSettings().first())
    }

    @Test fun settingsAndPendingUndoSurviveReopeningDatabase() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "shopping-reopen-test.db"
        suspend fun withDiskDatabase(block: suspend (ShoppingDao) -> Unit) {
            val disk = Room.databaseBuilder(context, ShoppingDatabase::class.java, databaseName).build()
            try {
                block(disk.shopping())
            } finally {
                disk.close()
            }
        }
        try {
            withDiskDatabase { shopping ->
                shopping.saveItem(null, "Rice", "Market", Priority.HIGH)
                val item = shopping.observeItems().first().single().item
                shopping.setWidgetStore(item.storeId)
                shopping.setAppearance(Appearance.DARK)
                shopping.remove(item.id)
            }
            withDiskDatabase { shopping ->
                assertEquals("Rice", shopping.observeRemoval().first())
                assertTrue(shopping.observeItems().first().isEmpty())
                val settings = checkNotNull(shopping.observeSettings().first())
                assertEquals(Appearance.DARK, settings.appearance)
                assertEquals(shopping.observeStores().first().single().id, settings.storeId)
                shopping.undoRemoval()
                assertEquals("Rice", shopping.observeItems().first().single().item.name)
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
