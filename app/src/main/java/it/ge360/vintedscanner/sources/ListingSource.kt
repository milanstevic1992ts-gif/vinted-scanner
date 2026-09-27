package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SourceDescriptor

interface ListingSource {
    val descriptor: SourceDescriptor
    val minimumScanIntervalMs: Long get() = 0L
    fun isConfigured(): Boolean
    suspend fun scan(search: SavedSearch): List<Listing>
}
