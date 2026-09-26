package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.SavedSearch
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExporterTest {
    @Test
    fun exportsVersionedJsonWithCoreData() {
        val json = BackupExporter.toJson(
            searches = listOf(
                SavedSearch(
                    id = 7,
                    query = "Nike Air Max 95",
                    maxPrice = 70.0,
                    size = "43",
                    brand = "Nike"
                )
            ),
            listings = listOf(
                Listing(
                    id = "abc",
                    searchId = 7,
                    title = "Nike Air Max 95",
                    price = 45.0,
                    url = "https://example.invalid/item",
                    favorite = true
                )
            ),
            profile = PreferenceProfile(mapOf("nike" to 4)),
            generatedAt = 0L
        )

        assertTrue(json.contains("\"format\": \"vinted-scanner-backup\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("Nike Air Max 95"))
        assertTrue(json.contains("\"favorite\":true"))
        assertTrue(json.contains("\"nike\":4"))
    }
}
