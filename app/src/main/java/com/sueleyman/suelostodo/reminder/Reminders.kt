package com.sueleyman.suelostodo.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sueleyman.suelostodo.MainActivity
import com.sueleyman.suelostodo.R
import com.sueleyman.suelostodo.SuelosToDoApp
import com.sueleyman.suelostodo.data.Task
import kotlinx.coroutines.launch

object ReminderScheduler {
    const val CHANNEL_ID = "reminders"
    private const val EXTRA_ID = "taskId"
    private const val EXTRA_TITLE = "title"
    const val ACTION_DONE = "com.sueleyman.suelostodo.DONE"

    private fun pending(context: Context, taskId: String, title: String = ""): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java)
            .setAction("reminder:$taskId")
            .putExtra(EXTRA_ID, taskId)
            .putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            context, taskId.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, task: Task) {
        val at = task.reminderAt
        if (task.deleted || task.done || at == null || at <= System.currentTimeMillis()) {
            cancel(context, task.id); return
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, task.id, task.title)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, taskId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, taskId))
    }

    suspend fun rescheduleAll(context: Context) {
        com.sueleyman.suelostodo.data.AppDatabase.get(context).dao().getPendingReminders()
            .forEach { schedule(context, it) }
    }

    internal fun show(context: Context, taskId: String, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, taskId.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction("open:$taskId")
                .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val done = PendingIntent.getBroadcast(
            context, taskId.hashCode() + 1,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Erinnerung")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Erledigt", done)
            .build()
        NotificationManagerCompat.from(context).notify(taskId.hashCode(), n)
    }

    internal fun extraId(i: Intent) = i.getStringExtra(EXTRA_ID)
    internal fun extraTitle(i: Intent) = i.getStringExtra(EXTRA_TITLE)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = ReminderScheduler.extraId(intent) ?: return
        val app = context.applicationContext as SuelosToDoApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                if (intent.action == ReminderScheduler.ACTION_DONE) {
                    val t = app.repo.getTask(id)
                    if (t != null && !t.done) app.repo.toggleDone(id)
                    NotificationManagerCompat.from(context).cancel(id.hashCode())
                } else {
                    val t = app.repo.getTask(id)
                    if (t != null && !t.done && !t.deleted) {
                        ReminderScheduler.show(context, id, t.title)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SuelosToDoApp
        val pending = goAsync()
        app.appScope.launch {
            try { ReminderScheduler.rescheduleAll(context) } finally { pending.finish() }
        }
    }
}
