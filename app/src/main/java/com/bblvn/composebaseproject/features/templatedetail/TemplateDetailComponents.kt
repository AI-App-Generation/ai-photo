package com.bblvn.composebaseproject.features.templatedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.CompareSlider
import com.bblvn.composebaseproject.ui.components.GradientPlaceholder
import com.bblvn.composebaseproject.ui.components.PhotoSlot
import com.bblvn.composebaseproject.ui.components.PremiumBadge
import com.bblvn.composebaseproject.ui.components.RemoteImage
import com.bblvn.composebaseproject.ui.components.TemplateCard
import com.bblvn.composebaseproject.ui.components.TemplateCardSize
import com.bblvn.composebaseproject.ui.components.TextActionButton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.rootScrim

private const val HERO_ASPECT = 3f / 4f

/**
 * The short blurb under the meta chips.
 *
 * Composed by the app from the template name and its category. The catalog's `prompt` field is
 * a ~6.4k-character engineering artifact and is NEVER surfaced to the user.
 */
@Composable
internal fun templateDescription(template: Template, categoryName: String): String {
    val name = template.name
    return when {
        categoryName.isNotBlank() && template.allowsTwoImages ->
            stringResource(R.string.template_detail_desc_two, name, categoryName)

        categoryName.isNotBlank() ->
            stringResource(R.string.template_detail_desc_one, name, categoryName)

        template.allowsTwoImages ->
            stringResource(R.string.template_detail_desc_two_plain, name)

        else -> stringResource(R.string.template_detail_desc_one_plain, name)
    }
}

/**
 * Full-bleed 3:4 hero. Reuses the shared [CompareSlider] — including its one-shot
 * 0.5 -> 0.75 -> 0.5 hint (`hintOnFirstShow`) that tells the user the divider is draggable.
 *
 * Risks #15 — a sparse catalog row can leave BOTH compare urls null, and the pair can also be
 * the same single image. Neither case may reach the slider (a null model would shimmer forever),
 * so the hero degrades to a single image and then to [GradientPlaceholder].
 */
@Composable
internal fun TemplateHero(template: Template) {
    val before = template.compareBeforeUrl?.takeIf { it.isNotBlank() }
    val after = template.compareAfterUrl?.takeIf { it.isNotBlank() }
    val canCompare = before != null && after != null && before != after

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT),
    ) {
        when {
            canCompare -> CompareSlider(
                modifier = Modifier.fillMaxSize(),
                before = before,
                after = after,
                // Both halves come from the same catalog entry and share a ratio, so Crop is
                // right here (Fit is the component default for mismatched pairs).
                contentScale = ContentScale.Crop,
                hintOnFirstShow = true,
                overlay = { HeroLabels() },
            )

            after != null || before != null -> {
                RemoteImage(
                    modifier = Modifier.fillMaxSize(),
                    model = after ?: before,
                    contentDescription = template.name,
                    contentScale = ContentScale.Crop,
                    placeholderSeed = template.id,
                )
            }

            else -> GradientPlaceholder(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.ui.graphics.RectangleShape,
                seed = template.id,
                label = template.name,
            )
        }

        // Dissolve the bottom of the hero into bg_root so the info block reads as one surface.
        Box(Modifier.fillMaxSize().rootScrim(startFraction = 0.70f))
    }
}

/** "TRƯỚC" / "SAU" chips, drawn on the image so they need the Overlay (scrimmed) chip style. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.HeroLabels() {
    AppChip(
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(16.dp),
        text = stringResource(R.string.template_detail_before),
        size = AppChipSize.Small,
        style = AppChipStyle.Overlay,
    )
    AppChip(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(16.dp),
        text = stringResource(R.string.template_detail_after),
        size = AppChipSize.Small,
        style = AppChipStyle.Overlay,
    )
}

/**
 * Name + premium badge + meta chips + the app-composed description.
 *
 * The raw `prompt` is NEVER shown: it is a ~6.4k-character engineering artifact. The blurb is
 * built from the template name and its category instead.
 */
@Composable
internal fun TemplateInfo(
    template: Template,
    categoryName: String,
    description: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    var overflows by remember(description) { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = template.name,
                style = AppType.titleL,
                color = AppColors.textPrimary,
                maxLines = 2,
                // Risks #32 — long Vietnamese names must ellipsize, never push the badge away.
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (template.isPremium) {
                PremiumBadge(
                    // R1 — Top, not CenterVertically: the badge must stay on the first line when
                    // the name wraps to two.
                    modifier = Modifier.align(Alignment.Top).padding(start = 8.dp),
                    size = AppChipSize.Small,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(
                text = stringResource(
                    if (template.allowsTwoImages) {
                        R.string.template_detail_photos_two
                    } else {
                        R.string.template_detail_photos_one
                    }
                ),
                icon = R.drawable.ic_image,
                size = AppChipSize.Small,
            )
            AppChip(
                text = stringResource(R.string.template_detail_duration),
                icon = R.drawable.ic_clock,
                size = AppChipSize.Small,
            )
            if (categoryName.isNotBlank()) {
                AppChip(
                    modifier = Modifier.weight(1f, fill = false),
                    text = categoryName,
                    icon = R.drawable.ic_layers,
                    size = AppChipSize.Small,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = description,
            style = AppType.body,
            color = AppColors.textSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded && result.hasVisualOverflow) overflows = true
            },
        )

        if (overflows) {
            TextActionButton(
                modifier = Modifier.padding(top = 4.dp),
                text = stringResource(
                    if (expanded) {
                        R.string.template_detail_read_less
                    } else {
                        R.string.template_detail_read_more
                    }
                ),
                height = 36.dp,
                horizontalPadding = 0.dp,
                onClick = onToggleExpand,
            )
        }
    }
}

/**
 * Two photo slots — shown ONLY for `allows_two_images` templates (R5).
 *
 * Slot order is a contract with the prompt (Risks #17): index 0 is always the first picture the
 * prompt expects, index 1 the second. The labels are numbered for exactly that reason and must
 * never be reordered.
 */
@Composable
internal fun TemplateInputSlots(
    slots: List<String?>,
    onPick: (Int) -> Unit,
    onClear: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
        Text(
            text = stringResource(R.string.template_detail_slots_title),
            style = AppType.titleM,
            color = AppColors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.template_detail_slots_subtitle),
            style = AppType.caption,
            color = AppColors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoSlot(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.template_detail_slot_first),
                imageModel = slots.getOrNull(0),
                onPick = { onPick(0) },
                onClear = slots.getOrNull(0)?.let { { onClear(0) } },
            )
            PhotoSlot(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.template_detail_slot_second),
                imageModel = slots.getOrNull(1),
                onPick = { onPick(1) },
                onClear = slots.getOrNull(1)?.let { { onClear(1) } },
            )
        }
    }
}

/** "Cùng phong cách" — the rest of this template's category, as Compact cards. */
@Composable
internal fun MoreFromCategory(
    templates: List<Template>,
    onTemplateClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = stringResource(R.string.template_detail_related_title),
            style = AppType.titleM,
            color = AppColors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(templates, key = { it.id }) { item ->
                TemplateCard(
                    imageModel = item.displayImageUrl.ifBlank { null },
                    name = item.name,
                    size = TemplateCardSize.Compact,
                    isPremium = item.isPremium,
                    seed = item.id,
                    onClick = { onTemplateClick(item.id) },
                )
            }
        }
    }
}
