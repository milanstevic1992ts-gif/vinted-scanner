package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.domain.OpportunityScorer
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PricePoint
import it.ge360.vintedscanner.model.SavedSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ListingSource {
    suspend fun scan(search: SavedSearch): List<Listing>
}

class OfflineListingSource : ListingSource {
    override suspend fun scan(search: SavedSearch): List<Listing> = emptyList()
}

data class ScanOutcome(
    val scanned: Int,
    val newOpportunities: List<Listing>
)

class ScannerRepository(
    private val database: AppDatabase,
    private val source: ListingSource = OfflineListingSource()
) {
    suspend fun searches(): List<SavedSearch> = withContext(Dispatchers.IO) {
        database.getSearches()
    }

    suspend fun saveSearch(search: SavedSearch): Long = withContext(Dispatchers.IO) {
        database.saveSearch(search)
    }

    suspend fun deleteSearch(id: Long) = withContext(Dispatchers.IO) {
        database.deleteSearch(id)
    }

    suspend fun setSearchActive(id: Long, active: Boolean) = withContext(Dispatchers.IO) {
        database.setSearchActive(id, active)
    }

    suspend fun opportunities(): List<Listing> = withContext(Dispatchers.IO) {
        database.getTopListings()
    }

    suspend fun favorites(): List<Listing> = withContext(Dispatchers.IO) {
        database.getTopListings(favoritesOnly = true)
    }

    suspend fun setFavorite(listingId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        database.setFavorite(listingId, favorite)
    }

    suspend fun priceHistory(listingId: String): List<PricePoint> = withContext(Dispatchers.IO) {
        database.getPriceHistory(listingId)
    }

    suspend fun importSharedListing(
        title: String,
        price: Double,
        marketMedian: Double?,
        url: String,
        condition: String?
    ): Pair<Listing, Boolean> = withContext(Dispatchers.IO) {
        val raw = Listing(
            id = SharedListingParser.stableId(url),
            searchId = 0,
            title = title.trim().ifBlank { "Annuncio condiviso" },
            price = price,
            marketMedian = marketMedian,
            url = url.trim(),
            condition = condition?.trim()?.takeIf(String::isNotBlank)
        )
        val score = OpportunityScorer.score(raw)
        val scored = raw.copy(
            score = score.score,
            estimatedMargin = score.estimatedMargin,
            riskFlags = score.riskFlags
        )
        val isNew = database.upsertListing(scored)
        scored to isNew
    }

    suspend fun scanActive(): ScanOutcome = withContext(Dispatchers.IO) {
        var scanned = 0
        val newOpportunities = mutableListOf<Listing>()

        database.getSearches()
            .filter { it.active }
            .forEach { search ->
                source.scan(search)
                    .asSequence()
                    .filter { search.maxPrice == null || it.price <= search.maxPrice }
                    .filter { search.brand.isNullOrBlank() || it.title.contains(search.brand, ignoreCase = true) }
                    .filter {
                        search.condition.isNullOrBlank() ||
                            it.condition?.contains(search.condition, ignoreCase = true) == true
                    }
                    .filter {
                        search.size.isNullOrBlank() ||
                            it.title.contains(search.size, ignoreCase = true)
                    }
                    .forEach { raw ->
                        scanned++
                        val result = OpportunityScorer.score(raw)
                        val scored = raw.copy(
                            searchId = search.id,
                            score = result.score,
                            estimatedMargin = result.estimatedMargin,
                            riskFlags = result.riskFlags
                        )
                        val isNew = database.upsertListing(scored)
                        val clearsMargin = scored.estimatedMargin?.let { it >= search.minMargin } ?: false
                        if (isNew && scored.score >= 75 && clearsMargin) {
                            newOpportunities += scored
                        }
                    }
            }

        ScanOutcome(scanned, newOpportunities)
    }
}
