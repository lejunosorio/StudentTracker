package dev.soloistdev.studenttracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.soloistdev.studenttracker.MainActivity
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ReminderSettings
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.TodayDigest

/**
 * Everything this app puts in the status bar.
 *
 * Kept in one place so the two rules that matter are enforced once rather than at each call site:
 * a notification never names a child unless the teacher has allowed it, and nothing is posted
 * without the runtime permission actually being held - a missing permission is a silent no-op on
 * every Android version, so checking here is the only thing that stops a caller assuming it worked.
 */
object Notifier {

    const val CHANNEL_CLASS = "class_reminders"
    const val CHANNEL_DIGEST = "daily_briefing"
    const val CHANNEL_BACKUP = "backup_status"

    private const val ID_CLASS = 2001
    private const val ID_DIGEST = 2002
    private const val ID_BACKUP = 2003

    /** Route to open when a notification is tapped, read back by [MainActivity]. */
    const val EXTRA_ROUTE = "dev.soloistdev.studenttracker.OPEN_ROUTE"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Separate channels on purpose: a teacher who finds the pre-class nudge distracting can
        // silence just that one from the system settings without losing the morning briefing.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CLASS,
                context.getString(R.string.channel_class_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_class_reminders_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIGEST,
                context.getString(R.string.channel_daily_briefing),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_daily_briefing_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BACKUP,
                context.getString(R.string.channel_backup_status),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_backup_status_desc) }
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    /** "Class 10-A starts in 10 minutes", with whatever is worth knowing before walking in. */
    fun postClassNudge(context: Context, digest: TodayDigest.Digest, minutesUntil: Int) {
        val className = digest.next?.classroom?.name ?: digest.current?.classroom?.name ?: return

        // Resolved once per notification. Behind it is an EncryptedSharedPreferences open and a
        // KeyStore round trip, which is not something to do per line of body text.
        val named = ReminderSettings.showNames(context)

        val title = context.getString(R.string.notif_class_starting, className, minutesUntil)
        val lines = mutableListOf<String>()

        lines.add(context.getString(R.string.notif_class_roster, digest.classSize))

        digest.summary?.let { summary ->
            if (summary.atRisk > 0) lines.add(context.getString(R.string.notif_class_at_risk, summary.atRisk))
        }
        if (digest.absentLastSession.isNotEmpty()) {
            lines.add(
                context.getString(
                    R.string.notif_class_absent_last,
                    describe(context, digest.absentLastSession, named)
                )
            )
        }
        if (digest.onStreak.isNotEmpty()) {
            lines.add(
                context.getString(
                    R.string.notif_class_streak,
                    describe(context, digest.onStreak.map { it.first }, named)
                )
            )
        }

        post(context, ID_CLASS, CHANNEL_CLASS, title, lines, "today", named)
    }

    /** The morning briefing: the things that will not fix themselves. */
    fun postDigest(context: Context, digest: TodayDigest.Digest) {
        val named = ReminderSettings.showNames(context)
        val lines = mutableListOf<String>()

        if (ReminderSettings.alertStreaks(context)) {
            digest.onStreak.forEach { (student, days) ->
                lines.add(
                    if (named) {
                        context.getString(R.string.notif_digest_streak_named, displayName(student), days)
                    } else {
                        context.getString(R.string.notif_digest_streak_anon, days)
                    }
                )
            }
        }
        if (ReminderSettings.alertAbsentLastSession(context) && digest.absentLastSession.isNotEmpty()) {
            lines.add(
                context.getString(
                    R.string.notif_digest_absent,
                    describe(context, digest.absentLastSession, named)
                )
            )
        }
        if (ReminderSettings.alertOpenConcerns(context) && digest.openConcerns.isNotEmpty()) {
            val total = digest.openConcerns.sumOf { it.second }
            lines.add(context.getString(R.string.notif_digest_concerns, total, digest.openConcerns.size))
        }
        if (ReminderSettings.alertOverdueMarking(context)) {
            digest.dueSoon.take(3).forEach { due ->
                lines.add(
                    if (due.isOverdue) {
                        context.getString(R.string.notif_digest_overdue, due.column.name, due.outstanding)
                    } else {
                        context.getString(R.string.notif_digest_due, due.column.name, due.outstanding)
                    }
                )
            }
        }

        // Nothing to say is a result, not a reason to post. A daily "all clear" is exactly the
        // notification that trains a teacher to swipe this channel away unread.
        if (lines.isEmpty()) return

        val scope = digest.focusClassName ?: context.getString(R.string.notif_scope_all_classes)
        val title = context.getString(R.string.notif_digest_title, lines.size, scope)

        post(context, ID_DIGEST, CHANNEL_DIGEST, title, lines, "today", named)
    }

    /** Only ever posted when an automatic backup has failed; success stays silent. */
    fun postBackupFailure(context: Context) {
        post(
            context,
            ID_BACKUP,
            CHANNEL_BACKUP,
            context.getString(R.string.notif_backup_failed_title),
            listOf(context.getString(R.string.notif_backup_failed_body)),
            "settings_backup",
            // Carries no student data at all, so there is nothing to hide from a lock screen.
            named = true
        )
    }

    private fun post(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        lines: List<String>,
        route: String,
        named: Boolean
    ) {
        if (!hasPermission(context)) return
        ensureChannels(context)

        val body = lines.joinToString("\n")

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, route))
            // The gate exists to keep this data behind an unlock; a lock-screen preview would
            // hand it to anyone who picks the phone up.
            .setVisibility(
                if (named) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_SECRET
            )

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post. Nothing to recover.
        }
    }

    private fun openAppIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * A handful of students as text: names when that is allowed, a bare count when it is not.
     *
     * Truncated at three because a status-bar line that lists a whole class is unreadable, and the
     * teacher is being sent to the app anyway.
     */
    private fun describe(context: Context, students: List<StudentEntity>, named: Boolean): String {
        if (!named) {
            return context.resources.getQuantityString(
                R.plurals.notif_student_count, students.size, students.size
            )
        }
        val shown = students.take(3).joinToString(", ") { displayName(it) }
        val extra = students.size - 3
        return if (extra > 0) context.getString(R.string.notif_and_more, shown, extra) else shown
    }

    private fun displayName(student: StudentEntity): String =
        "${student.firstName} ${student.lastName}".trim()
}
