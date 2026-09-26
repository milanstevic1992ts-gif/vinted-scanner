package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.OpportunityScore
import kotlin.math.roundToInt

object OpportunityScorer {
    private val riskTerms = listOf(
        "macchia", "strappo", "rotto", "rotta", "difetto", "difetti",
        "consumato", "consumata", "da riparare", "replica", "non originale"
    )

    fun score(listing: Listing): OpportunityScore {
        val median = listing.marketMedian
        val totalCost = listing.price + listing.shipping
        val margin = median?.minus(totalCost)
        val normalizedTitle = listing.title.lowercase()
        val risks = riskTerms.filter(normalizedTitle::contains)

        var points = 50
        if (median != null && median > 0) {
            val discount = ((median - totalCost) / median).coerceIn(-1.0, 1.0)
            points += (discount * 45).roundToInt()
        }
        listing.sellerRating?.let {
            points += when {
                it >= 4.8 -> 8
                it >= 4.5 -> 4
                it < 4.0 -> -8
                else -> 0
            }
        }
        points -= risks.size * 12

        return OpportunityScore(
            score = points.coerceIn(0, 100),
            estimatedMargin = margin,
            riskFlags = risks
        )
    }
}
