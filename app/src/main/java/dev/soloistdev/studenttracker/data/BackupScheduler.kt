package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling local backups.
 *
 * An offline app with no cloud has exactly one serious failure mode: the device. This turns that
 * into a managed risk rather than an unspoken one, by writing a dated snapshot whenever the app
 * goes to the background and keeping the most recent few.
 *
 * Two things drive it. Backgrounding the app calls [maybeAutoBackup], which catches the data the
 * teacher just entered at zero cost. That was originally the only trigger, on the reasoning that a
 * backup is only needed after a change - but it means the last snapshot is always as old as the
 * last launch, so a teacher who marks a set of papers and then loses the phone before reopening
 * the app loses the lot. [BackupWorkScheduler] therefore also runs it on a schedule with the app
 * closed, which is the half of the promise the lifecycle hook could never keep.
 */
object BackupScheduler {

    private const val PREFS = "app_settings"
    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_LAST_RUN = "auto_backup_last_run"
    private const val KEY_RETENTION = "auto_backup_retention"
    private const val KEY_INTERVAL_HOURS = "auto_backup_interval_hours"

    const val DEFAULT_RETENTION = 5
    const val DEFAULT_INTERVAL_HOURS = 12

    private const val FILE_PREFIX = "autobackup_"

    // Snapshots are sealed under the device key. The plain suffix is still recognised on read
    // because installs that predate this wrote unencrypted files, and those are exactly the
    // files a teacher may need in an emergency.
    private const val FILE_SUFFIX = JsonSyncEngine.ENCRYPTED_SUFFIX
    private const val LEGACY_FILE_SUFFIX = ".json"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun retention(context: Context): Int =
        prefs(context).getInt(KEY_RETENTION, DEFAULT_RETENTION).coerceIn(1, 30)

