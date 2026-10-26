package com.example.studentcodex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Student Codex shape system.
 *
 * One primary radius (12dp - rounded but restrained, fits a precise study
 * tool on mobile) with exactly two documented exceptions:
 *
 *  - 8dp  micro: tiny inline badges and compact chips where 12dp would
 *        visually collapse.
 *  - 16dp sheet: bottom sheets, dialogs and other overlay surfaces, one step
 *        larger to read as a distinct elevation class.
 *
 * Pills are reserved for tags/selection chips only (Material components
 * apply those internally). No other radii exist in the app.
 */
val CodexShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // micro badges
    small = RoundedCornerShape(12.dp),      // primary: chips, small controls
    medium = RoundedCornerShape(12.dp),     // primary: cards, bubbles, inputs
    large = RoundedCornerShape(16.dp),      // sheets and overlays
    extraLarge = RoundedCornerShape(16.dp), // largest overlay surfaces
)
