package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.domain.MarketEstimator
import it.ge360.vintedscanner.domain.OpportunityScorer
import it.ge360.vintedscanner.domain.PreferenceEngine
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
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
        recomputeIntelligence()
        database.getTopListings()
    }

    suspend fun favorites(): List<Listing> = withContext(Dispatchers.IO) {
        database.getTopListings(favoritesOnly = true)
    }

    suspend fun archive(): List<Listing> = withContext(Dispatchers.IO) {
        database.getArchiveListings()
    }

    suspend fun backupJson(): String = withContext(Dispatchers.IO) {
        BackupExporter.toJson(
            searches = database.getSearches(),
            listings = database.getArchiveListings(),
            profile = database.getPreferenceProfile()
        )
    }

    suspend fun preferenceProfile(): PreferenceProfile = withContext(Dispatchers.IO) {
        database.getPreferenceProfile()
    }

    suspend fun setFavorite(listingId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        database.setFavorite(listingId, favorite)
        recomputeIntelligence()
    }

    suspend fun setNotInterested(listingId: String) = withContext(Dispatchers.IO) {
        database.setNotInterested(listingId)
        recomputeIntelligence()
    }

    suspend fun priceHistory(listingId: String): List<PricePoint> = withContext(Dispatchers.IO) {
        database.getPriceHistory(listingId)
    }

    suspend fun importSharedListing(
        title: String,
        price: Double,
        extraCosts: Double,
        marketMedian: Double?,
        url: String,
        condition: String?
    ): Pair<Listing, Boolean> = withContext(Dispatchers.IO) {
        val raw = Listing(
            id = SharedListingParser.stableId(url),
            searchId = 0,
            title = title.trim().ifBlank { "Annuncio condiviso" },
            price = price,
            shipping = extraCosts.coerceAtLeast(0.0),
            marketMedian = marketMedian,
            marketSampleCount = if (marketMedian != null) 1 else 0,
            marketConfidence = if (marketMedian != null) 25 else 0,
            url = url.trim(),
            condition = condition?.trim()?.takeIf(String::isNotBlank)
        )

        val initialScore = OpportunityScorer.score(raw)
        val initial = raw.copy(
            score = initialScore.score,
            estimatedMargin = initialScore.estimatedMargin,
            preferenceBoost = initialScore.preferenceBoost,
            riskFlags = initialScore.riskFlags
        )

        val isNew = database.upsertListing(initial)
        recomputeIntelligence()

        (database.getListing(initial.id) ?: initial) to isNew
    }

    suspend fun scanActive(): ScanOutcome = withContext(Dispatchers.IO) {
        var scanned = 0
        val newIds = linkedSetOf<String>()

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
                        val prepared = raw.copy(searchId = search.id)
                        val result = OpportunityScorer.score(prepared)
                        val scored = prepared.copy(
                            score = result.score,
                            estimatedMargin = result.estimatedMargin,
                            preferenceBoost = result.preferenceBoost,
                            riskFlags = result.riskFlags
                        )
                        if (database.upsertListing(scored)) {
                            newIds += scored.id
                        }
                    }
            }

        recomputeIntelligence()

        val searchesById = database.getSearches().associateBy { it.id }
        val newOpportunities = newIds.mapNotNull(database::getListing)
            .filter { listing ->
                val threshold = searchesById[listing.searchId]?.minMargin ?: 0.0
                listing.score >= 75 &&
                    (listing.estimatedMargin ?: 0.0) >= threshold
            }

        ScanOutcome(scanned, newOpportunities)
    }

    private fun recomputeIntelligence() {
        val universe = database.getAllListings()
        if (universe.isEmpty()) return

        val profile = database.getPreferenceProfile()

        universe.forEach { listing ->
            val market = MarketEstimator.estimate(listing, universe)
            val preferenceBoost = PreferenceEngine.boost(listing.title, profile)

            val prepared = listing.copy(
                marketMedian = market.median,
                marketSampleCount = market.sampleCount,
                marketConfidence = market.confidence,
                preferenceBoost = preferenceBoost
            )

            val result = OpportunityScorer.score(prepared, preferenceBoost)
            database.updateIntelligence(
                prepared.copy(
                    score = result.score,
                    estimatedMargin = result.estimatedMargin,
                    preferenceBoost = result.preferenceBoost,
                    riskFlags = result.riskFlags
                )
            )
        }
    }
}
