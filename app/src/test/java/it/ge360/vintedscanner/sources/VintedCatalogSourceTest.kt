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

        assertTrue(url.startsWith("https://www.vinted.it/catalog?"))
        assertTrue(url.contains("search_text=Festool+levigatrice"))
        assertTrue(url.contains("price_to=200"))
        assertTrue(url.contains("order=newest_first"))
                assertFalse(url.contains("proxy"))
        assertFalse(url.contains("token"))
    }

    @Test
    fun nearDuplicateBrandTypoDoesNotPolluteSearchQuery() {
        val source = VintedCatalogSource()
        val url = source.buildSearchUrl(
            SavedSearch(
                id = 2,
                query = "hilti",
                maxPrice = 100.0,
                size = null,
                brand = "hili"
            )
        )

        assertTrue(url.contains("search_text=hilti"))
        assertFalse(url.contains("hili+hilti"))
    }

    @Test
    fun parsesPublicCatalogHtmlCard() {
        val source = VintedCatalogSource()
        val html = """
            <html><body>
              <div class="card">
                <a href="/items/123456789-hilti-te-30">
                  <img
                    src="https://images1.vinted.net/test.jpg"
                    alt="Hilti TE 30, brand: Hilti, condizioni: Ottime, 85.00 €, 90.10 € include la Protezione acquisti"
                  />
                </a>
              </div>
            </body></html>
        """.trimIndent()

        val items = source.parseCatalogHtml(html, searchId = 7)

        assertTrue(items.size == 1)
        val item = items.single()
        assertTrue(item.id == "vinted-item:123456789")
        assertTrue(item.title == "Hilti TE 30")
        assertTrue(item.price == 85.0)
        assertTrue(item.condition?.contains("Hilti") == true)
        assertTrue(item.condition?.contains("Ottime") == true)
        assertTrue(item.url.contains("/items/123456789-hilti-te-30"))
    }

    @Test
    fun parsesItalianCommaPriceFromPublicCatalog() {
        val source = VintedCatalogSource()
        val html = """
            <a href="/items/987654321-festool">
              <img alt="Festool ETS, brand: Festool, condizioni: Buone, 149,90 €" />
            </a>
        """.trimIndent()

        val item = source.parseCatalogHtml(html, searchId = 8).single()

        assertTrue(kotlin.math.abs(item.price - 149.90) < 0.001)
    }

    @Test
    fun sourceIsConfiguredButThrottled() {
        val source = VintedCatalogSource()

        assertTrue(source.isConfigured())
        assertTrue(source.minimumScanIntervalMs >= 5L * 60L * 1000L)
    }
}
