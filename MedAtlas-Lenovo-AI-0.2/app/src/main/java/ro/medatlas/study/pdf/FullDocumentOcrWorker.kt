package ro.medatlas.study.pdf

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.DocumentPager
import ro.medatlas.study.data.LibraryStore
import java.util.concurrent.TimeUnit

/**
 * Full-PDF OCR, without keeping large bitmaps or the entire PDF in memory.
 *
 * Android WorkManager survives leaving the reader and can restart after
 * process death. Each completed page is saved independently; reruns skip
 * non-empty saved text so user corrections are never overwritten.
 * A page with no detected text is also marked completed separately.
 */
class FullDocumentOcrWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val documentId = inputData.getString(KEY_DOCUMENT) ?: return@withContext Result.failure()
        val store = LibraryStore(applicationContext)
        val doc = store.documents().firstOrNull { it.id == documentId && it.kind == "PDF" }
            ?: return@withContext Result.failure()
        val preferences = applicationContext.getSharedPreferences("medatlas_ocr_completed", Context.MODE_PRIVATE)
        var completed = 0
        var total = 0
        try {
            DocumentPager(doc, store).use { pager ->
                total = pager.count
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    for (index in 0 until total) {
                        if (isStopped) return@withContext Result.retry()
                        val key = documentId + "." + index
                        if (preferences.getBoolean(key, false) || store.pageText(documentId, index).isNotBlank()) {
                            completed++
                            if (completed % 5 == 0 || completed == total) {
                                setProgress(Data.Builder().putInt(KEY_DONE, completed)
                                    .putInt(KEY_TOTAL, total).build())
                            }
                            continue
                        }
                        val page = pager.render(index)
                        try {
                            val result = Tasks.await(
                                recognizer.process(InputImage.fromBitmap(page, 0)),
                                60, TimeUnit.SECONDS
                            )
                            store.savePageText(documentId, index, result.text)
                            // Also mark genuinely blank pages so that they don't repeat forever.
                            preferences.edit().putBoolean(key, true).apply()
                        } finally {
                            page.recycle()
                        }
                        completed++
                        setProgress(Data.Builder().putInt(KEY_DONE, completed)
                            .putInt(KEY_TOTAL, total).build())
                    }
                } finally {
                    recognizer.close()
                }
            }
            return@withContext Result.success(Data.Builder()
                .putInt(KEY_DONE, completed).putInt(KEY_TOTAL, total).build())
        } catch (ex: Exception) {
            if (isStopped) return@withContext Result.retry()
            return@withContext if (runAttemptCount < 2) Result.retry()
            else Result.failure(Data.Builder()
                .putString(KEY_ERROR, ex.localizedMessage?.take(200) ?: "OCR failed")
                .putInt(KEY_DONE, completed).putInt(KEY_TOTAL, total).build())
        }
    }

    companion object {
        const val KEY_DOCUMENT = "document_id"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"

        fun workName(documentId: String) = "medatlas_full_ocr_" + documentId

        fun enqueue(context: Context, documentId: String) {
            val request = OneTimeWorkRequestBuilder<FullDocumentOcrWorker>()
                .setInputData(Data.Builder().putString(KEY_DOCUMENT, documentId).build())
                .setConstraints(Constraints.Builder()
                    .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("medatlas-ocr")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(documentId), ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
