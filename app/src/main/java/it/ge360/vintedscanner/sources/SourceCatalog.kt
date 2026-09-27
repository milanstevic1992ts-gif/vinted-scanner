package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.model.SourceDescriptor
import it.ge360.vintedscanner.model.SourceKind

object SourceCatalog {
    const val ANDROID_SHARE_ID = "android_share"
    const val VINTED_NOTIFICATIONS_ID = "vinted_notifications"
    const val VINTED_CATALOG_ID = "vinted_catalog"
    const val VINTED_ANDROID_PACKAGE = "fr.vinted"
    const val VINTED_ITALY_BASE_URL = "https://www.vinted.it"

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

    val vintedCatalog = SourceDescriptor(
        id = VINTED_CATALOG_ID,
        name = "Catalogo Vinted sperimentale",
        kind = SourceKind.VINTED_CATALOG,
        supportsAutomaticScan = true,
        requiresConfiguration = false,
        defaultEnabled = false
    )

    val defaults = listOf(androidShare, vintedNotifications, vintedCatalog)
}
