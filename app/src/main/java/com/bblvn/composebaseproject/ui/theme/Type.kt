package com.bblvn.composebaseproject.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Exactly 6 typography levels — no decorative font, no extra sizes.
 * Screens must pick one of these; never build a TextStyle inline.
 */
object AppType {

    /** 28sp / 34 / Bold — Onboarding title, Paywall headline, big tab title. */
    val display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )

    /** 22sp / 28 / Bold — screen name, template name on Detail. */
    val titleL = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    /** 18sp / 24 / SemiBold — section header, primary button label. */
    val titleM = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    /** 15sp / 22 / Medium — descriptions, main body copy. */
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** 13sp / 18 / Medium — template name on card, secondary button label. */
    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** 11sp / 14 / Medium — badge, meta, timestamp. */
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
}

/**
 * Material typography is only wired so that library-provided composables
 * (Text defaults, ripple, etc.) inherit something sane. App code should use
 * [AppType] directly.
 */
val AppMaterialTypography = Typography(
    displayLarge = AppType.display,
    displayMedium = AppType.display,
    displaySmall = AppType.display,
    headlineLarge = AppType.titleL,
    headlineMedium = AppType.titleL,
    headlineSmall = AppType.titleM,
    titleLarge = AppType.titleL,
    titleMedium = AppType.titleM,
    titleSmall = AppType.label,
    bodyLarge = AppType.body,
    bodyMedium = AppType.body,
    bodySmall = AppType.label,
    labelLarge = AppType.label,
    labelMedium = AppType.label,
    labelSmall = AppType.caption,
)

/** Kept for the BaseProject files that already reference `Typography`. */
val Typography = AppMaterialTypography
