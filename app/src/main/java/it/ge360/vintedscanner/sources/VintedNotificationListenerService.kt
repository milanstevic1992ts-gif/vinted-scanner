package it.ge360.vintedscanner.sources

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.ge360.vintedscanner.VintedScannerApplication
import it.ge360.vintedscanner.live.LiveScannerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VintedNotificationListenerService : NotificationListenerService() {
    companion object {
        const val VINTED_PACKAGE = "fr.vinted"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        val app = application as VintedScannerApplication
        scope.launch {
            app.repository.refreshSourceDiagnostics()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName != VINTED_PACKAGE) return

        val app = application as VintedScannerApplication
        val liveEnabled = app.getSharedPreferences(LiveScannerService.PREFS, 0)
            .getBoolean(LiveScannerService.KEY_LIVE, false)
        if (!liveEnabled) return

        val rawText = extractNotificationText(notification.notification)
        if (rawText.isBlank()) return

        scope.launch {
            val listing = app.repository.ingestVintedNotification(
                rawText = rawText,
                postedAt = notification.postTime
            )
            if (
                listing != null &&
                listing.score >= 75 &&
                (listing.estimatedMargin ?: 0.0) > 0
            ) {
                app.notificationHelper.notifyOpportunity(listing)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun extractNotificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = linkedSetOf<String>()

        fun add(value: CharSequence?) {
            value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(parts::add)
        }

        add(extras.getCharSequence(Notification.EXTRA_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.forEach(::add)

        return parts.joinToString("\n")
    }
}
