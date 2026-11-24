package com.phydevs.shopping

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = ShoppingDatabase.dao(this)
        setContent {
            ShoppingTheme {
                MainScreen(
                    dao = dao,
                    openEditor = { id ->
                        startActivity(Intent(this, EditorActivity::class.java)
                            .putExtra(EditorActivity.MODE, if (id == null) "add" else "edit")
                            .putExtra(EditorActivity.ITEM, id ?: 0L))
                    },
                    mutate = { change ->
                        lifecycleScope.launch {
                            change()
                            refreshWidgets(applicationContext)
                        }
                    },
                    pin = ::pinWidget,
                    canPin = AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported,
                )
            }
        }
    }

    private fun pinWidget() {
        val accepted = AppWidgetManager.getInstance(this)
            .requestPinAppWidget(ComponentName(this, ShoppingWidgetReceiver::class.java), null, null)
        if (!accepted) Toast.makeText(this, "Open your launcher's widget picker to add Shopping list.", Toast.LENGTH_LONG).show()
    }
}

class EditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = ShoppingDatabase.dao(this)
        val mode = intent.getStringExtra(MODE)
        val id = intent.getLongExtra(ITEM, 0)
        setContent {
            ShoppingTheme {
                Surface(shape = MaterialTheme.shapes.extraLarge) {
                    val stores by remember(dao) { dao.observeStores() }.collectAsStateWithLifecycle(emptyList())
                    when (mode) {
                        "filter" -> Column(Modifier.padding(20.dp)) {
                            Text("Widget store", style = MaterialTheme.typography.headlineSmall)
                            LazyColumn(Modifier.weight(1f, fill = false)) {
                                item {
                                    TextButton(onClick = { selectStore(dao, null) }) { Text("All stores") }
                                }
                                items(stores, key = { it.id }) { store ->
                                    TextButton(onClick = { selectStore(dao, store.id) }) { Text(store.name) }
                                }
                            }
                            TextButton(onClick = ::finish) { Text("Cancel") }
                        }
                        "edit" -> {
                            val items by remember(dao) { dao.observeItems() }.collectAsState(initial = null)
                            val row = items?.find { it.item.id == id }
                            when {
                                items == null -> Text("Loading…", Modifier.padding(24.dp))
                                row == null -> Column(Modifier.padding(24.dp)) {
                                    Text("This item is no longer available.")
                                    TextButton(onClick = ::finish) { Text("Close") }
                                }
                                else -> ItemForm(row, stores, ::finish) { name, store, priority, done ->
                                    saveItem(dao, id, name, store, priority, done)
                                }
                            }
                        }
                        else -> ItemForm(null, stores, ::finish) { name, store, priority, done ->
                            saveItem(dao, null, name, store, priority, done)
                        }
                    }
                }
            }
        }
    }

    private fun selectStore(dao: ShoppingDao, id: Long?) {
        lifecycleScope.launch {
            dao.setWidgetStore(id)
            refreshWidgets(applicationContext)
            finish()
        }
    }

    private fun saveItem(
        dao: ShoppingDao, id: Long?, name: String, store: String, priority: Priority,
        done: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val saved = dao.saveItem(id, name, store, priority)
            if (saved) {
                refreshWidgets(applicationContext)
                finish()
            } else {
                done(false)
            }
        }
    }

    companion object {
        const val MODE = "com.phydevs.shopping.MODE"
        const val ITEM = "com.phydevs.shopping.ITEM"
    }
}

