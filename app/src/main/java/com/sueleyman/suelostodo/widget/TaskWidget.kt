package com.sueleyman.suelostodo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sueleyman.suelostodo.MainActivity
import com.sueleyman.suelostodo.R
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.INBOX_ID
import com.sueleyman.suelostodo.data.ListColors
import com.sueleyman.suelostodo.data.Task
import com.sueleyman.suelostodo.data.TaskList
import com.sueleyman.suelostodo.ui.flattenTree

private val KEY_LIST = stringPreferencesKey("listId")
internal val KEY_TASK = ActionParameters.Key<String>("taskId")

class TaskWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as SuelosToDoApp).repo
        provideContent {
            val prefs = currentState<Preferences>()
            val lists by repo.observeLists().collectAsState(initial = emptyList())
            val open by repo.observeOpenTasks().collectAsState(initial = emptyList())
            val selectedId = prefs[KEY_LIST] ?: INBOX_ID
            val list = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull()
            WidgetContent(context, list, open.filter { it.listId == list?.id })
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TaskWidget().updateAll(context)
    }
}

@Composable
private fun WidgetContent(context: Context, list: TaskList?, tasks: List<Task>) {
    val bg = Color(ListColors.argb(list?.color ?: 0))
    val white = ColorProvider(Color.White)
    val dim = ColorProvider(Color(0xCCFFFFFF))
    val rows = flattenTree(tasks)

    Column(
        modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<NextListAction>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = list?.name ?: "Sülos ToDo",
                    style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Image(ImageProvider(R.drawable.ic_next), "Nächste Liste", GlanceModifier.size(22.dp))
            }
            Image(
                ImageProvider(R.drawable.ic_add), "Aufgabe hinzufügen",
                GlanceModifier.size(30.dp).clickable(
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                            .setData(Uri.parse("suelostodo://add/${list?.id ?: INBOX_ID}"))
                            .putExtra(MainActivity.EXTRA_ADD_TO_LIST, list?.id ?: INBOX_ID)
                    )
                )
            )
        }
        if (rows.isEmpty()) {
            Text("Keine offenen Aufgaben 🎉", style = TextStyle(color = dim, fontSize = 14.sp))
        } else {
            LazyColumn {
                items(rows, itemId = { it.task.id.hashCode().toLong() }) { row ->
                    val t = row.task
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(start = (row.task.depth * 18).dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            ImageProvider(R.drawable.ic_circle), "Erledigen",
                            GlanceModifier.size(if (t.depth == 0) 22.dp else 18.dp)
                                .clickable(actionRunCallback<ToggleDoneAction>(actionParametersOf(KEY_TASK to t.id)))
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            text = t.title + if (row.hasChildren && !t.expanded) "  (+${row.childCount})" else "",
                            style = TextStyle(color = if (t.depth == 0) white else dim, fontSize = if (t.depth == 0) 15.sp else 14.sp),
                            maxLines = 2,
                            modifier = GlanceModifier.defaultWeight().clickable(
                                actionStartActivity(
                                    Intent(context, MainActivity::class.java)
                                        .setData(Uri.parse("suelostodo://task/${t.id}"))
                                        .putExtra(MainActivity.EXTRA_TASK_ID, t.id)
                                )
                            )
                        )
                        if (t.important) Image(ImageProvider(R.drawable.ic_star), "Wichtig", GlanceModifier.size(16.dp))
                    }
                }
            }
        }
    }
}

class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[KEY_TASK] ?: return
        (context.applicationContext as SuelosToDoApp).repo.toggleDone(id)
        TaskWidget().update(context, glanceId)
    }
}

class NextListAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val lists = (context.applicationContext as SuelosToDoApp).repo.getLists()
        if (lists.isEmpty()) return
        updateAppWidgetState(context, glanceId) { prefs ->
            val cur = prefs[KEY_LIST] ?: INBOX_ID
            val idx = lists.indexOfFirst { it.id == cur }
            prefs[KEY_LIST] = lists[(idx + 1).mod(lists.size)].id
        }
        TaskWidget().update(context, glanceId)
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}
