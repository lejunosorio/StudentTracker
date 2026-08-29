package dev.soloistdev.studenttracker.data

import android.content.Context
import dev.soloistdev.studenttracker.security.SecurityHelper

/**
 * What the teacher has asked to be told about, and when.
 *
 * Everything here is opt-in and off by default. An app that starts notifying on first launch gets
 * its notifications turned off wholesale within a day, which costs the one alert that mattered.
 */
object ReminderSettings {

    private const val PREFS = "app_settings"

    private const val KEY_ENABLED = "reminders_enabled"
    private const val KEY_CLASS_NUDGE = "reminders_class_nudge"
    private const val KEY_LEAD_MINUTES = "reminders_lead_minutes"
    private const val KEY_DIGEST = "reminders_digest"
    private const val KEY_DIGEST_HOUR = "reminders_digest_hour"
    private const val KEY_DIGEST_MINUTE = "reminders_digest_minute"
    private const val KEY_ALERT_STREAKS = "reminders_alert_streaks"
    private const val KEY_ALERT_ABSENT = "reminders_alert_absent"
    private const val KEY_ALERT_CONCERNS = "reminders_alert_concerns"
    private const val KEY_ALERT_OVERDUE = "reminders_alert_overdue"
    private const val KEY_SHOW_NAMES = "reminders_show_names"
    private const val KEY_SHOW_NAMES_SET = "reminders_show_names_set"
    private const val KEY_LAST_DIGEST_DAY = "reminders_last_digest_day"

    const val DEFAULT_LEAD_MINUTES = 10
    const val DEFAULT_DIGEST_HOUR = 6
    const val DEFAULT_DIGEST_MINUTE = 30

    /** Lead times offered in the picker. Anything finer than five minutes is not deliverable. */
    val LEAD_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun classNudgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLASS_NUDGE, true)

    fun setClassNudgeEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_CLASS_NUDGE, value).apply()

    fun leadMinutes(context: Context): Int =
        prefs(context).getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES).coerceIn(5, 120)

    fun setLeadMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(5, 120)).apply()

    fun digestEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DIGEST, true)

    fun setDigestEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_DIGEST, value).apply()

    fun digestHour(context: Context): Int =
        prefs(context).getInt(KEY_DIGEST_HOUR, DEFAULT_DIGEST_HOUR).coerceIn(0, 23)

    fun digestMinute(context: Context): Int =
        prefs(context).getInt(KEY_DIGEST_MINUTE, DEFAULT_DIGEST_MINUTE).coerceIn(0, 59)

    fun setDigestTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_DIGEST_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_DIGEST_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun alertStreaks(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT_STREAKS, true)
    fun setAlertStreaks(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ALERT_STREAKS, v).apply()

    fun alertAbsentLastSession(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT_ABSENT, false)
    fun setAlertAbsentLastSession(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ALERT_ABSENT, v).apply()

    fun alertOpenConcerns(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT_CONCERNS, true)
    fun setAlertOpenConcerns(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ALERT_CONCERNS, v).apply()

    fun alertOverdueMarking(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT_OVERDUE, true)
    fun setAlertOverdueMarking(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ALERT_OVERDUE, v).apply()

    /**
     * Whether a notification or the widget may name a child.
     *
     * A notification is readable by whoever is holding the phone, and the lock screen shows it
     * without any unlock at all. Defaulting this to the inverse of the app's own security gate
     * means a teacher who has bothered to lock the app does not have the same data leak out the
     * side; anyone who has not is assumed to want the more useful wording. Either way the teacher
     * can override it, and once they do their choice sticks regardless of the gate.
     */
    fun showNames(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_SHOW_NAMES_SET, false)) return p.getBoolean(KEY_SHOW_NAMES, true)
        return !SecurityHelper.isSecurityGateEnabled(context)
    }

    fun setShowNames(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SHOW_NAMES, value)
            .putBoolean(KEY_SHOW_NAMES_SET, true)
            .apply()
    }

    /**
     * Day-of-year stamp of the last briefing posted, so a reschedule - a reboot, a settings
     * change, an alarm the OS delivered late - cannot post the same briefing twice.
     */
    fun lastDigestDay(context: Context): Int = prefs(context).getInt(KEY_LAST_DIGEST_DAY, -1)

    fun setLastDigestDay(context: Context, dayStamp: Int) =
        prefs(context).edit().putInt(KEY_LAST_DIGEST_DAY, dayStamp).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
