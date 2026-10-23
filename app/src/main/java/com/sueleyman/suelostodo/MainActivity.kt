package com.sueleyman.suelostodo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sueleyman.suelostodo.sync.SyncScheduler
import com.sueleyman.suelostodo.ui.HomeScreen
import com.sueleyman.suelostodo.ui.SearchScreen
import com.sueleyman.suelostodo.ui.ArrangeScreen
import com.sueleyman.suelostodo.ui.ListScreen
import com.sueleyman.suelostodo.ui.SettingsScreen
import com.sueleyman.suelostodo.ui.TaskDetailScreen
import com.sueleyman.suelostodo.ui.SuelosToDoTheme
import com.sueleyman.suelostodo.ui.TransferScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private sealed interface Deeplink {
        data class OpenTask(val id: String) : Deeplink
        data class AddTo(val listId: String) : Deeplink
        data class OpenList(val listId: String) : Deeplink
        data object Transfer : Deeplink
    }

    private val deeplink = MutableStateFlow<Deeplink?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        SyncScheduler.syncNow(this)

        setContent {
            SuelosToDoTheme {
                val nav = rememberNavController()
                val link by deeplink.collectAsState()
                LaunchedEffect(link) {
                    when (val l = link) {
                        is Deeplink.OpenTask -> nav.navigate("task/${l.id}")
                        is Deeplink.AddTo -> nav.navigate("list/${l.listId}?add=true") {
                            popUpTo("home")
                        }
                        is Deeplink.OpenList -> nav.navigate("list/${l.listId}") { popUpTo("home") }
                        Deeplink.Transfer -> if (nav.currentDestination?.route?.startsWith("transfer") != true) nav.navigate("transfer")
                        null -> {}
                    }
                    deeplink.value = null
                }
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpenList = { nav.navigate("list/$it") },
                            onSettings = { nav.navigate("settings") },
                            onArrange = { nav.navigate("arrange") },
                            onOpenTask = { nav.navigate("task/$it") },
                            onSearch = { nav.navigate("search") }
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            onBack = { nav.popBackStack() },
                            onOpenTask = { nav.navigate("task/$it") },
                            onOpenList = { nav.navigate("list/$it") }
                        )
                    }
                    composable(
                        "list/{id}?add={add}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("add") { type = NavType.BoolType; defaultValue = false },
                        )
                    ) { e ->
                        ListScreen(
                            listId = e.arguments?.getString("id")!!,
                            autoAdd = e.arguments?.getBoolean("add") == true,
                            onBack = { if (!nav.popBackStack()) finish() },
                            onOpenTask = { nav.navigate("task/$it") },
                            onExport = { nav.navigate("transfer?list=${e.arguments?.getString("id")}") }
                        )
                    }
                    composable(
                        "transfer?list={list}",
                        arguments = listOf(navArgument("list") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { e ->
                        TransferScreen(preselectedList = e.arguments?.getString("list"), onBack = { nav.popBackStack() })
                    }
                    composable("task/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                        TaskDetailScreen(
                            taskId = e.arguments?.getString("id")!!,
                            onBack = { if (!nav.popBackStack()) finish() },
                            onOpenTask = { nav.navigate("task/$it") }
                        )
                    }
                    composable("arrange") { ArrangeScreen(onBack = { nav.popBackStack() }) }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() }, onTransfer = { nav.navigate("transfer") })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // make sure placed widgets are (re)drawn whenever the app is opened
        (application as SuelosToDoApp).appScope.launch {
            runCatching { com.sueleyman.suelostodo.widget.TaskWidget.refresh(this@MainActivity) }
            runCatching { com.sueleyman.suelostodo.io.FlaggedMail.syncIfDue(this@MainActivity) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        if (i.getBooleanExtra(EXTRA_OPEN_TRANSFER, false)) { deeplink.value = Deeplink.Transfer; return }
        i.getStringExtra(EXTRA_TASK_ID)?.let { deeplink.value = Deeplink.OpenTask(it); return }
        i.getStringExtra(EXTRA_OPEN_LIST)?.let { deeplink.value = Deeplink.OpenList(it); return }
        i.getStringExtra(EXTRA_ADD_TO_LIST)?.let { deeplink.value = Deeplink.AddTo(it) }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_ADD_TO_LIST = "addToList"
        const val EXTRA_OPEN_LIST = "openList"
        const val EXTRA_OPEN_TRANSFER = "openTransfer"
    }
}
