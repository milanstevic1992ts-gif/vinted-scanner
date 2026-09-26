package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.Listing
import kotlin.math.roundToInt

data class DealCandidate(
    val listing: Listing,
    val dealIndex: Int,
    val freshnessScore: Int,
    val marginRatio: Int,
    val reasons: List<String>
)

object DealRanker {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun rank(
        listings: List<Listing>,
        now: Long = System.currentTimeMillis()
    ): List<DealCandidate> =
        listings
            .filter { it.feedback >= 0 }
            .map { evaluate(it, now) }
            .sortedWith(
                compareByDescending<DealCandidate> { it.dealIndex }
                    .thenByDescending { it.listing.estimatedMargin ?: Double.NEGATIVE_INFINITY }
                    .thenByDescending { it.listing.marketConfidence }
                    .thenByDescending { it.listing.firstSeenAt }
            )

    fun evaluate(listing: Listing, now: Long = System.currentTimeMillis()): DealCandidate {
        val margin = listing.estimatedMargin
        val reference = listing.marketMedian
        val marginRatioRaw = if (margin != null && reference != null && reference > 0) {
            (margin / reference * 100.0)
        } else 0.0
        val marginRatio = marginRatioRaw.roundToInt().coerceIn(-100, 100)

        val freshness = freshnessScore(listing.firstSeenAt, now)
        val positiveMargin = marginRatio.coerceIn(0, 60) / 60.0 * 25.0
        val scorePart = listing.score.coerceIn(0, 100) / 100.0 * 30.0
        val confidencePart = listing.marketConfidence.coerceIn(0, 100) / 100.0 * 20.0
        val similarityPart = listing.marketSimilarity.coerceIn(0, 100) / 100.0 * 10.0
        val freshnessPart = freshness / 100.0 * 15.0

        val riskPenalty = (listing.riskFlags.size * 7).coerceAtMost(21)
        val weakDataPenalty = when {
            listing.marketMedian == null -> 12
            listing.marketSampleCount < 2 -> 8
            listing.marketConfidence < 35 -> 5
            else -> 0
        }

        val dealIndex = (
            scorePart +
                positiveMargin +
                confidencePart +
                similarityPart +
                freshnessPart -
                riskPenalty -
                weakDataPenalty
            ).roundToInt().coerceIn(0, 100)

        return DealCandidate(
            listing = listing,
            dealIndex = dealIndex,
            freshnessScore = freshness,
            marginRatio = marginRatio,
            reasons = reasonsFor(listing, marginRatio, freshness)
        )
    }

    private fun freshnessScore(firstSeenAt: Long, now: Long): Int {
        val age = (now - firstSeenAt).coerceAtLeast(0L)
        return when {
            age <= 30L * 60L * 1000L -> 100
            age <= 2L * 60L * 60L * 1000L -> 90
            age <= 6L * 60L * 60L * 1000L -> 78
            age <= 12L * 60L * 60L * 1000L -> 65
            age <= DAY_MS -> 52
            age <= 3L * DAY_MS -> 35
            age <= 7L * DAY_MS -> 18
            else -> 5
        }
    }

    private fun reasonsFor(
        listing: Listing,
        marginRatio: Int,
        freshness: Int
    ): List<String> {
        val reasons = mutableListOf<String>()

        listing.estimatedMargin?.let { margin ->
            when {
                margin >= 50 -> reasons += "Margine netto molto alto"
                margin >= 30 -> reasons += "Margine netto interessante"
                margin >= 15 -> reasons += "Margine netto positivo"
            }
        }

        when {
            marginRatio >= 40 -> reasons += "Prezzo molto sotto il valore osservato"
            marginRatio >= 25 -> reasons += "Prezzo sotto i comparabili"
        }

        when {
            listing.marketConfidence >= 80 && listing.marketSimilarity >= 75 ->
                reasons += "Comparabili molto solidi"
            listing.marketConfidence >= 65 ->
                reasons += "Stima con buona confidenza"
        }

        when {
            freshness >= 90 -> reasons += "Annuncio appena rilevato"
            freshness >= 65 -> reasons += "Annuncio recente"
        }

        if (listing.marketOutliersRemoved > 0) {
            reasons += "Outlier esclusi dalla stima"
        }

        if (listing.riskFlags.isNotEmpty()) {
            reasons += "Presenti segnali di rischio"
        }

        if (reasons.isEmpty()) {
            reasons += when {
                listing.marketMedian == null -> "Dati comparabili ancora insufficienti"
                listing.marketConfidence < 40 -> "Stima da consolidare"
                else -> "Opportunità da verificare"
            }
        }

        return reasons.distinct().take(3)
    }
}
