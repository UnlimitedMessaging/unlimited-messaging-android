package app.unlimitedmessaging.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.unlimitedmessaging.android.api.StatusReport
import app.unlimitedmessaging.android.container

/**
 * Tells the backend that a message reached a new status. One unique work per
 * (message, status): enqueuing the same report twice is a no-op.
 *
 * Reports for one message can reach the server out of order (a SENT retried
 * after its DELIVERED went through): the server only ever moves a message
 * forward, see docs/API_CONTRACT.md.
 */
class StatusReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val report = StatusReport(
            status = inputData.getString(KEY_STATUS) ?: return Result.failure(),
            occurredAt = inputData.getString(KEY_OCCURRED_AT) ?: return Result.failure(),
            errorCode = inputData.getString(KEY_ERROR_CODE),
            parts = inputData.getInt(KEY_PARTS, 0).takeIf { it > 0 },
        )
        return runReport(TAG, runAttemptCount, container.auth) {
            container.deviceApi.reportStatus(messageId, report)
        }.toResult()
    }

    companion object {
        private const val TAG = "StatusReportWorker"
        private const val KEY_MESSAGE_ID = "messageId"
        private const val KEY_STATUS = "status"
        private const val KEY_OCCURRED_AT = "occurredAt"
        private const val KEY_ERROR_CODE = "errorCode"
        private const val KEY_PARTS = "parts"

        fun enqueue(
            context: Context,
            messageId: String,
            status: String,
            occurredAt: String,
            errorCode: String?,
            parts: Int,
        ) {
            val request = OneTimeWorkRequestBuilder<StatusReportWorker>()
                .forReport()
                .setInputData(
                    workDataOf(
                        KEY_MESSAGE_ID to messageId,
                        KEY_STATUS to status,
                        KEY_OCCURRED_AT to occurredAt,
                        KEY_ERROR_CODE to errorCode,
                        KEY_PARTS to parts,
                    ),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("status:$messageId:$status", ExistingWorkPolicy.KEEP, request)
        }
    }
}
