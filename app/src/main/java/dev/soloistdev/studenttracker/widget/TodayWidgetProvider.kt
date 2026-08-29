package dev.soloistdev.studenttracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.soloistdev.studenttracker.MainActivity
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ReminderSettings
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.data.TodayDigest
import dev.soloistdev.studenttracker.guardBackgroundWork
import dev.soloistdev.studenttracker.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Today digest on the home screen.
 *
 * Taking the roll is a thirty-second job that currently costs a cold launch of an encrypted app
 * behind a biometric gate, which is why it slips. This puts the answer - and the way in - one tap
 * from the launcher.
 *
 * Rendered text is cached in prefs rather than recomputed on every draw. A launcher redraws a
 * widget whenever it feels like it (rotation, theme change, resize), and opening SQLCipher on each
 * of those would be both slow and pointless; the cache is refreshed by whoever last computed a
 * real digest.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // A widget being placed for the first time has nothing cached, so treat that like a
        // refresh rather than showing the empty state until something else happens to compute one.
        val wantsData = intent.action == ACTION_REFRESH ||
                (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE && !hasCache(context))
        if (!wantsData) return

        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                guardBackgroundWork(TAG) {
                    publish(appContext, TodayDigest.compute(StudentRepository(appContext)))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TodayWidget"
        private const val ACTION_REFRESH = "dev.soloistdev.studenttracker.WIDGET_REFRESH"

        private const val PREFS = "today_widget"
        private const val KEY_EYEBROW = "eyebrow"
        private const val KEY_CLASS = "class"
        private const val KEY_DETAIL = "detail"
        private const val KEY_FLAGS = "flags"

        /**
         * Renders [digest] into the widget and caches the result.
         *
         * Called by anything that has just paid for a digest - the Today screen, a reminder
         * firing - so the widget is current without ever scheduling work of its own.
         */
        fun publish(context: Context, digest: TodayDigest.Digest) {
            val appContext = context.applicationContext
            cache(appContext, digest)

            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val ids = try {
                manager.getAppWidgetIds(ComponentName(appContext, TodayWidgetProvider::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }
            if (ids.isEmpty()) return

            val views = buildViews(appContext)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun hasCache(context: Context): Boolean =
            prefs(context).contains(KEY_CLASS)

        private fun cache(context: Context, digest: TodayDigest.Digest) {
            val inSession = digest.current != null
            val target = digest.current ?: digest.next

            val eyebrow = context.getString(
                if (inSession) R.string.widget_eyebrow_now else R.string.widget_eyebrow_next
            )

            val className = target?.classroom?.name ?: context.getString(R.string.widget_no_class)

            val detail = when {
                target == null -> context.getString(R.string.widget_no_class_detail)
                inSession -> context.getString(
                    R.string.widget_detail_in_session,
                    target.classroom.endTime,
                    digest.classSize
                )
                else -> context.getString(
                    R.string.widget_detail_upcoming,
                    target.classroom.startTime,
                    digest.classSize
                )
            }

            prefs(context).edit()
                .putString(KEY_EYEBROW, eyebrow)
                .putString(KEY_CLASS, className)
                .putString(KEY_DETAIL, detail)
                .putString(KEY_FLAGS, flagLine(context, digest))
                .apply()
        }

        /**
         * The one line worth reading before walking in.
         *
         * Only ever one: a widget this size has room for a headline, not a report, and picking the
         * most urgent thing is more useful than three truncated ones. Order is deliberate - an
         * absence run is happening now, unresolved concerns and overdue marking are not.
         */
        private fun flagLine(context: Context, digest: TodayDigest.Digest): String {
            val named = ReminderSettings.showNames(context)

            digest.onStreak.firstOrNull()?.let { (student, days) ->
                return if (named) {
                    context.getString(R.string.widget_flag_streak_named, student.firstName, days)
                } else {
                    context.getString(R.string.widget_flag_streak_anon, digest.onStreak.size)
                }
            }
            if (digest.openConcerns.isNotEmpty()) {
                return context.getString(
                    R.string.widget_flag_concerns,
                    digest.openConcerns.sumOf { it.second }
                )
            }
            digest.overdue.firstOrNull()?.let { due ->
                return context.getString(R.string.widget_flag_overdue, due.column.name, due.outstanding)
            }
            if (digest.absentLastSession.isNotEmpty()) {
                return context.getString(
                    R.string.widget_flag_absent_last,
                    digest.absentLastSession.size
                )
            }
            return if (digest.classSize > 0) context.getString(R.string.widget_flag_clear) else ""
        }

        private fun buildViews(context: Context): RemoteViews {
            val p = prefs(context)
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            views.setTextViewText(
                R.id.widget_eyebrow,
                p.getString(KEY_EYEBROW, context.getString(R.string.widget_eyebrow_next))
            )
            views.setTextViewText(
                R.id.widget_class,
                p.getString(KEY_CLASS, context.getString(R.string.widget_no_class))
            )
            views.setTextViewText(
                R.id.widget_detail,
                p.getString(KEY_DETAIL, context.getString(R.string.widget_no_class_detail))
            )
            views.setTextViewText(R.id.widget_flags, p.getString(KEY_FLAGS, ""))

            views.setOnClickPendingIntent(R.id.widget_class, openApp(context, "today"))
            views.setOnClickPendingIntent(R.id.widget_flags, openApp(context, "today"))
            views.setOnClickPendingIntent(
                R.id.widget_action_attendance,
                openApp(context, "scan_attendance")
            )
            views.setOnClickPendingIntent(R.id.widget_action_refresh, refreshIntent(context))

            return views
        }

        private fun openApp(context: Context, route: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Notifier.EXTRA_ROUTE, route)
            }
            return PendingIntent.getActivity(
                context,
                route.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
