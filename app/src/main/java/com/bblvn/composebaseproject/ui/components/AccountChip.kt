package com.bblvn.composebaseproject.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bblvn.composebaseproject.R

/**
 * The free/PRO badge in the top-right of the tab screens.
 *
 * Both states are the same `AppChipSize.Medium`, so the bar does not twitch when entitlement
 * flips (R1) — only the colour and the click behaviour change. A PRO user has nothing to buy, so
 * the chip becomes non-clickable rather than routing to a paywall they already passed.
 */
@Composable
fun AccountChip(
    isPremium: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = AppChip(
    modifier = modifier,
    text = stringResource(R.string.home_account_pro),
    icon = R.drawable.ic_crown,
    size = AppChipSize.Medium,
    style = if (isPremium) AppChipStyle.Gold else AppChipStyle.GoldOutline,
    glow = isPremium,
    onClick = if (isPremium) null else onClick,
)
