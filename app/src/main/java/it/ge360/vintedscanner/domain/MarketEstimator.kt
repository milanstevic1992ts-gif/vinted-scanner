package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.MarketEstimate
import kotlin.math.roundToInt

object MarketEstimator {
    private const val MIN_SIMILARITY = 0.28

    fun estimate(target: Listing, universe: List<Listing>): MarketEstimate {
        val matches = universe.asSequence()
            .filter { it.id != target.id }
            .map { it to TextSignals.similarity(target.title, it.title) }
            .filter { (_, similarity) -> similarity >= MIN_SIMILARITY }
            .sortedByDescending { (_, similarity) -> similarity }
            .take(30)
            .toList()

        if (matches.size < 2) {
            return MarketEstimate(
                median = target.marketMedian,
                sampleCount = if (target.marketMedian != null) 1 else 0,
                confidence = if (target.marketMedian != null) 25 else 0
            )
        }

        val prices = matches.map { it.first.price }.sorted()
        val median = when {
            prices.isEmpty() -> null
            prices.size % 2 == 1 -> prices[prices.size / 2]
            else -> (prices[prices.size / 2 - 1] + prices[prices.size / 2]) / 2.0
        }

        val averageSimilarity = matches.map { it.second }.average()
        val sampleFactor = (matches.size.coerceAtMost(12) / 12.0) * 55.0
        val similarityFactor = averageSimilarity.coerceIn(0.0, 1.0) * 45.0

        return MarketEstimate(
            median = median,
            sampleCount = matches.size,
            confidence = (sampleFactor + similarityFactor).roundToInt().coerceIn(0, 100)
        )
    }
}
