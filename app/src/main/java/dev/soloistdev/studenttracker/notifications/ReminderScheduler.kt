package dev.soloistdev.studenttracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.soloistdev.studenttracker.data.ClassSchedule
import dev.soloistdev.studenttracker.data.ReminderSettings
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Arms the two alarms the reminders run on, and re-arms them after each one fires.
 *
 * Deliberately inexact. Firing on the exact minute needs SCHEDULE_EXACT_ALARM, which Play treats
 * as a restricted permission reserved for alarm clocks and calendars - a gradebook asking for it
 * is a review problem for something nobody needs. `setAndAllowWhileIdle` still punches through
 * doze and lands within a few minutes, which is the right precision for "your next class starts
 * soon": a teacher does not need the nudge at 07:50:00, they need it before they walk in.
 *
 * There is no repeating alarm. Each fire computes the next one from the current schedule, so
 * editing a classroom's times takes effect at the next tick instead of a stale repeat surviving
 * until reinstall.
 */
object ReminderScheduler {

    const val ACTION_CLASS_NUDGE = "dev.soloistdev.studenttracker.CLASS_NUDGE"
    const val ACTION_DAILY_DIGEST = "dev.soloistdev.studenttracker.DAILY_DIGEST"

    const val EXTRA_CLASS_NAME = "class_name"

    private const val REQ_CLASS = 5001
    private const val REQ_DIGEST = 5002

    /**
     * Brings both alarms into line with the current settings and schedule.
     *
     * Safe to call as often as convenient - on launch, after a settings change, after a classroom
     * is edited, and from each fire. Every path replaces rather than adds, so repeated calls
     * cannot accumulate alarms.
     */
    suspend fun reschedule(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        cancel(appContext, REQ_CLASS, ACTION_CLASS_NUDGE)
        cancel(appContext, REQ_DIGEST, ACTION_DAILY_DIGEST)

        if (!ReminderSettings.isEnabled(appContext)) return@withContext
        if (!Notifier.hasPermission(appContext)) return@withContext

        val now = System.currentTimeMillis()

        if (ReminderSettings.classNudgeEnabled(appContext)) {
            scheduleClassNudge(appContext, now)
        }
        if (ReminderSettings.digestEnabled(appContext)) {
            val at = nextDigestMillis(
                now,
                ReminderSettings.digestHour(appContext),
                ReminderSettings.digestMinute(appContext)
            )
            arm(appContext, REQ_DIGEST, Intent(ACTION_DAILY_DIGEST), at)
        }
    }

    fun cancelAll(context: Context) {
        cancel(context.applicationContext, REQ_CLASS, ACTION_CLASS_NUDGE)
        cancel(context.applicationContext, REQ_DIGEST, ACTION_DAILY_DIGEST)
    }

    private suspend fun scheduleClassNudge(context: Context, nowMillis: Long) {
        val classrooms = try {
            StudentRepository(context).getAllClassrooms()
        } catch (t: Throwable) {
            // The database may be unopenable at boot, before the user has unlocked the device and
            // the key store is available. Leaving the alarm unset is correct: the next app launch
            // reschedules.
            //
            // Throwable, because the native SQLCipher bindings fail with Errors as well as
            // exceptions, and this runs from a boot receiver where anything escaping crashes the
            // app before the teacher has touched it.
            t.printStackTrace()
            return
        }

        val lead = ReminderSettings.leadMinutes(context) * 60_000L

        // The first session whose nudge has not already passed. Skipping rather than firing late
        // matters when the app is opened mid-morning: without it the teacher would be told a class
        // that started twenty minutes ago is about to start.
        val target = ClassSchedule.nextStartsWithin(classrooms, nowMillis)
            .firstOrNull { it.startMillis - lead > nowMillis } ?: return

        arm(
            context,
            REQ_CLASS,
            Intent(ACTION_CLASS_NUDGE).putExtra(EXTRA_CLASS_NAME, target.classroom.name),
            target.startMillis - lead
        )
    }

    /** The next time today's or tomorrow's briefing is due. */
    fun nextDigestMillis(nowMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun arm(context: Context, requestCode: Int, intent: Intent, atMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // FLAG_UPDATE_CURRENT always creates, so this cannot be null in practice; the platform
        // signature is nullable only because of FLAG_NO_CREATE.
        val pending = pendingIntent(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (e: Exception) {
            // Some OEM builds cap how many alarms an app may hold. A reminder that cannot be
            // armed is a missing convenience, never a reason to take the app down with it.
            e.printStackTrace()
        }
    }

    private fun cancel(context: Context, requestCode: Int, action: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val existing = pendingIntent(
            context,
            requestCode,
            Intent(action),
            PendingIntent.FLAG_NO_CREATE
        ) ?: return
        manager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        extraFlags: Int
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent.setClass(context, ReminderReceiver::class.java),
        extraFlags or PendingIntent.FLAG_IMMUTABLE
    )
}
