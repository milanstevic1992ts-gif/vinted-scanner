package it.ge360.vintedscanner.model

data class SavedSearch(
    val id: Long = 0,
    val query: String,
    val maxPrice: Double?,
    val size: String?,
    val minMargin: Double = 20.0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class Listing(
    val id: String,
    val searchId: Long,
    val title: String,
    val price: Double,
    val shipping: Double = 0.0,
    val marketMedian: Double? = null,
    val url: String,
    val imageUrl: String? = null,
    val condition: String? = null,
    val sellerRating: Double? = null,
    val publishedAt: Long? = null,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val score: Int = 0,
    val estimatedMargin: Double? = null,
    val riskFlags: List<String> = emptyList()
)

data class OpportunityScore(
    val score: Int,
    val estimatedMargin: Double?,
    val riskFlags: List<String>
)
