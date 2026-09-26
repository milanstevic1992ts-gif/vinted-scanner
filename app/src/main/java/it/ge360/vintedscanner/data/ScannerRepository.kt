package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.domain.OpportunityScorer
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ListingSource {
    suspend fun scan(search: SavedSearch): List<Listing>
}

class OfflineListingSource : ListingSource {
    override suspend fun scan(search: SavedSearch): List<Listing> = emptyList()
}

class ScannerRepository(
    private val database: AppDatabase,
    private val source: ListingSource = OfflineListingSource()
) {
    suspend fun searches() = withContext(Dispatchers.IO) { database.getSearches() }
    suspend fun addSearch(search: SavedSearch) = withContext(Dispatchers.IO) { database.insertSearch(search) }
    suspend fun opportunities() = withContext(Dispatchers.IO) { database.getTopListings() }

    suspend fun scanActive(): Int = withContext(Dispatchers.IO) {
        var saved = 0
        database.getSearches().filter { it.active }.forEach { search ->
            source.scan(search).forEach { raw ->
                val result = OpportunityScorer.score(raw)
                database.upsertListing(
                    raw.copy(
                        score = result.score,
                        estimatedMargin = result.estimatedMargin,
                        riskFlags = result.riskFlags
                    )
                )
                saved++
            }
        }
        saved
    }
}
