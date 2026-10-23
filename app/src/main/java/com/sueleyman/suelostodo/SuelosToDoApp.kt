package com.sueleyman.suelostodo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.sueleyman.suelostodo.data.AppDatabase
import com.sueleyman.suelostodo.data.Repository
import com.sueleyman.suelostodo.reminder.ReminderScheduler
import com.sueleyman.suelostodo.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SuelosToDoApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this, AppDatabase.get(this), appScope)
        com.sueleyman.suelostodo.ui.UiPrefs.init(this)
        com.sueleyman.suelostodo.io.MsAuth.refreshState(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ReminderScheduler.CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
        )
        appScope.launch {
            repo.ensureInbox()
            runCatching { SyncScheduler.configurePeriodic(this@SuelosToDoApp) }
        }
    }

    companion object {
        lateinit var instance: SuelosToDoApp
            private set
    }
}
