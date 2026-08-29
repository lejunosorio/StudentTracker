package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.soloistdev.studenttracker.notifications.Notifier
import java.util.concurrent.TimeUnit

/**
 * Runs a rolling backup with the app closed.
 *
 * The lifecycle hook in MainActivity only fires when the teacher happens to open and leave the
 * app, so the newest snapshot was always as old as the last launch. For an offline app whose only
 * serious failure mode is the device itself, that is the gap that matters: a phone lost on Friday
 * takes the whole week with it if the app was last opened on Monday.
 *
 * WorkManager rather than another alarm because this is the one job that has to survive things
 * alarms do not - reboots, app updates, doze, and being force-stopped - and because it can be told
 * to wait for a battery that is not about to die instead of writing a snapshot at 2%.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!BackupScheduler.isEnabled(context)) return Result.success()

        return when (val outcome = BackupScheduler.runBackupWithOutcome(context, StudentRepository(context))) {
            is BackupScheduler.Outcome.Written -> Result.success()

            // An empty database is a finished job, not a failure. Retrying would spin every
            // interval for a teacher who has not added anyone yet.
            BackupScheduler.Outcome.NothingToSave -> Result.success()

            is BackupScheduler.Outcome.Failed -> {
                outcome.error?.printStackTrace()
                // The usual cause is the key store not being readable yet - common shortly after a
                // reboot, before the device has been unlocked once. That fixes itself, so retry
                // quietly and only speak up once it clearly has not.
                if (runAttemptCount >= MAX_QUIET_ATTEMPTS) {
                    Notifier.postBackupFailure(context)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }
    }

    private companion object {
        const val MAX_QUIET_ATTEMPTS = 3
    }
}

/** Keeps the periodic backup in step with the teacher's backup settings. */
object BackupWorkScheduler {

    private const val WORK_NAME = "auto_backup"

    /**
     * Enqueues, updates or cancels the periodic backup to match the current settings.
     *
     * Safe to call repeatedly - on launch, and whenever a backup setting changes. UPDATE rather
     * than KEEP so changing the interval takes effect now instead of at the next reinstall.
     */
    fun sync(context: Context) {
        val appContext = context.applicationContext
        val manager = try {
            WorkManager.getInstance(appContext)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        if (!BackupScheduler.isEnabled(appContext)) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        // WorkManager will not run a period shorter than 15 minutes, and the setting is in hours,
        // so the floor here only guards against a corrupted preference.
        val hours = BackupScheduler.intervalHours(appContext).toLong().coerceAtLeast(1L)

        val request = PeriodicWorkRequestBuilder<BackupWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()

        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
