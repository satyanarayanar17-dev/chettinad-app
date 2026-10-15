package com.example.portal

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.api.CareOccurrence
import java.text.SimpleDateFormat
import java.util.*

/** Inexact reminders; no exact-alarm access or medical details in lock-screen text. */
object CareReminders {
    private const val CHANNEL = "care_tasks"
    fun enabled(context: Context) = context.getSharedPreferences("care_reminders", Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun enable(context: Context, enabled: Boolean) {
        context.getSharedPreferences("care_reminders", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        if (!enabled) cancel(context)
    }
    private fun pending(context: Context, key: Int) = PendingIntent.getBroadcast(context, key,
        Intent(context, CareReminderReceiver::class.java).putExtra("notification_id", key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(context: Context) {
        val prefs = context.getSharedPreferences("care_reminders", Context.MODE_PRIVATE)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        prefs.getStringSet("scheduled", emptySet()).orEmpty().forEach { id -> id.toIntOrNull()?.let { manager.cancel(pending(context, it)); NotificationManagerCompat.from(context).cancel(it) } }
        prefs.edit().remove("scheduled").apply()
    }
    fun schedule(context: Context, tasks: List<CareOccurrence>) {
        cancel(context)
        if (!enabled(context)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "Care plan reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val ids = mutableSetOf<String>(); val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        tasks.filter { it.status == "UPCOMING" }.forEach { task ->
            val time = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).parse(task.scheduled_for.replace(Regex("\\.[0-9]+"), ""))?.time }.getOrNull()
            if (time != null && time > System.currentTimeMillis()) {
                val key = (task.task_id + task.occurrence_date).hashCode(); ids.add(key.toString())
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context, key))
            }
        }
        context.getSharedPreferences("care_reminders", Context.MODE_PRIVATE).edit().putStringSet("scheduled", ids).apply()
    }
    fun show(context: Context, id: Int) {
        if (!enabled(context)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Chettinad Care").setContentText("Open your care plan to review a scheduled task.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { CareReminders.show(context, intent.getIntExtra("notification_id", 0)) }
}
@Composable fun ReminderPreferences(state: PortalState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CareReminders.enabled(context)) }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted; enabled = granted; CareReminders.enable(context, granted)
        if (granted) CareReminders.schedule(context, state.today)
    }
    InfoCard("Care plan reminders", "Optional reminders for the tasks loaded today. Timing may vary with your device settings; open the app daily to refresh them.") {
        Switch(checked = enabled, onCheckedChange = { value ->
            if (value && Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { enabled = value; CareReminders.enable(context, value); if (value) CareReminders.schedule(context, state.today) }
        })
        if (denied) Text("Notifications are disabled in system settings.")
    }
}
