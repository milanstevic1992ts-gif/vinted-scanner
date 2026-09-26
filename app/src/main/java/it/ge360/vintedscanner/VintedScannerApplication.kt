package it.ge360.vintedscanner

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.ge360.vintedscanner.data.AppDatabase
import it.ge360.vintedscanner.data.ScannerRepository
import it.ge360.vintedscanner.worker.ScannerWorker
import java.util.concurrent.TimeUnit

class VintedScannerApplication : Application() {
    lateinit var repository: ScannerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ScannerRepository(AppDatabase(this))
        scheduleScanner()
    }

    private fun scheduleScanner() {
        val request = PeriodicWorkRequestBuilder<ScannerWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vinted-scanner-periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
