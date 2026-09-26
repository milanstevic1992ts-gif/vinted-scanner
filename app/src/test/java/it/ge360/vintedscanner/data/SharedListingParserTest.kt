package it.ge360.vintedscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SharedListingParserTest {
    @Test
    fun parsesSharedText() {
        val draft = SharedListingParser.parse(
            "Nike Air Max 95\n42,50 €\nhttps://www.vinted.it/items/123-air-max"
        )

        assertEquals("Nike Air Max 95", draft.titleGuess)
        assertEquals(42.50, draft.priceGuess ?: 0.0, 0.001)
        assertEquals("https://www.vinted.it/items/123-air-max", draft.url)
    }

    @Test
    fun parsesVintedDeepLinkFromNotificationText() {
        val draft = SharedListingParser.parse(
            "Festool ETS EC 150\n149,90 €\nvinted://item/12345"
        )

        assertEquals("vinted://item/12345", draft.url)
        assertEquals(149.90, draft.priceGuess ?: 0.0, 0.001)
    }

    @Test
    fun stableIdNormalizesFragmentAndTrailingSlash() {
        val a = SharedListingParser.stableId("https://www.vinted.it/items/123-test/")
        val b = SharedListingParser.stableId("https://www.vinted.it/items/123-test/#photo")

        assertNotNull(a)
        assertEquals(a, b)
    }
}
