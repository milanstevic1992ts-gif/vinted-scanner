package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.PreferenceProfile
import kotlin.math.roundToInt

object PreferenceEngine {
    fun boost(title: String, profile: PreferenceProfile): Int {
        val tokens = TextSignals.tokens(title)
        if (tokens.isEmpty()) return 0

        val raw = profile.weightFor(tokens)
        val normalized = (raw / tokens.size.toDouble()).roundToInt()
        return normalized.coerceIn(-12, 12)
    }

    fun updatedWeights(
        existing: Map<String, Int>,
        title: String,
        feedback: Int
    ): Map<String, Int> {
        if (feedback == 0) return existing

        val delta = if (feedback > 0) 2 else -2
        return existing.toMutableMap().apply {
            TextSignals.tokens(title).forEach { token ->
                this[token] = ((this[token] ?: 0) + delta).coerceIn(-20, 20)
            }
        }
    }
}
