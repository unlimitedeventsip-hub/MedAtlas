package ro.medatlas.study

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.LibraryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Explicitly authorized, one-way snapshots into an Android SAF folder.
 *
 * Google Drive is supported when it exposes a writable folder in the Android
 * Documents provider. This does not give MedAtlas account-wide Drive access,
 * and it is NOT two-way synchronization.
 */
internal object DriveAutoBackup {
    private const val PREF = "medatlas_drive_backup"
    private const val KEY_TREE = "destination_tree_uri"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_LAST_ARCHIVE = "last_archive"
    private const val KEY_LAST_ERROR = "last_error"
    private const val PERIODIC = "medatlas-drive-backup-daily"
    private const val ONCE = "medatlas-drive-backup-now"
    const val SNAPSHOT_PREFIX = "MedAtlas-auto-"

    fun destination(context: Context): Uri? {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_TREE, null) ?: return null
        return runCatching { Uri.parse(saved) }.getOrNull()
    }

    fun lastSuccess(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST_SUCCESS, 0L)

    fun lastArchive(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_ARCHIVE, "").orEmpty()

    fun lastError(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_ERROR, "").orEmpty()

    fun enabled(context: Context) = destination(context) != null

    data class SavedCopy(val name: String, val uri: Uri, val modifiedAt: Long, val bytes: Long)

    /**
     * Lists only MedAtlas-created snapshots within the previously authorized
     * folder. The caller should run this off the main thread: cloud providers
     * can make this a network operation.
     */
    fun savedCopies(context: Context): List<SavedCopy> {
        val folderUri = destination(context) ?: return emptyList()
        val grant = context.contentResolver.persistedUriPermissions.any {
            it.uri == folderUri && it.isReadPermission
        }
        if (!grant) error("Accesul la copiile salvate a expirat. Selectează folderul din nou.")
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("Folderul de backup nu este disponibil.")
        return folder.listFiles().asSequence()
            .filter { file ->
                file.isFile && file.name.orEmpty().startsWith(SNAPSHOT_PREFIX) &&
                    file.name.orEmpty().endsWith(".zip", ignoreCase = true)
            }
            .map { file ->
                SavedCopy(
                    name = file.name.orEmpty(),
                    uri = file.uri,
                    modifiedAt = file.lastModified(),
                    bytes = file.length()
                )
            }
            .sortedByDescending { it.name }
            .take(20)
            .toList()
    }

    /** Called only after the user picks a folder in ACTION_OPEN_DOCUMENT_TREE. */
    fun enable(context: Context, selected: Uri) {
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(
            selected,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        require(resolver.persistedUriPermissions.any {
            it.uri == selected && it.isWritePermission
        }) { "Nu s-a acordat permisiunea de scriere pentru acest folder." }
        val folder = DocumentFile.fromTreeUri(context, selected)
        require(folder != null && folder.isDirectory && folder.canWrite()) {
            "Folderul ales nu permite salvarea automată. Alege alt folder."
        }
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val previous = destination(context)
        prefs.edit().putString(KEY_TREE, selected.toString())
            .remove(KEY_LAST_ERROR).apply()

        // Release the old grant only when a different destination was chosen.
        if (previous != null && previous != selected) runCatching {
            resolver.releasePersistableUriPermission(
                previous,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<DriveAutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic
        )
        backupNow(context)
    }

    fun backupNow(context: Context) {
        require(enabled(context)) { "Alege mai întâi un folder pentru backup." }
        val work = OneTimeWorkRequestBuilder<DriveAutoBackupWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE, ExistingWorkPolicy.KEEP, work
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(ONCE)
        val uri = destination(context)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY_TREE).remove(KEY_LAST_ERROR).apply()
        if (uri != null) runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    internal fun recordSuccess(context: Context, archiveName: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .putString(KEY_LAST_ARCHIVE, archiveName)
            .remove(KEY_LAST_ERROR).apply()
    }

    internal fun recordFailure(context: Context, reason: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, reason.take(300)).apply()
    }
}

/**
 * One completed ZIP snapshot per run. Old snapshots are trimmed to 8 only
 * AFTER a new archive has been exported successfully. Never deletes study
 * files on the tablet and never restores without a user's action.
 */
internal class DriveAutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val destination = DriveAutoBackup.destination(context)
            ?: return@withContext Result.success()
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == destination && it.isWritePermission
        }
        if (!hasPermission) {
            DriveAutoBackup.recordFailure(context, "Permisiunea folderului a expirat. Alege din nou folderul.")
            return@withContext Result.failure()
        }
        val folder = DocumentFile.fromTreeUri(context, destination)
        if (folder == null || !folder.canWrite()) {
            DriveAutoBackup.recordFailure(context, "Folderul selectat nu permite scrierea.")
            return@withContext Result.failure()
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ROOT).format(Date())
        val filename = DriveAutoBackup.SNAPSHOT_PREFIX + timestamp + "-" +
            UUID.randomUUID().toString().take(8) + ".zip"
        var created: DocumentFile? = null
        try {
            created = folder.createFile("application/zip", filename)
                ?: error("Providerul de fișiere nu poate crea arhiva.")
            LibraryStore(context).exportBackup(created.uri)
            DriveAutoBackup.recordSuccess(context, created.name ?: filename)

            // Delete only MedAtlas-generated older ZIP snapshots in the selected folder.
            // An unsupported provider may throw during listing; this must not fail backup.
            runCatching {
                folder.listFiles().asSequence()
                    .filter {
                        it.isFile &&
                            it.name.orEmpty().startsWith(DriveAutoBackup.SNAPSHOT_PREFIX) &&
                            it.name.orEmpty().endsWith(".zip", ignoreCase = true)
                    }
                    .sortedByDescending { it.name.orEmpty() }
                    .drop(8)
                    .forEach { it.delete() }
            }
            Result.success()
        } catch (error: Exception) {
            runCatching { created?.delete() }
            DriveAutoBackup.recordFailure(
                context, error.localizedMessage ?: "Nu s-a putut crea backupul."
            )
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
