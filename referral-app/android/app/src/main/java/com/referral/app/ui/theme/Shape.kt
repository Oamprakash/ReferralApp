package com.referral.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // text fields
    small = RoundedCornerShape(12.dp),       // chips
    medium = RoundedCornerShape(16.dp),     // buttons, small cards
    large = RoundedCornerShape(22.dp),      // cards
    extraLarge = RoundedCornerShape(28.dp), // dialogs, sheets
)

/** Spacing scale used across screens. */
/**
 * Spacing on an 8dp grid, with 4dp steps for fine alignment.
 *  - [xs] 4: a label and its value, a title and its subtitle
 *  - [sm] 8: items in a row (chips, buttons, icon + text)
 *  - [md] 12: groups inside a card
 *  - [lg] 16: card padding, gap between cards, screen edges
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 16.dp      // horizontal screen padding
    val cardGap = 16.dp     // between cards in a list
}
