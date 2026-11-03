package com.xinyuan.gas.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xinyuan.gas.MainActivity
import com.xinyuan.gas.R
import com.xinyuan.gas.data.sync.CloudRecordSummary
import com.xinyuan.gas.data.sync.CloudSessionStore
import com.xinyuan.gas.data.sync.CloudSettings
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object RectificationReminder {
    // Serializes session changes with notification publication; network calls never hold this lock.
    val lock = Any()
    const val OPEN_PENDING = "open_pending_reminders"
    private const val CHANNEL = "rectification_reminders"
    private const val PERIODIC_JOB = 31031
    private const val CHECK_JOB = 31032
    private const val NOTIFICATION = 31033
    private const val POLL_HOURS = 6L
    private const val LAST_CHECK = "last_successful_check"

    private fun prefs(context: Context) =
        context.getSharedPreferences("rectification_reminders", Context.MODE_PRIVATE)

    private fun owner(settings: CloudSettings): String = MessageDigest.getInstance("SHA-256")
        .digest("${settings.baseUrl}|${settings.userId}|${settings.loginScope}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun history(context: Context, settings: CloudSettings) =
        context.getSharedPreferences("reminder_history_${owner(settings)}", Context.MODE_PRIVATE)

    fun ensureScheduled(context: Context, checkNow: Boolean = false) = synchronized(lock) {
        val settings = CloudSessionStore(context).load()
        if (!settings.isLoggedIn) {
            cancel(context)
            return@synchronized
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "隐患整改提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "未整改记录满3天后提醒，之后每隔3天重复，完成整改后停止"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val store = prefs(context)
        val changed = store.getString("owner", null) != owner(settings)
        if (changed) {
            cancel(context)
            store.edit().putString("owner", owner(settings)).remove(LAST_CHECK).apply()
        }
        val component = ComponentName(context, RectificationReminderJobService::class.java)
        if (scheduler.getPendingJob(PERIODIC_JOB) == null) {
            scheduler.schedule(JobInfo.Builder(PERIODIC_JOB, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(TimeUnit.HOURS.toMillis(POLL_HOURS))
                .build())
        }
        val elapsed = System.currentTimeMillis() - store.getLong(LAST_CHECK, 0L)
        if ((changed || checkNow) && (elapsed < 0L || elapsed >= TimeUnit.MINUTES.toMillis(15)) &&
            scheduler.getPendingJob(CHECK_JOB) == null) {
            scheduler.schedule(JobInfo.Builder(CHECK_JOB, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1_000L)
                .build())
        }
    }

    fun cancel(context: Context) = synchronized(lock) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(PERIODIC_JOB)
        scheduler.cancel(CHECK_JOB)
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        prefs(context).edit().remove("owner").remove(LAST_CHECK).apply()
    }

    fun checkAfterPermissionGranted(context: Context) = synchronized(lock) {
        prefs(context).edit().remove(LAST_CHECK).apply()
        ensureScheduled(context, checkNow = true)
    }

    fun notificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun publish(context: Context, session: CloudSettings, records: List<CloudRecordSummary>) = synchronized(lock) {
        val current = CloudSessionStore(context).load()
        if (!current.isLoggedIn || current != session) return@synchronized
        val today = LocalDate.now()
        val store = history(context, current)
        val pending = records.filter { it.status == "pending" }
        val pendingIds = pending.map { it.id.toString() }.toSet()
        val edit = store.edit()
        // A completed record must no longer contribute to a displayed reminder.
        store.all.keys.filter { it.startsWith("sent_") && it.removePrefix("sent_") !in pendingIds }
            .forEach { edit.remove(it) }
        val lastNotifiedDays = pending.mapNotNull {
            val key = "sent_${it.id}"
            if (store.contains(key)) it.id to store.getLong(key, 0L) else null
        }.toMap()
        val due = ReminderPolicy.dueRecords(
            pending.map { ReminderCandidate(it.id, it.inspectionDate, it.status) }, lastNotifiedDays, today)
        val notified = store.getStringSet("visible_ids", emptySet()).orEmpty().intersect(pendingIds)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (due.isNotEmpty() && notificationsEnabled(context)) {
            manager.notify(NOTIFICATION, notification(context, due.size))
            due.forEach { edit.putLong("sent_${it.id}", today.toEpochDay()) }
            edit.putStringSet("visible_ids", due.map { it.id.toString() }.toSet())
        } else if (notified.isEmpty()) {
            manager.cancel(NOTIFICATION)
            edit.remove("visible_ids")
        } else if (notified.size != store.getStringSet("visible_ids", emptySet()).orEmpty().size) {
            // Update only an existing notification; never re-show a dismissed one between reminders.
            if (manager.activeNotifications.any { it.id == NOTIFICATION } && notificationsEnabled(context)) {
                manager.notify(NOTIFICATION, notification(context, notified.size, alert = false))
            }
            edit.putStringSet("visible_ids", notified)
        }
        edit.apply()
        prefs(context).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
    }

    private fun notification(context: Context, count: Int, alert: Boolean = true): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(OPEN_PENDING, true)
        val contentIntent = PendingIntent.getActivity(context, NOTIFICATION, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_rectification_reminder)
            .setContentTitle("隐患整改提醒")
            .setContentText("有 $count 条未整改记录到期，请及时处理。")
            .setStyle(NotificationCompat.BigTextStyle().bigText("有 $count 条未整改记录到期，请及时处理。点击查看待整改列表，未完成的记录每隔3天再次提醒。"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
    }
}
