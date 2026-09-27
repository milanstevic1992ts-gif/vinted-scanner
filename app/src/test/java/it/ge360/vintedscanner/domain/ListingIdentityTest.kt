package it.ge360.vintedscanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ListingIdentityTest {
    @Test
    fun webAndDeepLinkForSameItemShareCanonicalId() {
        val web = ListingIdentity.canonicalId(
            "https://www.vinted.it/items/123456789-nike-air-max#photo"
        )
        val deep = ListingIdentity.canonicalId("vinted://item/123456789")

        assertEquals("vinted-item:123456789", web)
        assertEquals(web, deep)
    }

    @Test
    fun genericUrlsUseStableNormalizedHash() {
        val a = ListingIdentity.canonicalId("https://example.com/item/abc/")
        val b = ListingIdentity.canonicalId("https://EXAMPLE.com/item/abc/#x")
        val c = ListingIdentity.canonicalId("https://example.com/item/xyz")

        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
