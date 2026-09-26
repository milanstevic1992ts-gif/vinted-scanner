package it.ge360.vintedscanner.model

data class SavedSearch(
    val id: Long = 0,
    val query: String,
    val maxPrice: Double?,
    val size: String?,
    val brand: String? = null,
    val condition: String? = null,
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
    val marketSampleCount: Int = 0,
    val marketConfidence: Int = 0,
    val url: String,
    val imageUrl: String? = null,
    val condition: String? = null,
    val sellerRating: Double? = null,
    val publishedAt: Long? = null,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val score: Int = 0,
    val estimatedMargin: Double? = null,
    val preferenceBoost: Int = 0,
    val riskFlags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val feedback: Int = 0
)

data class OpportunityScore(
    val score: Int,
    val estimatedMargin: Double?,
    val preferenceBoost: Int,
    val riskFlags: List<String>
)

data class PricePoint(
    val listingId: String,
    val price: Double,
    val seenAt: Long
)

data class SharedListingDraft(
    val rawText: String,
    val url: String?,
    val titleGuess: String,
    val priceGuess: Double?
)

data class MarketEstimate(
    val median: Double?,
    val sampleCount: Int,
    val confidence: Int
)

data class PreferenceProfile(
    val tokenWeights: Map<String, Int> = emptyMap()
) {
    fun weightFor(tokens: Collection<String>): Int =
        tokens.sumOf { tokenWeights[it] ?: 0 }
}

enum class SourceKind {
    MANUAL_SHARE,
    AUTHORIZED_REMOTE
}

enum class SourceStatus {
    READY,
    IDLE,
    SCANNING,
    NOT_CONFIGURED,
    ERROR,
    DISABLED
}

data class SourceDescriptor(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val supportsAutomaticScan: Boolean,
    val requiresConfiguration: Boolean
)

data class SourceDiagnostic(
    val descriptor: SourceDescriptor,
    val enabled: Boolean,
    val status: SourceStatus,
    val lastEventAt: Long? = null,
    val lastScanAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastReceivedCount: Int = 0,
    val totalReceived: Long = 0,
    val lastError: String? = null
)
