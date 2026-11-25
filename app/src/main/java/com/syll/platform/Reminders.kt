package com.syll.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.syll.MainActivity
import com.syll.R
import com.syll.VocabFlowApplication
import com.syll.data.KeyValueStore
import com.syll.domain.LearningPreferences
import com.syll.features.plan.ReminderScheduling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Android 13+ asks before an app may post notifications (iOS `UNUserNotificationCenter.requestAuthorization`). */
class NotificationPermission(context: Context, storage: KeyValueStore) :
    RuntimePermission(context, storage, Manifest.permission.POST_NOTIFICATIONS, "notificationsPermanentlyDenied")

/**
 * iOS `NotificationReminderScheduler`: one repeating reminder per learning day, at the reminder time. Permission is
 * only asked when reminders are on. Each day is a weekly alarm (inexact, like any repeating alarm since Android 4.4);
 * [ReminderReceiver] posts the notification, and [RescheduleReceiver] sets the alarms again after a reboot or update.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val permission: NotificationPermission,
    private val scope: CoroutineScope,
) : ReminderScheduling {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun apply(preferences: LearningPreferences) {
        LearningPreferences.ALL_WEEKDAYS.forEach { alarms.cancel(pendingIntent(it, preferences.wordsPerDay)) }
        if (!preferences.reminderEnabled || preferences.learningDays.isEmpty()) return
        scope.launch {
            val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permission.request()
            if (!allowed) return@launch
            preferences.learningDays.forEach { weekday ->
                alarms.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextTrigger(weekday, preferences.reminderHour, preferences.reminderMinute),
                    AlarmManager.INTERVAL_DAY * 7,
                    pendingIntent(weekday, preferences.wordsPerDay),
                )
            }
        }
    }

    private fun pendingIntent(weekday: Int, wordsPerDay: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_BASE + weekday,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_WORDS, wordsPerDay),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_WORDS = "wordsPerDay"
        private const val REQUEST_BASE = 4_200

        /**
         * The next time [weekday] (iOS numbering: 1 = Sunday … 7 = Saturday) reaches [hour]:[minute], in the device's
         * zone; later today counts, a time already past moves to next week.
         */
        fun nextTrigger(weekday: Int, hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
            val day = DayOfWeek.of(if (weekday == 1) 7 else weekday - 1)
            var next = now.with(TemporalAdjusters.nextOrSame(day)).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            return next.atZone(zone).toInstant().toEpochMilli()
        }
    }
}

/** Posts "Time for your flow" when a learning day's alarm goes off. Tapping it opens the app. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val words = intent.getIntExtra(AlarmReminderScheduler.EXTRA_WORDS, LearningPreferences.DEFAULT.wordsPerDay)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.time_for_your_flow))
            .setContentText(context.getString(R.string.x_new_words_are_waiting, words))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL = "learning-reminders"
        const val NOTIFICATION_ID = 1
    }
}

/** Alarms don't survive a reboot or an app update: set them again from the saved plan. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? VocabFlowApplication)?.container?.plan?.rescheduleReminders()
    }
}