    fun setRetention(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_RETENTION, count.coerceIn(1, 30)).apply()
    }

    fun intervalHours(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS).coerceIn(1, 168)

    fun setIntervalHours(context: Context, hours: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_HOURS, hours.coerceIn(1, 168)).apply()
    }

    fun lastRunMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_RUN, 0L)

    /**
     * Directory holding the rotation. Prefers external app-specific storage so a teacher can
     * copy a snapshot off the device with a file manager and no permission prompt; falls back to
     * internal storage when external is unavailable.
     */
    fun backupDir(context: Context): File {
        val external = context.getExternalFilesDir("backups")
        val dir = external ?: File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Existing snapshots, newest first. Includes legacy plaintext ones so they stay restorable. */
    fun listBackups(context: Context): List<File> =
        backupDir(context)
            .listFiles { f ->
                f.isFile && f.name.startsWith(FILE_PREFIX) &&
                        (f.name.endsWith(FILE_SUFFIX) || f.name.endsWith(LEGACY_FILE_SUFFIX))
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Runs a backup only when enabled and the throttle interval has elapsed. */
    suspend fun maybeAutoBackup(context: Context, repository: StudentRepository): File? = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) return@withContext null

        val elapsed = System.currentTimeMillis() - lastRunMillis(context)
        if (elapsed < intervalHours(context) * 60L * 60L * 1000L) return@withContext null

        runBackup(context, repository)
    }

    /**
     * Why a backup run produced no file.
     *
     * A caller in the foreground can treat both empty outcomes the same and shrug. A background
     * worker cannot: "there was nothing to save" is a finished job, and "the key store would not
     * open" is a job to retry - and eventually to tell the teacher about, because a backup that
     * has been silently failing for a month is worse than one that was never switched on.
     */
    sealed interface Outcome {
        data class Written(val file: File) : Outcome
        data object NothingToSave : Outcome
        data class Failed(val error: Exception?) : Outcome
    }

    /** Writes a snapshot immediately, regardless of the throttle. */
    suspend fun runBackup(context: Context, repository: StudentRepository): File? =
        (runBackupWithOutcome(context, repository) as? Outcome.Written)?.file

    suspend fun runBackupWithOutcome(
        context: Context,
        repository: StudentRepository
    ): Outcome = withContext(Dispatchers.IO) {
        try {
            // Skip a genuinely empty database: a snapshot of nothing would rotate a real backup
            // out of the retention window. Checked across every table the backup carries, so a
            // setup with classrooms or templates but no students yet is still protected.
            val hasAnything = repository.getAllActiveStudents().isNotEmpty() ||
                    repository.getAllClassrooms().isNotEmpty() ||
                    repository.getAllAttendanceRecords().isNotEmpty() ||
                    repository.getAllAssessmentColumns().isNotEmpty() ||
                    repository.getAllFormTemplates().isNotEmpty() ||
                    repository.getAllSavedFilters().isNotEmpty()
            if (!hasAnything) return@withContext Outcome.NothingToSave

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val target = File(backupDir(context), "$FILE_PREFIX$stamp$FILE_SUFFIX")

            val ok = JsonSyncEngine.writeEncryptedBackupTo(context, repository, target)
            if (!ok) return@withContext Outcome.Failed(null)

            prefs(context).edit().putLong(KEY_LAST_RUN, System.currentTimeMillis()).apply()
            sealLegacyPlaintextBackups(context)
            pruneOldBackups(context)
            Outcome.Written(target)
        } catch (t: Throwable) {
            // Throwable: this runs from a WorkManager worker with the app closed, and the database
            // it opens fails with Errors as well as exceptions. Reporting Failed lets the worker
            // retry; letting an Error through would kill a background process instead.
            t.printStackTrace()
            Outcome.Failed(t as? Exception ?: RuntimeException(t))
        }
    }

    /** Keeps only the newest [retention] snapshots. */
    private fun pruneOldBackups(context: Context) {
        val keep = retention(context)
        listBackups(context).drop(keep).forEach { stale ->
            try {
                stale.delete()
            } catch (_: Exception) {
                // A snapshot we cannot delete is not worth failing a backup over
            }
        }
    }

    /**
     * Re-seals any snapshot left in the clear by an older install, then removes the plaintext.
     *
     * Runs after a successful backup, so it only ever deletes a plaintext file once there is a
     * sealed copy of the same data - and leaves the original alone if sealing fails, because a
     * readable backup beats a tidy directory.
     */
    private suspend fun sealLegacyPlaintextBackups(context: Context) {
        backupDir(context)
            .listFiles { f ->
                f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(LEGACY_FILE_SUFFIX)
            }
            ?.forEach { legacy ->
                try {
                    val sealed = File(
                        legacy.parentFile,
                        legacy.name.removeSuffix(LEGACY_FILE_SUFFIX) + FILE_SUFFIX
                    )
                    if (!sealed.exists()) {
                        val text = legacy.readText(Charsets.UTF_8)
                        if (JsonSyncEngine.sealTextTo(context, text, sealed)) {
                            sealed.setLastModified(legacy.lastModified())
                            legacy.delete()
                        }
                    } else {
                        legacy.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }

    /**
     * Hands a snapshot to another app.
     *
     * The stored file is sealed under a key that never leaves this device, so it is meaningless
     * anywhere else; sharing therefore exports a readable copy into the cache. That is the same
     * plaintext the explicit "export JSON" action produces, and it happens only when the teacher
     * asks for it - unlike a snapshot sitting on disk indefinitely.
     */
    suspend fun shareBackup(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val readable = File(exportDir, file.name.removeSuffix(FILE_SUFFIX) + LEGACY_FILE_SUFFIX)
            if (readable.exists()) readable.delete()
            readable.writeText(JsonSyncEngine.readLocalBackup(context, file), Charsets.UTF_8)
            readable.deleteOnExit()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", readable)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(intent, "Share backup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun describe(file: File): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US)
        val sizeKb = (file.length() / 1024).coerceAtLeast(1)
        return "${sdf.format(Date(file.lastModified()))}  •  ${sizeKb} KB"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
