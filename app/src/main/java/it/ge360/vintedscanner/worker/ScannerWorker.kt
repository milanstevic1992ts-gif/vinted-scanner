package it.ge360.vintedscanner.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.ge360.vintedscanner.VintedScannerApplication

class ScannerWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VintedScannerApplication
        return runCatching {
            val outcome = app.repository.scanActive()
            outcome.newOpportunities.forEach(app.notificationHelper::notifyOpportunity)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
