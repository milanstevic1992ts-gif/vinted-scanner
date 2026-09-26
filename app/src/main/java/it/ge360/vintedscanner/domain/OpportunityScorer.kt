package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.OpportunityScore
import kotlin.math.roundToInt

object OpportunityScorer {
    fun score(listing: Listing, preferenceBoost: Int = listing.preferenceBoost): OpportunityScore {
        val median = listing.marketMedian
        val totalCost = listing.price + listing.shipping
        val margin = median?.minus(totalCost)
        val risks = TextSignals.riskFlags(listing.title, listing.condition)

        var points = 48

        if (median != null && median > 0) {
            val discount = ((median - totalCost) / median).coerceIn(-1.0, 1.0)
            val confidenceWeight = 0.45 + (listing.marketConfidence.coerceIn(0, 100) / 100.0) * 0.55
            points += (discount * 44 * confidenceWeight).roundToInt()
        }

        listing.sellerRating?.let {
            points += when {
                it >= 4.8 -> 8
                it >= 4.5 -> 4
                it < 4.0 -> -8
                else -> 0
            }
        }

        points += preferenceBoost.coerceIn(-12, 12)
        points -= risks.size * 10

        if (listing.marketSampleCount >= 5 && listing.marketConfidence >= 50) {
            points += 3
        }

        return OpportunityScore(
            score = points.coerceIn(0, 100),
            estimatedMargin = margin,
            preferenceBoost = preferenceBoost.coerceIn(-12, 12),
            riskFlags = risks
        )
    }
}
