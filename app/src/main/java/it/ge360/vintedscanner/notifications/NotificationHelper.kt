package it.ge360.vintedscanner.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.ge360.vintedscanner.MainActivity
import it.ge360.vintedscanner.model.Listing

class NotificationHelper(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "vinted_scanner_opportunities"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Occasioni Vinted Scanner",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avvisi per annunci interessanti trovati o importati"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun notifyOpportunity(listing: Listing) {
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            listing.id.hashCode(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val marginText = listing.estimatedMargin?.let {
            " · margine stimato €" + "%.2f".format(it)
        }.orEmpty()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Possibile occasione: " + listing.title)
            .setContentText("€" + "%.2f".format(listing.price) + " · " + listing.score + "/100" + marginText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Prezzo €" + "%.2f".format(listing.price) +
                        " · punteggio " + listing.score + "/100" + marginText
                )
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(listing.id.hashCode(), notification)
        }
    }
}
