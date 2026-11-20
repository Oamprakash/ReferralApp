package com.referral.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * How well a seeker fits a job, in three tiers used by the score ring, the match badge and the
 * skills / experience bars. No red anywhere: a low match is a stretch role, not an error.
 *
 * | Tier     | Ring (0–100) | Bar (0–50)   | Colour |
 * |----------|--------------|--------------|--------|
 * | Strong   | 65–100       | 35–50 (≥70%) | emerald-teal (in the brand family) |
 * | Moderate | 40–64        | 20–34 (≥40%) | amber  |
 * | Low      | 0–39         | 0–19         | slate  |
 */
enum class MatchTier {
    STRONG, MODERATE, LOW;

    companion object {
        fun ofScore(score: Int): MatchTier = when {
            score >= 65 -> STRONG
            score >= 40 -> MODERATE
            else -> LOW
        }

        /** For a bar out of [max] points, by percentage. */
        fun ofPoints(points: Int, max: Int = 50): MatchTier {
            val pct = if (max <= 0) 0 else points * 100 / max
            return when {
                pct >= 70 -> STRONG
                pct >= 40 -> MODERATE
                else -> LOW
            }
        }
    }
}

/** [text] for numbers and labels, [bar] for the filled part, [track] for the empty part and pill backgrounds. */
@Immutable
data class TierColors(val text: Color, val bar: Color, val track: Color)

@Composable
fun MatchTier.colors(): TierColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (!dark) when (this) {
        // Emerald-teal rather than grass green, so it sits with the brand teal (Hide, nav, links)
        MatchTier.STRONG -> TierColors(Color(0xFF0F766E), Color(0xFF0F766E), Color(0xFFCCF5EF))
        MatchTier.MODERATE -> TierColors(Color(0xFFB45309), Color(0xFFD97706), Color(0xFFFEF3C7))
        MatchTier.LOW -> TierColors(Color(0xFF475569), Color(0xFF64748B), Color(0xFFE2E8F0))
    } else when (this) {
        // Same hues, lightened for the dark background
        MatchTier.STRONG -> TierColors(Color(0xFF2DD4BF), Color(0xFF2DD4BF), Color(0xFF134E4A))
        MatchTier.MODERATE -> TierColors(Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFF4A3410))
        MatchTier.LOW -> TierColors(Color(0xFFB4C0D0), Color(0xFF94A3B8), Color(0xFF243146))
    }
}
