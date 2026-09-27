package it.ge360.vintedscanner.live

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import it.ge360.vintedscanner.MainActivity
import it.ge360.vintedscanner.VintedScannerApplication
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class LiveScannerService : Service() {
    companion object {
        const val CHANNEL_ID = "vinted_scanner_live"
        const val NOTIFICATION_ID = 3604
        const val PREFS = "vinted_scanner_settings"
        const val KEY_LIVE = "live_mode"
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var task: ScheduledFuture<*>? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        scheduleLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        task?.cancel(true)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleLoop() {
        if (task != null) return
        task = executor.scheduleWithFixedDelay(
            {
                val app = application as VintedScannerApplication
                runCatching {
                    runBlocking {
                        val outcome = app.repository.scanActive()
                        outcome.newOpportunities.forEach(app.notificationHelper::notifyOpportunity)
                    }
                }
            },
            0,
            60,
            TimeUnit.SECONDS
        )
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Vinted Scanner Live")
            .setContentText("Sorgenti automatiche attive · catalogo limitato localmente e notifiche event-driven")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vinted Scanner Live",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica persistente della modalità Live"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
