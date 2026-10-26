package com.example.studentcodex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.studentcodex.R

/**
 * Student Codex type scale.
 *
 * Typeface: DM Sans (SIL Open Font License), bundled as a single variable
 * font and instanced per weight. It is a geometric sans with restrained,
 * precise character - the skill's recommended register for a study tool that
 * should feel like printed matter, not a template.
 *
 * Five jobs: screen titles (headlineSmall), section/card titles
 * (titleMedium), row labels (titleSmall), reading text (body*), and UI
 * chrome/metadata (label*). Hierarchy comes from size + weight - never from
 * ALL CAPS or letter-spaced labels.
 *
 * Nothing renders below 12sp. 14sp is the working size for both reading and
 * row labels because the app is a dense, scan-first study tool.
 */
@OptIn(ExperimentalTextApi::class)
val CodexFontFamily: FontFamily = FontFamily(
    Font(
        resId = R.font.dmsans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.dmsans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.dmsans,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.dmsans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

val CodexTypography: Typography = Typography(
    // Screen titles - one per screen, sentence case, semibold.
    headlineSmall = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // Reserved: large marketing/display moments do not occur in this app.
    headlineMedium = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    // Profile name - the only titleLarge use.
    titleLarge = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    // Section headers and card titles.
    titleMedium = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    // Row primary labels - the workhorse label of dense lists.
    titleSmall = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Reading text.
    bodyLarge = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Captions and secondary metadata.
    bodySmall = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Buttons and interactive chips.
    labelLarge = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    // Metadata labels (due times, counts, sources).
    labelMedium = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    // Smallest chrome text - kept at the 12sp readability floor.
    labelSmall = TextStyle(
        fontFamily = CodexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
)
