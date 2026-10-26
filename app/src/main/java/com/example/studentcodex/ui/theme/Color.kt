package com.example.studentcodex.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Student Codex semantic color tokens.
 *
 * Visual identity: "library green on warm paper".
 *
 * The product is a scholarly study companion, so the palette borrows its
 * materials from that world: warm paper for the canvas (never pure neutral),
 * deep library green as the single accent (focus, structure, go), and a brass
 * tertiary used sparingly for demo/awareness notices. Every neutral is tinted
 * a few percent toward the dominant hue so surfaces feel like one material.
 *
 * The surfaceContainer ramp gives the app exactly one level of elevation
 * above the canvas: cards and chat bubbles sit on `Low` (a sheet of paper
 * stacked on the desk), while chrome (bottom navigation, menus) sits on
 * `Container`. Higher steps exist only because Material 3 components read
 * them by default - without hand-tuned values here they would fall back to
 * the library's cool gray tokens and clash with the warm paper canvas.
 *
 * Deliberately NOT used: purple/indigo accents, cyan/teal accents, pure gray
 * neutrals, neon-on-slate dark mode. Light and dark schemes are hand-tuned
 * pairs; dynamic color (Android 12+) stays opt-in so the brand reads
 * consistently in demos and screenshots.
 */

// Light scheme - warm paper
val PrimaryLight = Color(0xFF2F5B41)          // deep library green: actions, due emphasis
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFC4EFD2) // pale sage fills
val OnPrimaryContainerLight = Color(0xFF0B2114)

val SecondaryLight = Color(0xFF48554E)        // ink green: supporting content
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD3DED5)
val OnSecondaryContainerLight = Color(0xFF12231A)

val TertiaryLight = Color(0xFF715C24)         // brass: demo/awareness notices only
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFF5E3A6)
val OnTertiaryContainerLight = Color(0xFF231A00)

val ErrorLight = Color(0xFFB3362E)            // muted warm red, harmonized with paper
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9D9D4)
val OnErrorContainerLight = Color(0xFF410903)

val BackgroundLight = Color(0xFFFAF9F4)       // warm paper canvas
val OnBackgroundLight = Color(0xFF1A1F1B)     // green-tinted ink
val SurfaceLight = Color(0xFFFAF9F4)
val OnSurfaceLight = Color(0xFF1A1F1B)
val SurfaceVariantLight = Color(0xFFE1E7DE)   // paper darkened toward green
val OnSurfaceVariantLight = Color(0xFF404B44) // metadata ink (calmer than body ink)
val OutlineLight = Color(0xFF707E75)
val OutlineVariantLight = Color(0xFFC4CCC2)   // row dividers

// Light surfaceContainer ramp - stacked paper steps above the canvas.
val SurfaceContainerLowestLight = Color(0xFFFDFCF8)
val SurfaceContainerLowLight = Color(0xFFF1EFE5)      // post cards, chat bubbles
val SurfaceContainerLight = Color(0xFFEAE8DC)         // bottom navigation, menus
val SurfaceContainerHighLight = Color(0xFFE2E0D3)
val SurfaceContainerHighestLight = Color(0xFFDCDACB)  // filter chips

// Dark scheme - green-tinted charcoal
val PrimaryDark = Color(0xFF9BD5AE)           // sage green, high contrast on charcoal
val OnPrimaryDark = Color(0xFF0A1F12)
val PrimaryContainerDark = Color(0xFF2C5238)
val OnPrimaryContainerDark = Color(0xFFB8EFC8)

val SecondaryDark = Color(0xFFB6C4B9)
val OnSecondaryDark = Color(0xFF222E26)
val SecondaryContainerDark = Color(0xFF3A4640)
val OnSecondaryContainerDark = Color(0xFFC9D7CD)

val TertiaryDark = Color(0xFFD9C27A)          // lamp brass in the dark
val OnTertiaryDark = Color(0xFF38300A)
val TertiaryContainerDark = Color(0xFF524619)
val OnTertiaryContainerDark = Color(0xFFF1E1A9)

val ErrorDark = Color(0xFFEFB8B2)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFFFDAD5)

val BackgroundDark = Color(0xFF121713)        // charcoal with a green cast
val OnBackgroundDark = Color(0xFFE1E6DF)
val SurfaceDark = Color(0xFF121713)
val OnSurfaceDark = Color(0xFFE1E6DF)
val SurfaceVariantDark = Color(0xFF414C44)
val OnSurfaceVariantDark = Color(0xFFC1C9C0)
val OutlineDark = Color(0xFF8B998F)
val OutlineVariantDark = Color(0xFF414C44)    // row dividers in dark mode

// Dark surfaceContainer ramp - charcoal sheets above the desk.
val SurfaceContainerLowestDark = Color(0xFF0D110E)
val SurfaceContainerLowDark = Color(0xFF1A211C)       // post cards, chat bubbles
val SurfaceContainerDark = Color(0xFF212A24)          // bottom navigation, menus
val SurfaceContainerHighDark = Color(0xFF2A342D)
val SurfaceContainerHighestDark = Color(0xFF333E36)   // filter chips
