package com.bblvn.composebaseproject.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * AI Photo — "Dark cinematic AI studio".
 *
 * The app is DARK-ONLY: there is no light variant and no dynamic color.
 * Every color used anywhere in the app must come from this object; do not
 * hardcode hex values inside screens.
 */
object AppColors {

    // --- Nền & bề mặt ---------------------------------------------------
    val bgRoot = Color(0xFF07070B)
    val bgSurface = Color(0xFF121218)
    val bgElevated = Color(0xFF1C1C26)
    val bgScrim = Color(0xFF000000)
    val bgSkeleton = Color(0xFF1A1A22)

    // --- Viền -----------------------------------------------------------
    val strokeSoft = Color.White.copy(alpha = 0.08f)
    val strokeStrong = Color.White.copy(alpha = 0.16f)
    val strokeActive = Color(0xFF7C5CFF)

    // --- Thương hiệu / accent -------------------------------------------
    val accentViolet = Color(0xFF7C5CFF)
    val accentMagenta = Color(0xFFFF4FD8)
    val accentCyan = Color(0xFF4FE3FF)
    val accentBlue = Color(0xFF3B8BFF)

    // --- Premium ---------------------------------------------------------
    val goldLight = Color(0xFFFFD87A)
    val goldDeep = Color(0xFFFFA33D)

    // --- Text -------------------------------------------------------------
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFA5A5B8)
    val textTertiary = Color(0xFF6E6E82)
    val textOnAccent = Color(0xFF0B0510)

    // --- Trạng thái --------------------------------------------------------
    val stateSuccess = Color(0xFF37E29A)
    val stateDanger = Color(0xFFFF5470)
    val stateWarning = Color(0xFFFFB44D)
}

/**
 * The ONLY two brand gradients allowed in the app (R9), plus the halo used
 * behind logos / hero CTAs and the scrim helpers that must sit under any text
 * drawn on top of an image.
 *
 * Never build a new `Brush.linearGradient` inside a screen — add it here if a
 * genuinely new need appears.
 */
object AppGradients {

    /** linear(135°) accent_violet -> accent_magenta. Primary CTA, progress, active border. */
    val brand: Brush = Brush.linearGradient(
        colors = listOf(AppColors.accentViolet, AppColors.accentMagenta),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    /** linear(135°) gold_light -> gold_deep. Everything premium. */
    val gold: Brush = Brush.linearGradient(
        colors = listOf(AppColors.goldLight, AppColors.goldDeep),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    /**
     * [brand] laid out left-to-right. For thin horizontal fills (progress bars, page
     * indicators) where the 135° diagonal of [brand] would collapse into one flat colour.
     */
    val brandHorizontal: Brush = Brush.horizontalGradient(
        listOf(AppColors.accentViolet, AppColors.accentMagenta)
    )

    /** Soft white highlight band swept across a progress fill, between [startX] and [endX]. */
    fun sheen(startX: Float, endX: Float): Brush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.55f),
            Color.Transparent,
        ),
        startX = startX,
        endX = endX,
    )

    /** Same as [brand] but usable as a border stroke that sweeps around a shape. */
    val brandSweep: Brush = Brush.sweepGradient(
        listOf(
            AppColors.accentViolet,
            AppColors.accentMagenta,
            AppColors.accentCyan,
            AppColors.accentViolet,
        )
    )

    /** radial accent_violet @0.35 -> transparent. Glow behind logo / hero CTA. */
    fun halo(radius: Float = Float.POSITIVE_INFINITY): Brush = Brush.radialGradient(
        colors = listOf(
            AppColors.accentViolet.copy(alpha = 0.35f),
            AppColors.accentViolet.copy(alpha = 0.12f),
            Color.Transparent,
        ),
        radius = radius,
    )

    /**
     * Ambient violet wash used at the top of Home / Tools. Drawn with a Brush,
     * never an image asset (R8). [centerX] is in pixels; pass the box's centre.
     */
    fun topHalo(radiusPx: Float, centerX: Float): Brush = Brush.radialGradient(
        colors = listOf(
            AppColors.accentViolet.copy(alpha = 0.28f),
            AppColors.accentViolet.copy(alpha = 0.08f),
            Color.Transparent,
        ),
        center = Offset(centerX, 0f),
        radius = radiusPx,
    )

    /**
     * Mandatory scrim under any text placed over an image.
     * [startFraction] is where the darkening begins (0f = top of the box).
     */
    fun scrimBottom(
        startFraction: Float = 0.45f,
        endAlpha: Float = 0.85f,
    ): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            startFraction.coerceIn(0f, 0.99f) to Color.Transparent,
            1f to AppColors.bgScrim.copy(alpha = endAlpha),
        )
    )

    /** Scrim that fades an image into the page background (hero bottoms, onboarding). */
    fun scrimToRoot(startFraction: Float = 0.55f): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            startFraction.coerceIn(0f, 0.99f) to Color.Transparent,
            1f to AppColors.bgRoot,
        )
    )

    /**
     * Fade that starts transparent at the top of a text block drawn over artwork and reaches
     * near-solid [AppColors.bgRoot] after [fadeHeightPx]; everything below stays solid. Sized in
     * pixels rather than as a fraction so it tracks the block however tall it grows.
     */
    fun fadeToRoot(fadeHeightPx: Float): Brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, AppColors.bgRoot.copy(alpha = 0.94f)),
        startY = 0f,
        endY = fadeHeightPx,
    )

    /** Left-to-right scrim used by the featured (landscape) tool card. */
    fun scrimStart(endAlpha: Float = 0.85f): Brush = Brush.horizontalGradient(
        listOf(AppColors.bgScrim.copy(alpha = endAlpha), Color.Transparent)
    )

    /** Fade behind a floating bottom CTA so it stays readable over an image. */
    val bottomCtaFade: Brush = Brush.verticalGradient(
        listOf(Color.Transparent, AppColors.bgRoot)
    )
}
