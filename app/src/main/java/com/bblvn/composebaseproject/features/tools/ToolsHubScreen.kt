package com.bblvn.composebaseproject.features.tools

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.data.domain.ToolType
import com.bblvn.composebaseproject.ui.components.AccountChip
import com.bblvn.composebaseproject.ui.components.PremiumBadge
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.imageScrim

/**
 * The Tools tab: five template-less quick edits.
 *
 * Scrollable from the start even though the content fits today — the tool list is the part of this
 * app most likely to grow.
 */
@Composable
fun ToolsHubScreen(
    onToolClick: (String) -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPremium by SettingManager.isPurchased.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 56.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Text(
                        text = stringResource(R.string.tools_title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.tools_subtitle),
                        style = AppType.body,
                        color = AppColors.textSecondary,
                    )
                }
            }

            item {
                FeaturedToolCard(
                    modifier = Modifier.padding(top = 20.dp),
                    tool = ToolPresentation.featured,
                    locked = ToolPresentation.featured.isPremium && !isPremium,
                    onClick = { onToolClick(ToolPresentation.featured.name) },
                )
            }

            item {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToolPresentation.grid.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { tool ->
                                ToolCard(
                                    modifier = Modifier.weight(1f),
                                    tool = tool,
                                    locked = tool.isPremium && !isPremium,
                                    onClick = { onToolClick(tool.name) },
                                )
                            }
                            // Keeps the last row aligned to the grid when the count is odd.
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(240.dp)) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            AccountChip(isPremium = isPremium, onClick = onOpenPaywall)
        }
    }
}

@Composable
private fun FeaturedToolCard(
    modifier: Modifier = Modifier,
    tool: ToolType,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(AppShapes.card)
            .background(AppColors.bgSurface)
            .border(1.dp, AppColors.strokeSoft, AppShapes.card)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(ToolPresentation.cover(tool)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // The generated artwork keeps its left third calm on purpose, so this scrim leaves the
        // subject readable while the text sits on near-solid colour.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.62f)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_sparkles),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AppColors.accentCyan),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.tools_popular),
                    style = AppType.caption,
                    color = AppColors.accentCyan,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(ToolPresentation.name(tool)),
                style = AppType.titleL,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(ToolPresentation.subtitle(tool)),
                style = AppType.caption,
                color = AppColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (locked) {
            PremiumBadge(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    tool: ToolType,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(AppShapes.card)
            .background(AppColors.bgSurface)
            .border(1.dp, AppColors.strokeSoft, AppShapes.card)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(ToolPresentation.cover(tool)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().imageScrim())
        if (locked) {
            PremiumBadge(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(AppShapes.chip)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, AppColors.strokeSoft, AppShapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(ToolPresentation.icon(tool)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AppColors.textPrimary),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(ToolPresentation.name(tool)),
                style = AppType.label,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(ToolPresentation.subtitle(tool)),
                style = AppType.caption,
                color = AppColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
