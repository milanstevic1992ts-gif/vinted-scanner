package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.domain.CalibrationAnalyzer
import it.ge360.vintedscanner.domain.DealRanker
import it.ge360.vintedscanner.domain.ListingIdentity
import it.ge360.vintedscanner.domain.MarketEstimator
import it.ge360.vintedscanner.domain.OpportunityScorer
import it.ge360.vintedscanner.domain.PreferenceEngine
import it.ge360.vintedscanner.model.CalibrationReview
import it.ge360.vintedscanner.model.CalibrationSession
import it.ge360.vintedscanner.model.CalibrationSummary
import it.ge360.vintedscanner.model.EstimateVerdict
import it.ge360.vintedscanner.model.FeedbackEvent
import it.ge360.vintedscanner.model.FeedbackReason
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.RankingVerdict
import it.ge360.vintedscanner.model.PricePoint
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SourceDiagnostic
import it.ge360.vintedscanner.sources.ListingSource
import it.ge360.vintedscanner.sources.SourceCatalog
import it.ge360.vintedscanner.sources.VintedCatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanOutcome(
    val scanned: Int,
    val newOpportunities: List<Listing>
)

class ScannerRepository(
    private val database: AppDatabase,
    private val sources: List<ListingSource> = listOf(VintedCatalogSource()),
    private val notificationAccess: () -> Boolean = { false }
) {
    init {
        database.ensureSources(SourceCatalog.defaults)
        syncConfigurationState()
    }
    suspend fun searches(): List<SavedSearch> = withContext(Dispatchers.IO) {
        database.getSearches()
    }

    suspend fun sourceDiagnostics(): List<SourceDiagnostic> = withContext(Dispatchers.IO) {
        database.getSourceDiagnostics()
    }

    suspend fun refreshSourceDiagnostics(): List<SourceDiagnostic> = withContext(Dispatchers.IO) {
        syncConfigurationState()
        database.getSourceDiagnostics()
    }

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): List<SourceDiagnostic> =
        withContext(Dispatchers.IO) {
            database.setSourceEnabled(sourceId, enabled)
            syncConfigurationState()
            database.getSourceDiagnostics()
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
            .filter { it.feedbackReason != FeedbackReason.PURCHASED }
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
            profile = database.getPreferenceProfile(),
            feedbackEvents = database.getFeedbackEvents(1000)
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

    suspend fun setFeedback(
        listingId: String,
        reason: FeedbackReason
    ) = withContext(Dispatchers.IO) {
        database.setFeedbackReason(listingId, reason)
        recomputeIntelligence()
    }

    suspend fun feedbackEvents(): List<FeedbackEvent> = withContext(Dispatchers.IO) {
        database.getFeedbackEvents()
    }

    suspend fun priceHistory(listingId: String): List<PricePoint> = withContext(Dispatchers.IO) {
        database.getPriceHistory(listingId)
    }

    suspend fun calibrationSession(): CalibrationSession? = withContext(Dispatchers.IO) {
        database.getActiveCalibrationSession() ?: database.getLatestCalibrationSession()
    }

    suspend fun calibrationSummary(): CalibrationSummary = withContext(Dispatchers.IO) {
        val session = database.getActiveCalibrationSession() ?: database.getLatestCalibrationSession()
        if (session == null) {
            CalibrationSummary(sessionId = null)
        } else {
            CalibrationAnalyzer.summarize(
                sessionId = session.id,
                reviews = database.getCalibrationReviews(session.id),
                targetCount = session.targetCount
            )
        }
    }

    suspend fun startCalibration(targetCount: Int = 25): CalibrationSession =
        withContext(Dispatchers.IO) {
            database.startCalibrationSession(targetCount)
        }

    suspend fun completeCalibration() = withContext(Dispatchers.IO) {
        database.getActiveCalibrationSession()?.let {
            database.completeCalibrationSession(it.id)
        }
    }

    suspend fun saveCalibrationReview(
        listingId: String,
        signatureCorrect: Boolean,
        estimateVerdict: EstimateVerdict,
        rankingVerdict: RankingVerdict,
        expectedValue: Double?,
        notes: String?
    ) = withContext(Dispatchers.IO) {
        val session = database.getActiveCalibrationSession() ?: return@withContext
        val listing = database.getListing(listingId) ?: return@withContext
        val profile = database.getPreferenceProfile()
        val deal = DealRanker.evaluate(listing = listing, profile = profile)

        database.saveCalibrationReview(
            CalibrationReview(
                id = 0,
                sessionId = session.id,
                listingId = listing.id,
                title = listing.title,
                createdAt = System.currentTimeMillis(),
                signatureCorrect = signatureCorrect,
                estimateVerdict = estimateVerdict,
                rankingVerdict = rankingVerdict,
                expectedValue = expectedValue,
                observedMedian = listing.marketMedian,
                marketConfidence = listing.marketConfidence,
                marketSimilarity = listing.marketSimilarity,
                dealIndex = deal.dealIndex,
                score = listing.score,
                estimatedMargin = listing.estimatedMargin,
                notes = notes?.trim()?.takeIf(String::isNotBlank)
            )
        )
    }

    suspend fun calibrationReportJson(): String? = withContext(Dispatchers.IO) {
        val session = database.getActiveCalibrationSession() ?: database.getLatestCalibrationSession()
            ?: return@withContext null
        val reviews = database.getCalibrationReviews(session.id)
        val summary = CalibrationAnalyzer.summarize(
            sessionId = session.id,
            reviews = reviews,
            targetCount = session.targetCount
        )
        CalibrationReportExporter.toJson(
            session = session.copy(reviewedCount = reviews.size),
            summary = summary,
            reviews = reviews
        )
    }

    suspend fun importSharedListing(
        title: String,
        price: Double,
        extraCosts: Double,
        marketMedian: Double?,
        url: String,
        condition: String?
    ): Pair<Listing, Boolean> = withContext(Dispatchers.IO) {
        val result = saveImportedListing(
            title = title,
            price = price,
            extraCosts = extraCosts,
            marketMedian = marketMedian,
            url = url,
            condition = condition,
            searchId = 0
        )
        database.recordManualSourceImport(SourceCatalog.ANDROID_SHARE_ID, 1)
        result
    }

    suspend fun ingestVintedNotification(
        rawText: String,
        postedAt: Long
    ): Listing? = withContext(Dispatchers.IO) {
        val diagnostic = database.getSourceDiagnostics()
            .firstOrNull { it.descriptor.id == SourceCatalog.VINTED_NOTIFICATIONS_ID }

        if (diagnostic?.enabled == false) {
            return@withContext null
        }

        if (!notificationAccess()) {
            database.markSourceNotConfigured(
                SourceCatalog.VINTED_NOTIFICATIONS_ID,
                "Concedi accesso notifiche Android a Vinted Scanner"
            )
            return@withContext null
        }

        val draft = SharedListingParser.parse(rawText)
        val url = draft.url
        val price = draft.priceGuess

        if (url.isNullOrBlank() || price == null) {
            database.markSourceSuccess(
                SourceCatalog.VINTED_NOTIFICATIONS_ID,
                1,
                "Notifica Vinted ricevuta, ma senza link/prezzo sufficienti per creare un annuncio"
            )
            return@withContext null
        }

        val searchId = matchSearchId(draft.titleGuess, rawText)
        val (listing, isNew) = saveImportedListing(
            title = draft.titleGuess,
            price = price,
            extraCosts = 0.0,
            marketMedian = null,
            url = url,
            condition = rawText.take(500),
            searchId = searchId,
            seenAt = postedAt
        )

        database.markSourceSuccess(
            SourceCatalog.VINTED_NOTIFICATIONS_ID,
            1,
            if (isNew) "Annuncio importato automaticamente da una notifica Vinted"
            else "Notifica Vinted ricevuta: annuncio già presente, dati aggiornati"
        )

        listing
    }

    private fun saveImportedListing(
        title: String,
        price: Double,
        extraCosts: Double,
        marketMedian: Double?,
        url: String,
        condition: String?,
        searchId: Long,
        seenAt: Long = System.currentTimeMillis()
    ): Pair<Listing, Boolean> {
        val raw = Listing(
            id = ListingIdentity.canonicalId(url),
            searchId = searchId,
            title = title.trim().ifBlank { "Annuncio Vinted" },
            price = price,
            shipping = extraCosts.coerceAtLeast(0.0),
            marketMedian = marketMedian,
            marketSampleCount = if (marketMedian != null) 1 else 0,
            marketConfidence = if (marketMedian != null) 25 else 0,
            url = url.trim(),
            condition = condition?.trim()?.takeIf(String::isNotBlank),
            publishedAt = seenAt,
            firstSeenAt = seenAt,
            lastSeenAt = seenAt
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
        return (database.getListing(initial.id) ?: initial) to isNew
    }

    private fun matchSearchId(title: String, rawText: String): Long {
        val haystack = "$title $rawText".lowercase()
        return database.getSearches()
            .asSequence()
            .filter { it.active }
            .map { search ->
                var score = 0
                if (haystack.contains(search.query.lowercase())) score += 4
                search.brand?.takeIf(String::isNotBlank)?.let {
                    if (haystack.contains(it.lowercase())) score += 3
                }
                search.size?.takeIf(String::isNotBlank)?.let {
                    if (haystack.contains(it.lowercase())) score += 1
                }
                search to score
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
            ?.id
            ?: 0L
    }

    suspend fun scanActive(): ScanOutcome = withContext(Dispatchers.IO) {
        var scanned = 0
        val newIds = linkedSetOf<String>()

        val diagnostics = database.getSourceDiagnostics().associateBy { it.descriptor.id }

        sources.forEach { source ->
            val sourceId = source.descriptor.id
            val diagnostic = diagnostics[sourceId]
            if (diagnostic?.enabled == false) return@forEach

            if (!source.isConfigured()) {
                database.markSourceNotConfigured(sourceId)
                return@forEach
            }

            val lastScanAt = diagnostic?.lastScanAt
            if (
                source.minimumScanIntervalMs > 0 &&
                lastScanAt != null &&
                System.currentTimeMillis() - lastScanAt < source.minimumScanIntervalMs
            ) {
                return@forEach
            }

            database.markSourceScanning(sourceId)
            var receivedBySource = 0

            try {
                database.getSearches()
                    .filter { it.active }
                    .forEach { search ->
                        source.scan(search)
                            .asSequence()
                            .filter { search.maxPrice == null || it.price <= search.maxPrice }
                            .filter {
                                val brand = search.brand
                                brand.isNullOrBlank() ||
                                    isNearDuplicateTerm(brand, search.query) ||
                                    it.title.contains(brand, ignoreCase = true) ||
                                    it.condition?.contains(brand, ignoreCase = true) == true
                            }
                            .filter {
                                search.condition.isNullOrBlank() ||
                                    it.condition.isNullOrBlank() ||
                                    it.condition.contains(search.condition, ignoreCase = true)
                            }
                            .filter {
                                search.size.isNullOrBlank() ||
                                    it.title.contains(search.size, ignoreCase = true) ||
                                    it.condition?.contains(search.size, ignoreCase = true) == true
                            }
                            .forEach { raw ->
                                scanned++
                                receivedBySource++
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
                database.markSourceSuccess(sourceId, receivedBySource)
            } catch (error: Throwable) {
                database.markSourceError(
                    sourceId,
                    error.message ?: error::class.java.simpleName
                )
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

    private fun isNearDuplicateTerm(a: String, b: String): Boolean {
        val left = a.lowercase().filter(Char::isLetterOrDigit)
        val right = b.lowercase().filter(Char::isLetterOrDigit)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true

        val maxDistance = when {
            minOf(left.length, right.length) <= 4 -> 1
            else -> 2
        }
        if (kotlin.math.abs(left.length - right.length) > maxDistance) return false

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[right.length] <= maxDistance
    }

    private fun syncConfigurationState() {
        val byId = sources.associateBy { it.descriptor.id }
        val diagnostics = database.getSourceDiagnostics().associateBy { it.descriptor.id }

        SourceCatalog.defaults.forEach { descriptor ->
            val current = diagnostics[descriptor.id]
            if (current?.enabled == false) return@forEach

            val source = byId[descriptor.id]
            when (descriptor.id) {
                SourceCatalog.ANDROID_SHARE_ID -> {
                    database.markSourceReady(
                        descriptor.id,
                        "Pronta per Condividi → Vinted Scanner"
                    )
                }
                SourceCatalog.VINTED_NOTIFICATIONS_ID -> {
                    if (notificationAccess()) {
                        database.markSourceReady(
                            descriptor.id,
                            "Accesso notifiche concesso · ascolto del pacchetto fr.vinted"
                        )
                    } else {
                        database.markSourceNotConfigured(
                            descriptor.id,
                            "Concedi accesso notifiche Android a Vinted Scanner"
                        )
                    }
                }
                SourceCatalog.VINTED_CATALOG_ID -> {
                    if (source == null || !source.isConfigured()) {
                        database.markSourceNotConfigured(
                            descriptor.id,
                            "Sorgente catalogo non disponibile"
                        )
                    } else {
                        database.markSourceReady(
                            descriptor.id,
                            "Endpoint catalogo sperimentale /api/v2/catalog/items · nessun bypass anti-bot"
                        )
                    }
                }
                else -> Unit
            }
        }
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
                marketSimilarity = market.similarity,
                marketOutliersRemoved = market.outliersRemoved,
                comparableLabel = market.comparableLabel,
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
