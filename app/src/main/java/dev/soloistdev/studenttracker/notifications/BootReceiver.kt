package dev.soloistdev.studenttracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.soloistdev.studenttracker.data.BackupWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms do not survive a reboot or an app update, so they are re-armed here.
 *
 * Without this the reminders work perfectly until the teacher's phone restarts overnight and then
 * quietly never fire again - the kind of failure nobody reports, they just stop trusting the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> Unit
            else -> return
        }

        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(appContext)
                // WorkManager persists its own schedule across reboots, but re-declaring it as
                // KEEP is free and covers an install whose work was never enqueued.
                BackupWorkScheduler.sync(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
