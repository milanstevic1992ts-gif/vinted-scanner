package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.model.SourceDescriptor
import it.ge360.vintedscanner.model.SourceKind

object SourceCatalog {
    const val ANDROID_SHARE_ID = "android_share"
    const val VINTED_NOTIFICATIONS_ID = "vinted_notifications"
    const val VINTED_ANDROID_PACKAGE = "fr.vinted"
    const val AUTHORIZED_REMOTE_ID = "authorized_remote"

    val androidShare = SourceDescriptor(
        id = ANDROID_SHARE_ID,
        name = "Condivisione Android",
        kind = SourceKind.MANUAL_SHARE,
        supportsAutomaticScan = false,
        requiresConfiguration = false
    )

    val vintedNotifications = SourceDescriptor(
        id = VINTED_NOTIFICATIONS_ID,
        name = "Notifiche Vinted",
        kind = SourceKind.VINTED_NOTIFICATIONS,
        supportsAutomaticScan = true,
        requiresConfiguration = true
    )

    val authorizedRemote = SourceDescriptor(
        id = AUTHORIZED_REMOTE_ID,
        name = "API remota autorizzata",
        kind = SourceKind.AUTHORIZED_REMOTE,
        supportsAutomaticScan = true,
        requiresConfiguration = true
    )

    val defaults = listOf(androidShare, vintedNotifications, authorizedRemote)
}
