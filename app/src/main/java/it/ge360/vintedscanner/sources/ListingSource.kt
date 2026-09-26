package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SourceDescriptor

interface ListingSource {
    val descriptor: SourceDescriptor
    fun isConfigured(): Boolean
    suspend fun scan(search: SavedSearch): List<Listing>
}

class AuthorizedRemoteSource : ListingSource {
    override val descriptor: SourceDescriptor = SourceCatalog.authorizedRemote

    override fun isConfigured(): Boolean = false

    override suspend fun scan(search: SavedSearch): List<Listing> = emptyList()
}