@Composable
private fun ShoppingTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color.White, onPrimary = Color.Black, secondary = Color(0xFFCCCCCC),
            background = Color(0xFF111111), surface = Color(0xFF1B1B1B),
            error = Color(0xFFFF647B),
        )
    } else {
        lightColorScheme(
            primary = Color.Black, onPrimary = Color.White, secondary = Color(0xFF505050),
            background = Color(0xFFF4F4F4), surface = Color.White,
            error = Color(0xFFB40028),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun MainScreen(
    dao: ShoppingDao,
    openEditor: (Long?) -> Unit,
    mutate: (suspend () -> Unit) -> Unit,
    pin: () -> Unit,
    canPin: Boolean,
) {
    val rows by remember(dao) { dao.observeItems() }.collectAsStateWithLifecycle(emptyList())
    val stores by remember(dao) { dao.observeStores() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember(dao) { dao.observeSettings() }.collectAsStateWithLifecycle(null)
    val removal by remember(dao) { dao.observeRemoval() }.collectAsStateWithLifecycle(null)
    var view by rememberSaveable { mutableStateOf(ItemView.ACTIVE) }
    var storeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var priority by rememberSaveable { mutableStateOf<Priority?>(null) }
    var page by rememberSaveable { mutableStateOf("list") }
    val store = stores.find { it.id == storeId }
    val visible = selectItems(rows, view, store?.id, priority)

    BackHandler(page != "list") { page = "list" }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
            when (page) {
                "stores" -> StoreScreen(dao, stores, mutate) { page = "list" }
                "widget" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    ScreenHeader("Widget", "Back") { page = "list" }
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    MenuButton(settings?.appearance?.label ?: "System", Appearance.entries.map { it.label to it }) {
                        mutate { dao.setAppearance(it) }
                    }
                    Text("Store shown on the widget", style = MaterialTheme.typography.titleMedium)
                    MenuButton(stores.find { it.id == settings?.storeId }?.name ?: "All stores", storeOptions(stores)) {
                        mutate { dao.setWidgetStore(it) }
                    }
                    if (canPin) Button(onClick = pin) { Text("Pin widget") }
                    else Text("To add the widget, open your launcher's widget picker and select Shopping list.")
                }
                else -> {
                    ScreenHeader("SHOPPING", "+ Add") { openEditor(null) }
                    Row {
                        TextButton(onClick = { page = "stores" }) { Text("Manage stores") }
                        TextButton(onClick = { page = "widget" }) { Text("Widget settings") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MenuButton(if (view == ItemView.ALL) "All items" else view.name.lowercase().replaceFirstChar { it.uppercase() },
                            listOf("Active" to ItemView.ACTIVE, "Purchased" to ItemView.PURCHASED, "All items" to ItemView.ALL),
                            Modifier.weight(1f)) { view = it }
                        MenuButton(store?.name ?: "All stores", storeOptions(stores), Modifier.weight(1f)) { storeId = it }
                        MenuButton(priority?.label ?: "Any priority",
                            listOf("Any priority" to null) + Priority.entries.map { it.label to it },
                            Modifier.weight(1f)) { priority = it }
                    }
                    if (removal != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Deleted $removal", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { mutate { dao.undoRemoval() } }) { Text("Undo") }
                        }
                    }
                    HorizontalDivider()
                    if (visible.isEmpty()) {
                        Text(when (view) {
                            ItemView.ACTIVE -> "No remaining items match these filters."
                            ItemView.PURCHASED -> "No purchased items match these filters."
                            ItemView.ALL -> "No items match these filters."
                        }, Modifier.padding(vertical = 20.dp))
                    } else LazyColumn(Modifier.weight(1f)) {
                        items(visible, key = { it.item.id }) { row ->
                            ShoppingListRow(
                                row, { openEditor(row.item.id) },
                                { checked -> mutate { dao.setPurchased(row.item.id, checked) } },
                                { mutate { dao.remove(row.item.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ShoppingListRow(row: ShoppingRow, edit: () -> Unit, purchase: (Boolean) -> Unit, delete: () -> Unit) {
    val item = row.item
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = item.purchased, onCheckedChange = purchase,
            modifier = Modifier.semantics {
                contentDescription = if (item.purchased) "Restore ${item.name}" else "Purchase ${item.name}"
            },
        )
        Column(Modifier.weight(1f).clickable(onClick = edit).padding(vertical = 10.dp)) {
            Text(item.name, fontWeight = FontWeight.Medium,
                color = if (item.priority == Priority.HIGH) MaterialTheme.colorScheme.error else Color.Unspecified)
            Text(listOfNotNull(row.store?.name, item.priority.label).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = delete, modifier = Modifier.semantics { contentDescription = "Delete ${item.name}" }) {
            Text("Delete")
        }
    }
    HorizontalDivider()
}

@Composable
private fun StoreScreen(dao: ShoppingDao, stores: List<Store>, mutate: (suspend () -> Unit) -> Unit, back: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    ScreenHeader("Stores", "Back", back)
    TextButton(onClick = { editing = 0L }) { Text("+ Add store") }
    if (stores.isEmpty()) Text("Stores entered on items will appear here.", Modifier.padding(vertical = 12.dp))
    LazyColumn {
        items(stores, key = { it.id }) { store ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(store.name, Modifier.weight(1f))
                TextButton(onClick = { editing = store.id }) { Text("Rename") }
                TextButton(onClick = { deleting = store.id }) { Text("Delete") }
            }
            HorizontalDivider()
        }
    }
    val editId = editing
    if (editId != null) {
        var name by rememberSaveable(editId) { mutableStateOf(stores.find { it.id == editId }?.name.orEmpty()) }
        var error by rememberSaveable(editId) { mutableStateOf(false) }
        var saving by remember(editId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (editId == 0L) "Add store" else "Rename store") },
            text = {
                OutlinedTextField(name, { name = it; error = false }, label = { Text("Store name") },
                    singleLine = true, isError = error,
                    supportingText = { if (error) Text("A store already uses that name.") })
            },
            confirmButton = {
                TextButton(enabled = !saving && cleanStoreName(name) != null, onClick = {
                    saving = true
                    val submittedName = name
                    mutate {
                        val saved = dao.saveStore(submittedName, editId.takeIf { it != 0L })
                        if (editing == editId) {
                            error = !saved
                            saving = false
                            if (saved) editing = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    val storeToDelete = stores.find { it.id == deleting }
    if (storeToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${storeToDelete.name}?") },
            text = { Text("Its items will stay on the list without a store.") },
            confirmButton = {
                TextButton(onClick = {
                    mutate { dao.deleteStore(storeToDelete.id) }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ItemForm(
    row: ShoppingRow?,
    stores: List<Store>,
    close: () -> Unit,
    submit: (String, String, Priority, (Boolean) -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(row?.item?.name.orEmpty()) }
    var store by rememberSaveable { mutableStateOf(row?.store?.name.orEmpty()) }
    var priority by rememberSaveable { mutableStateOf(row?.item?.priority ?: Priority.MEDIUM) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    val valid = cleanItemName(name) != null && store.trim().length <= 60
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (row == null) "Add item" else "Edit item", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Item name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = { if (name.trim().length > 100) Text("Use 100 characters or fewer.") })
        OutlinedTextField(store, { store = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Store (optional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            supportingText = { if (store.trim().length > 60) Text("Use 60 characters or fewer.") })
        if (stores.isNotEmpty()) MenuButton("Choose saved store", listOf("No store" to "") + stores.map { it.name to it.name }) {
            store = it
        }
        MenuButton("Priority · ${priority.label}", Priority.entries.map { it.label to it }) { priority = it }
        if (error) Text("The item couldn't be saved. It may have been deleted.", color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = close, enabled = !saving) { Text("Cancel") }
            Button(enabled = valid && !saving, onClick = {
                saving = true
                error = false
                submit(name, store, priority) { saved -> error = !saved; saving = false }
            }) { Text("Save") }
        }
    }
}

private fun storeOptions(stores: List<Store>): List<Pair<String, Long?>> =
    listOf("All stores" to null) + stores.map { it.name to it.id }

@Composable
private fun <T> MenuButton(label: String, options: List<Pair<String, T>>, modifier: Modifier = Modifier, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (name, value) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    expanded = false
                    select(value)
                })
            }
        }
    }
}
