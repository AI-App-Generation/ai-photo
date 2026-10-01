package com.bblvn.composebaseproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.imageScrim

/**
 * The three card footprints used across the app. There are no others.
 */
enum class TemplateCardSize(
    val fixedWidth: Dp?,
    val fixedHeight: Dp?,
    val aspectRatio: Float?,
) {
    /** 150 x 210 — Home category LazyRow. */
    Row(150.dp, 210.dp, null),

    /** 3:4, fills the grid cell — Category All, Creations. */
    Grid(null, null, 3f / 4f),

    /** 120 x 168 — "Cùng phong cách" strip on Template Detail. */
    Compact(120.dp, 168.dp, null),
}

/**
 * Image-first template / creation card: the picture fills the card, a scrim
 * gradient sits on top of it (R9 — never white text straight onto an image),
 * and the name goes on the scrim.
 *
 * Deliberately takes primitives rather than a data model so that the component
 * library does not depend on `data/`.
 */
@Composable
fun TemplateCard(
    modifier: Modifier = Modifier,
    imageModel: Any?,
    name: String,
    size: TemplateCardSize = TemplateCardSize.Row,
    subtitle: String? = null,
    isPremium: Boolean = false,
    /** e.g. "2 ảnh" — shown top-start over the image. */
    topStartBadge: String? = null,
    selected: Boolean = false,
    seed: Any? = imageModel,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Extra layer over the image: Creations uses it for the processing / failed state. */
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var base = modifier
    size.fixedWidth?.let { base = base.width(it) }
    size.fixedHeight?.let { base = base.height(it) }
    // R3.9: aspectRatio must come after any weight the caller applied.
    size.aspectRatio?.let { base = base.fillMaxWidth().aspectRatio(it) }

    base = base
        .clip(AppShapes.card)
        .background(AppColors.bgSurface)

    if (selected) base = base.border(2.dp, AppColors.strokeActive, AppShapes.card)
    if (onClick != null) base = base.clickable(onClick = onClick)

    Box(modifier = base) {
        RemoteImage(
            modifier = Modifier.matchParentSize(),
            model = imageModel,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            placeholderSeed = seed,
            // No broken-image icon on a card: the template name already sits on top, so a clean
            // theme gradient reads as intentional artwork rather than as a grid full of errors.
            showPlaceholderIcon = false,
        )

        // Mandatory scrim before any text lands on the image.
        Box(Modifier.matchParentSize().imageScrim(startFraction = 0.45f, endAlpha = 0.80f))

        if (topStartBadge != null) {
            AppChip(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                text = topStartBadge,
                size = AppChipSize.Small,
                style = AppChipStyle.Overlay,
            )
        }

        if (isPremium) {
            PremiumBadge(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                size = AppChipSize.Small,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Text(
                text = name,
                style = AppType.label,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppType.caption,
                    color = AppColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        overlay()
    }
}

/** Skeleton with exactly the same footprint, for the loading grid/row. */
@Composable
fun TemplateCardSkeleton(
    modifier: Modifier = Modifier,
    size: TemplateCardSize = TemplateCardSize.Row,
) {
    var base = modifier
    size.fixedWidth?.let { base = base.width(it) }
    size.fixedHeight?.let { base = base.height(it) }
    size.aspectRatio?.let { base = base.fillMaxWidth().aspectRatio(it) }
    ShimmerBox(modifier = base, shape = AppShapes.card)
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun TemplateCardPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TemplateCard(
                imageModel = null,
                name = "Cyberpunk Neon",
                isPremium = true,
                topStartBadge = "2 ảnh",
            )
            TemplateCard(
                imageModel = null,
                name = "Vintage Film",
                size = TemplateCardSize.Compact,
                subtitle = "2 giờ trước",
            )
        }
    }
}
