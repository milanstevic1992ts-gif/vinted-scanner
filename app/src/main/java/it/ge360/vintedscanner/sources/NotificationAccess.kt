package it.ge360.vintedscanner.sources

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotificationAccess {
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
}
