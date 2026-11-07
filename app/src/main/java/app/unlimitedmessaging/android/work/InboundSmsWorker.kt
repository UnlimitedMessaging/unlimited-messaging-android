package app.unlimitedmessaging.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.unlimitedmessaging.android.api.InboundSms
import app.unlimitedmessaging.android.container
import app.unlimitedmessaging.android.data.EntryStatus
import java.io.File

/** Uploads one received SMS. Unique per dedup key, so a duplicate broadcast is uploaded once. */
class InboundSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val dedupKey = inputData.getString(KEY_DEDUP) ?: return Result.failure()
        val bodyFile = inputData.getString(KEY_TEXT_FILE)?.let(::File)
        val text = inputData.getString(KEY_TEXT) ?: bodyFile?.takeIf { it.exists() }?.readText()
            ?: return Result.failure()

        val sms = InboundSms(
            subscriptionId = inputData.getInt(KEY_SUBSCRIPTION, -1),
            from = inputData.getString(KEY_FROM) ?: return Result.failure(),
            text = text,
            sentAt = inputData.getString(KEY_SENT_AT) ?: return Result.failure(),
            receivedAt = inputData.getString(KEY_RECEIVED_AT) ?: return Result.failure(),
            dedupKey = dedupKey,
        )
        val outcome = runReport(TAG, runAttemptCount, container.auth) {
            container.deviceApi.reportInbound(sms)
        }
        if (outcome == ReportOutcome.DONE) container.activityLog.setStatus(dedupKey, EntryStatus.FORWARDED)
        if (outcome != ReportOutcome.RETRY) bodyFile?.delete()
        return outcome.toResult()
    }

    companion object {
        private const val TAG = "InboundSmsWorker"
        private const val KEY_SUBSCRIPTION = "subscriptionId"
        private const val KEY_FROM = "from"
        private const val KEY_TEXT = "text"
        private const val KEY_TEXT_FILE = "textFile"
        private const val KEY_SENT_AT = "sentAt"
        private const val KEY_RECEIVED_AT = "receivedAt"
        private const val KEY_DEDUP = "dedupKey"

        /**
         * WorkManager input is capped at 10 KB. A long concatenated SMS can
         * exceed it, so above this many characters the text goes to a file in
         * the app's private storage instead.
         */
        private const val INLINE_TEXT_MAX = 2_000

        fun enqueue(
            context: Context,
            subscriptionId: Int,
            from: String,
            text: String,
            sentAt: String,
            receivedAt: String,
            dedupKey: String,
        ) {
            val textData = if (text.length <= INLINE_TEXT_MAX) {
                KEY_TEXT to text
            } else {
                val file = File(context.filesDir, "inbound").apply { mkdirs() }.resolve("$dedupKey.txt")
                file.writeText(text)
                KEY_TEXT_FILE to file.absolutePath
            }
            val request = OneTimeWorkRequestBuilder<InboundSmsWorker>()
                .forReport()
                .setInputData(
                    workDataOf(
                        KEY_SUBSCRIPTION to subscriptionId,
                        KEY_FROM to from,
                        textData,
                        KEY_SENT_AT to sentAt,
                        KEY_RECEIVED_AT to receivedAt,
                        KEY_DEDUP to dedupKey,
                    ),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("inbound:$dedupKey", ExistingWorkPolicy.KEEP, request)
        }
    }
}
