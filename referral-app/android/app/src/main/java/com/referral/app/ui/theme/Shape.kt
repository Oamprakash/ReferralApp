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
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val screen = 20.dp      // horizontal screen padding
}
