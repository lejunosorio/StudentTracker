package dev.soloistdev.studenttracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.soloistdev.studenttracker.data.ClassSchedule
import dev.soloistdev.studenttracker.guardBackgroundWork
import dev.soloistdev.studenttracker.data.ReminderSettings
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.data.TodayDigest
import dev.soloistdev.studenttracker.widget.TodayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Where a reminder actually becomes a notification.
 *
 * The work is a database read and a fold, so it runs off the main thread behind `goAsync` - a
 * receiver that blocks gets its process killed, and this one has to open an encrypted database.
 * Whatever happens, the next alarm is armed in a finally: a reminder chain that stops because one
 * fire threw is a feature that silently disappears after a week.
 */
class ReminderReceiver : BroadcastReceiver() {

    private companion object { const val TAG = "ReminderReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action ?: return
        val className = intent.getStringExtra(ReminderScheduler.EXTRA_CLASS_NAME)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                guardBackgroundWork(TAG) {
                    if (ReminderSettings.isEnabled(appContext) && Notifier.hasPermission(appContext)) {
                        when (action) {
                            ReminderScheduler.ACTION_CLASS_NUDGE -> postClassNudge(appContext, className)
                            ReminderScheduler.ACTION_DAILY_DIGEST -> postDigest(appContext)
                        }
                    }
                }
            } finally {
                // Separately guarded: re-arming has to happen even when posting failed, or one bad
                // fire ends the chain and the reminders silently stop for good.
                guardBackgroundWork(TAG) {
                    ReminderScheduler.reschedule(appContext)
                }
                pending.finish()
            }
        }
    }

    private suspend fun postClassNudge(context: Context, className: String?) {
        if (!ReminderSettings.classNudgeEnabled(context)) return

        val repository = StudentRepository(context)
        val digest = TodayDigest.compute(repository, focusClassOverride = className)

        // The alarm was inexact, so report the gap that actually remains rather than the lead time
        // it was armed with - being told a class starts in ten minutes when it starts in three is
        // worse than not being told.
        val minutesUntil = digest.next
            ?.minutesUntilStart(ClassSchedule.nowMinuteOfDay())
            ?.coerceAtLeast(0)
            ?: ReminderSettings.leadMinutes(context)

        Notifier.postClassNudge(context, digest, minutesUntil)
        TodayWidgetProvider.publish(context, digest)
    }

    private suspend fun postDigest(context: Context) {
        if (!ReminderSettings.digestEnabled(context)) return

        // One briefing a day. Reboots, settings changes and late deliveries all re-arm the alarm,
        // and without this stamp a bad morning could post the same list three times.
        val today = dayStamp()
        if (ReminderSettings.lastDigestDay(context) == today) return

        val digest = TodayDigest.compute(StudentRepository(context))
        Notifier.postDigest(context, digest)
        ReminderSettings.setLastDigestDay(context, today)
        TodayWidgetProvider.publish(context, digest)
    }

    private fun dayStamp(): Int = Calendar.getInstance().let {
        it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR)
    }
}
