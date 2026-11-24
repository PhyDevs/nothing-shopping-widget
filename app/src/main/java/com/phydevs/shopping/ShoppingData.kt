package com.phydevs.shopping

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import java.util.Locale

enum class Priority(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }
enum class Appearance(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class ItemView { ACTIVE, PURCHASED, ALL }

@Entity(indices = [Index(value = ["normalizedName"], unique = true)])
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("storeId")],
)
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val storeId: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val purchased: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val removed: Boolean = false,
)

data class ShoppingRow(
    @Embedded val item: ShoppingItem,
    @Relation(parentColumn = "storeId", entityColumn = "id") val store: Store?,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("storeId")],
)
data class WidgetSettings(
    @PrimaryKey val id: Int = 1,
    val storeId: Long? = null,
    val appearance: Appearance = Appearance.SYSTEM,
)

class ShoppingConverters {
    @TypeConverter fun encodePriority(value: Priority): String = value.name
    @TypeConverter fun decodePriority(value: String): Priority = Priority.valueOf(value)
    @TypeConverter fun encodeAppearance(value: Appearance): String = value.name
    @TypeConverter fun decodeAppearance(value: String): Appearance = Appearance.valueOf(value)
}

fun selectItems(
    rows: List<ShoppingRow>,
    view: ItemView,
    storeId: Long? = null,
    priority: Priority? = null,
): List<ShoppingRow> = rows.asSequence()
    .filter { !it.item.removed }
    .filter {
        when (view) {
            ItemView.ACTIVE -> !it.item.purchased
            ItemView.PURCHASED -> it.item.purchased
            ItemView.ALL -> true
        }
    }
    .filter { storeId == null || it.item.storeId == storeId }
    .filter { priority == null || it.item.priority == priority }
    .sortedWith(compareBy({ it.item.priority }, { it.item.createdAt }, { it.item.id }))
    .toList()

fun cleanItemName(name: String): String? = name.trim().takeIf { it.length in 1..100 }
fun cleanStoreName(name: String): String? = name.trim().takeIf { it.length in 1..60 }
fun storeKey(name: String): String = name.lowercase(Locale.ROOT)

@Dao
abstract class ShoppingDao {
    @Transaction
    @Query("SELECT * FROM ShoppingItem WHERE removed = 0")
    abstract fun observeItems(): Flow<List<ShoppingRow>>

    @Query("SELECT * FROM ShoppingItem WHERE id = :id AND removed = 0")
    abstract suspend fun getItem(id: Long): ShoppingItem?

    @Query("SELECT * FROM Store ORDER BY name COLLATE NOCASE, id")
    abstract fun observeStores(): Flow<List<Store>>

    @Query("SELECT * FROM Store WHERE normalizedName = :key")
    abstract suspend fun findStore(key: String): Store?

    @Insert
    protected abstract suspend fun insertStore(store: Store): Long

    @Query("UPDATE Store SET name = :name, normalizedName = :key WHERE id = :id")
    protected abstract suspend fun updateStore(id: Long, name: String, key: String)

    @Transaction
    open suspend fun saveStore(rawName: String, id: Long? = null): Boolean {
        val name = cleanStoreName(rawName) ?: return false
        val key = storeKey(name)
        val existing = findStore(key)
        if (existing != null && existing.id != id) return false
        if (id == null) insertStore(Store(name = name, normalizedName = key))
        else updateStore(id, name, key)
        return true
    }

    @Query("DELETE FROM Store WHERE id = :id")
    abstract suspend fun deleteStore(id: Long)

    @Insert
    protected abstract suspend fun insertItem(item: ShoppingItem)

    @Query("UPDATE ShoppingItem SET name = :name, storeId = :store, priority = :priority WHERE id = :id AND removed = 0")
    protected abstract suspend fun updateItem(id: Long, name: String, store: Long?, priority: Priority): Int

    @Transaction
    open suspend fun saveItem(id: Long?, rawName: String, rawStore: String, priority: Priority): Boolean {
        val name = cleanItemName(rawName) ?: return false
        val store = rawStore.trim()
        if (store.length > 60 || (id != null && getItem(id) == null)) return false
        val storeId = store.takeIf { it.isNotEmpty() }?.let {
            val key = storeKey(it)
            findStore(key)?.id ?: insertStore(Store(name = it, normalizedName = key))
        }
        if (id == null) insertItem(ShoppingItem(name = name, storeId = storeId, priority = priority))
        else updateItem(id, name, storeId, priority)
        return true
    }

    @Query("UPDATE ShoppingItem SET purchased = :purchased WHERE id = :id AND removed = 0")
    abstract suspend fun setPurchased(id: Long, purchased: Boolean)

    @Query("DELETE FROM ShoppingItem WHERE removed = 1")
    protected abstract suspend fun clearPreviousRemoval()

    @Query("UPDATE ShoppingItem SET removed = 1 WHERE id = :id")
    protected abstract suspend fun markRemoved(id: Long)

    @Transaction
    open suspend fun remove(id: Long) {
        if (getItem(id) == null) return
        clearPreviousRemoval()
        markRemoved(id)
    }

    @Query("SELECT name FROM ShoppingItem WHERE removed = 1 LIMIT 1")
    abstract fun observeRemoval(): Flow<String?>

    @Query("UPDATE ShoppingItem SET removed = 0 WHERE removed = 1")
    abstract suspend fun undoRemoval()

    @Query("SELECT * FROM WidgetSettings WHERE id = 1")
    abstract fun observeSettings(): Flow<WidgetSettings?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun initializeSettings(settings: WidgetSettings)

    @Query("UPDATE WidgetSettings SET storeId = (SELECT id FROM Store WHERE id = :storeId) WHERE id = 1")
    protected abstract suspend fun updateWidgetStore(storeId: Long?)

    @Transaction
    open suspend fun setWidgetStore(storeId: Long?) {
        initializeSettings(WidgetSettings())
        updateWidgetStore(storeId)
    }

    @Query("UPDATE WidgetSettings SET appearance = :appearance WHERE id = 1")
    protected abstract suspend fun updateAppearance(appearance: Appearance)

    @Transaction
    open suspend fun setAppearance(appearance: Appearance) {
        initializeSettings(WidgetSettings())
        updateAppearance(appearance)
    }
}

@Database(entities = [ShoppingItem::class, Store::class, WidgetSettings::class], version = 1)
@TypeConverters(ShoppingConverters::class)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shopping(): ShoppingDao

    companion object {
        @Volatile private var instance: ShoppingDatabase? = null

        fun dao(context: Context): ShoppingDao = (instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, ShoppingDatabase::class.java, "shopping.db"
            ).build().also { instance = it }
        }).shopping()
    }
}
