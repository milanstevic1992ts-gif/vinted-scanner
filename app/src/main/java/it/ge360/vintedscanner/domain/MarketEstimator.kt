package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.MarketEstimate
import kotlin.math.abs
import kotlin.math.roundToInt

object MarketEstimator {
    private const val MIN_SIMILARITY = 0.38

    fun estimate(target: Listing, universe: List<Listing>): MarketEstimate {
        val targetSignature = ProductSignatureExtractor.extract(target.title, target.condition)

        val candidates = universe.asSequence()
            .filter { it.id != target.id }
            .map { candidate ->
                val signature = ProductSignatureExtractor.extract(candidate.title, candidate.condition)
                val structured = ProductSignatureExtractor.similarity(targetSignature, signature)
                val textSimilarity = TextSignals.similarity(target.title, candidate.title)
                val combined = if (structured <= 0.0) {
                    0.0
                } else {
                    structured * 0.85 + textSimilarity * 0.15
                }
                ComparableCandidate(candidate, combined)
            }
            .filter { it.similarity >= MIN_SIMILARITY }
            .sortedByDescending { it.similarity }
            .take(40)
            .toList()

        if (candidates.size < 2) {
            return MarketEstimate(
                median = target.marketMedian,
                sampleCount = if (target.marketMedian != null) 1 else 0,
                confidence = if (target.marketMedian != null) 25 else 0,
                similarity = candidates.firstOrNull()?.let { (it.similarity * 100).roundToInt() } ?: 0,
                outliersRemoved = 0,
                comparableLabel = targetSignature.label
            )
        }

        val filtered = removePriceOutliers(candidates)
        val usable = if (filtered.size >= 2) filtered else candidates

        val prices = usable.map { it.listing.price }.sorted()
        val median = median(prices)
        val averageSimilarity = usable.map { it.similarity }.average()

        val sampleFactor = (usable.size.coerceAtMost(12) / 12.0) * 45.0
        val similarityFactor = averageSimilarity.coerceIn(0.0, 1.0) * 45.0

        val deviations = prices.map { abs(it - median) }.sorted()
        val mad = median(deviations)
        val relativeSpread = if (median > 0) (mad / median).coerceIn(0.0, 1.0) else 1.0
        val consistencyFactor = (1.0 - relativeSpread) * 10.0

        return MarketEstimate(
            median = median,
            sampleCount = usable.size,
            confidence = (sampleFactor + similarityFactor + consistencyFactor)
                .roundToInt()
                .coerceIn(0, 100),
            similarity = (averageSimilarity * 100).roundToInt().coerceIn(0, 100),
            outliersRemoved = candidates.size - usable.size,
            comparableLabel = targetSignature.label
        )
    }

    private fun removePriceOutliers(
        candidates: List<ComparableCandidate>
    ): List<ComparableCandidate> {
        if (candidates.size < 4) return candidates

        val prices = candidates.map { it.listing.price }.sorted()
        val center = median(prices)
        val deviations = prices.map { abs(it - center) }.sorted()
        val mad = median(deviations)

        val kept = if (mad > 0.0) {
            val robustSigma = mad * 1.4826
            val threshold = robustSigma * 3.0
            candidates.filter { abs(it.listing.price - center) <= threshold }
        } else {
            val low = center * 0.55
            val high = center * 1.80
            candidates.filter { it.listing.price in low..high }
        }

        return if (kept.size >= 2) kept else candidates
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private data class ComparableCandidate(
        val listing: Listing,
        val similarity: Double
    )
}
