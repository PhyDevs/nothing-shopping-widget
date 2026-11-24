package com.phydevs.shopping

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ShoppingWidget()
}

class ShoppingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = ShoppingDatabase.dao(context)
        val itemFlow = dao.observeItems()
        val initialRows = itemFlow.first()
        val storeFlow = dao.observeStores()
        val initialStores = storeFlow.first()
        val settingsFlow = dao.observeSettings()
        val initialSettings = settingsFlow.first()
        val removalFlow = dao.observeRemoval()
        val initialRemoval = removalFlow.first()
        provideContent {
            val items by itemFlow.collectAsState(initialRows)
            val stores by storeFlow.collectAsState(initialStores)
            val settings by settingsFlow.collectAsState(initialSettings)
            val removal by removalFlow.collectAsState(initialRemoval)
            val store = stores.find { it.id == settings?.storeId }
            ShoppingContent(
                context, selectItems(items, ItemView.ACTIVE, store?.id), store, removal,
                settings?.appearance ?: Appearance.SYSTEM,
            )
        }
    }
}

suspend fun refreshWidgets(context: Context) = ShoppingWidget().updateAll(context)

private val itemKey = ActionParameters.Key<Long>("shopping_item")
private val operationKey = ActionParameters.Key<String>("shopping_operation")

class ShoppingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val dao = ShoppingDatabase.dao(context)
        val id = parameters[itemKey]
        when (parameters[operationKey]) {
            "purchase" -> id?.let { dao.setPurchased(it, true) }
            "remove" -> id?.let { dao.remove(it) }
            "undo" -> dao.undoRemoval()
        }
        refreshWidgets(context)
    }
}

private fun widgetAction(operation: String, item: Long = 0): Action =
    actionRunCallback<ShoppingAction>(actionParametersOf(operationKey to operation, itemKey to item))

private fun editorAction(context: Context, mode: String, id: Long = 0): Action =
    actionStartActivity(Intent(context, EditorActivity::class.java)
        .setData("nothing-shopping://editor/$mode/$id".toUri())
        .putExtra(EditorActivity.MODE, mode)
        .putExtra(EditorActivity.ITEM, id))

@Composable
private fun ShoppingContent(
    context: Context,
    rows: List<ShoppingRow>,
    store: Store?,
    removal: String?,
    appearance: Appearance,
) {
    val ink = widgetColor(appearance, Color(0xFF151515), Color.White)
    val muted = widgetColor(appearance, Color(0xFF525252), Color(0xFFBBBBBB))
    val red = widgetColor(appearance, Color(0xFFB40028), Color(0xFFFF647B))
    val separator = widgetColor(appearance, Color(0xFFDDDDDD), Color(0xFF383838))
    val background = when (appearance) {
        Appearance.SYSTEM -> R.drawable.widget_system
        Appearance.LIGHT -> R.drawable.widget_light
        Appearance.DARK -> R.drawable.widget_dark
    }
    val compact = LocalSize.current.width < 205.dp
    val dotTitle = remember { shoppingTitle() }
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(ImageProvider(background))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 4.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Image(
                    ImageProvider(dotTitle), "SHOPPING", GlanceModifier.width(96.dp).height(16.dp),
                    colorFilter = ColorFilter.tint(ink),
                )
                Text("${rows.size} remaining", style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
            }
            WidgetButton("+", "Add item", editorAction(context, "add"), ink, size = 27)
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (store?.name ?: "All stores") + " ▾",
                modifier = GlanceModifier.defaultWeight().padding(vertical = 6.dp)
                    .clickable(editorAction(context, "filter")),
                style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1,
            )
            if (removal != null) {
                Text(
                    "Undo", modifier = GlanceModifier.padding(6.dp)
                        .semantics { contentDescription = "Undo deletion of $removal" }
                        .clickable(widgetAction("undo")),
                    style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        if (rows.isEmpty()) {
            Text(
                if (store == null) "List clear. Tap + to add an item." else "No remaining items at this store.",
                modifier = GlanceModifier.padding(top = 8.dp),
                style = TextStyle(color = muted, fontSize = 13.sp), maxLines = 3,
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows, itemId = { it.item.id }) { row ->
                    val item = row.item
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetButton("○", "Purchase ${item.name}", widgetAction("purchase", item.id), ink)
                        Column(
                            GlanceModifier.defaultWeight().padding(vertical = 4.dp)
                                .clickable(editorAction(context, "edit", item.id)),
                        ) {
                            Text(item.name, style = TextStyle(
                                color = if (item.priority == Priority.HIGH) red else ink,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            ), maxLines = 1)
                            Text(
                                listOfNotNull(row.store?.name, item.priority.label).joinToString(" · "),
                                style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1,
                            )
                        }
                        WidgetButton("×", "Delete ${item.name}", widgetAction("remove", item.id), muted)
                    }
                    Spacer(GlanceModifier.fillMaxWidth().height(1.dp)
                        .background(separator))
                }
            }
        }
    }
}

@Composable
private fun WidgetButton(label: String, description: String, action: Action, color: ColorProvider, size: Int = 20) {
    androidx.glance.layout.Box(
        modifier = GlanceModifier.size(42.dp)
            .semantics { contentDescription = description }
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = color, fontSize = size.sp, textAlign = TextAlign.Center))
    }
}

private fun widgetColor(appearance: Appearance, light: Color, dark: Color): ColorProvider =
    when (appearance) {
        Appearance.SYSTEM -> DayNightColor(light, dark)
        Appearance.LIGHT -> ColorProvider(light)
        Appearance.DARK -> ColorProvider(dark)
    }

private fun shoppingTitle(): Bitmap {
    val glyphs = listOf(
        listOf(15, 16, 16, 14, 1, 1, 30), // S
        listOf(17, 17, 17, 31, 17, 17, 17), // H
        listOf(14, 17, 17, 17, 17, 17, 14), // O
        listOf(30, 17, 17, 30, 16, 16, 16), // P
        listOf(30, 17, 17, 30, 16, 16, 16), // P
        listOf(14, 4, 4, 4, 4, 4, 14), // I
        listOf(17, 25, 25, 21, 19, 19, 17), // N
        listOf(14, 17, 16, 23, 17, 17, 15), // G
    )
    val bitmap = createBitmap(192, 32)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    glyphs.forEachIndexed { letter, rows ->
        rows.forEachIndexed { y, bits ->
            repeat(5) { x ->
                if (bits and (16 shr x) != 0) canvas.drawCircle(
                    (letter * 24 + x * 4 + 2).toFloat(), (y * 4 + 4).toFloat(), 1.45f, paint
                )
            }
        }
    }
    return bitmap
}
