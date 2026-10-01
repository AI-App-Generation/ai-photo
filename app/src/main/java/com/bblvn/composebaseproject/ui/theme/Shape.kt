package com.bblvn.composebaseproject.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The only 6 corner radii allowed in the app. Screens must not write a literal
 * `RoundedCornerShape(n.dp)` — pick a token from here (R9).
 */
object AppShapes {
    /** 24dp — cards, panels, grouped setting blocks. */
    val card = RoundedCornerShape(24.dp)

    /** 18dp — small thumbnails, photo slots, stat cards. */
    val thumb = RoundedCornerShape(18.dp)

    /** Fully rounded pill — chips, badges, icon-button ripple clip. */
    val chip = RoundedCornerShape(50)

    /** 28dp top corners — bottom sheets. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** 18dp — primary / secondary buttons. */
    val button = RoundedCornerShape(18.dp)

    /** 16dp — input fields. */
    val field = RoundedCornerShape(16.dp)

    /** 24dp top corners — the bottom navigation bar. */
    val navBar = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}
