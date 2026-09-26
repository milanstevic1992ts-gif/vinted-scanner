package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.model.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCatalogTest {
    @Test
    fun androidShareIsManualAndReadyWithoutConfiguration() {
        val source = SourceCatalog.androidShare

        assertEquals(SourceCatalog.ANDROID_SHARE_ID, source.id)
        assertEquals(SourceKind.MANUAL_SHARE, source.kind)
        assertFalse(source.supportsAutomaticScan)
        assertFalse(source.requiresConfiguration)
    }

    @Test
    fun vintedNotificationsAreAutomaticAndPermissionBased() {
        val source = SourceCatalog.vintedNotifications

        assertEquals(SourceCatalog.VINTED_NOTIFICATIONS_ID, source.id)
        assertEquals(SourceKind.VINTED_NOTIFICATIONS, source.kind)
        assertTrue(source.supportsAutomaticScan)
        assertTrue(source.requiresConfiguration)
        assertEquals("fr.vinted", SourceCatalog.VINTED_ANDROID_PACKAGE)
    }

    @Test
    fun authorizedRemoteIsAutomaticButRequiresConfiguration() {
        val source = SourceCatalog.authorizedRemote
        val connector = AuthorizedRemoteSource()

        assertEquals(SourceCatalog.AUTHORIZED_REMOTE_ID, source.id)
        assertEquals(SourceKind.AUTHORIZED_REMOTE, source.kind)
        assertTrue(source.supportsAutomaticScan)
        assertTrue(source.requiresConfiguration)
        assertFalse(connector.isConfigured())
    }

    @Test
    fun sourceIdsAreUnique() {
        val ids = SourceCatalog.defaults.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
