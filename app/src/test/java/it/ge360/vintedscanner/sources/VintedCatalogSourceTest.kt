package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.model.SavedSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VintedCatalogSourceTest {
    @Test
    fun buildsReadOnlyItalianCatalogQuery() {
        val source = VintedCatalogSource()
        val url = source.buildSearchUrl(
            SavedSearch(
                id = 1,
                query = "levigatrice",
                maxPrice = 200.0,
                size = null,
                brand = "Festool"
            )
        )

        assertTrue(url.startsWith("https://www.vinted.it/api/v2/catalog/items?"))
        assertTrue(url.contains("search_text=Festool+levigatrice"))
        assertTrue(url.contains("price_to=200"))
        assertTrue(url.contains("order=newest_first"))
        assertTrue(url.contains("per_page=24"))
        assertFalse(url.contains("proxy"))
        assertFalse(url.contains("token"))
    }

    @Test
    fun sourceIsConfiguredButThrottled() {
        val source = VintedCatalogSource()

        assertTrue(source.isConfigured())
        assertTrue(source.minimumScanIntervalMs >= 5L * 60L * 1000L)
    }
}
