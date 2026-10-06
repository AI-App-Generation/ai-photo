package com.bblvn.composebaseproject.features.paywall

import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.features.iap.CreditPack
import com.bblvn.composebaseproject.features.iap.Iap
import com.bblvn.composebaseproject.features.iap.IapController
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.utils.IntentUtils

/**
 * Paywall mua credit (sản phẩm mua một lần, tiêu thụ). Khác paywall PRO: không tắt ads, không có
 * khôi phục (gói đã consume thì không còn gì để khôi phục).
 *
 * Mua xong thì [IapController] cộng credit và phát [IapController.creditPurchased]; màn này báo số
 * credit vừa nhận rồi tự đóng.
 */
@Composable
fun CreditStoreScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prices by IapController.prices.collectAsState()
    val balance by AppCapability.creditBalance.collectAsState()
    // Gói giữa là gói nên chọn sẵn.
    var selected by remember { mutableStateOf(Iap.creditPacks[POPULAR_INDEX]) }

    LaunchedEffect(Unit) {
        IapController.creditPurchased.collect { credits ->
            Toast.makeText(
                context,
                context.getString(R.string.credit_store_purchased, credits),
                Toast.LENGTH_SHORT,
            ).show()
            onClose()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        Image(
            painter = painterResource(R.drawable.bg_paywall_luxe),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.4f,
        )
        Box(modifier = Modifier.fillMaxSize().background(AppGradients.scrimToRoot()))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 56.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(AppGradients.halo(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.bolt),
                            contentDescription = null,
                            tint = AppColors.goldLight,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.credit_store_title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.credit_store_subtitle),
                        style = AppType.body,
                        color = AppColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.credit_store_balance, balance),
                        style = AppType.label,
                        color = AppColors.goldLight,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Iap.creditPacks.forEachIndexed { index, pack ->
                        PackRow(
                            pack = pack,
                            selected = selected == pack,
                            price = prices[pack.productId] ?: "—",
                            badge = when (index) {
                                POPULAR_INDEX -> R.string.paywall_most_popular
                                Iap.creditPacks.lastIndex -> R.string.credit_store_best_value
                                else -> null
                            },
                            onSelect = { selected = pack },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(160.dp)) }
        }

        AppIconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.paywall_close),
            background = Color.Black.copy(alpha = 0.35f),
            onClick = onClose,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(AppGradients.bottomCtaFade)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton(
                text = stringResource(R.string.paywall_continue),
                height = 58.dp,
                onClick = { activity?.let { IapController.buy(it, selected.productId) } },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FooterText(stringResource(R.string.paywall_terms)) { IntentUtils.openUrl(context, TERMS_URL) }
                FooterText(stringResource(R.string.paywall_privacy)) { IntentUtils.openUrl(context, PRIVACY_URL) }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: CreditPack,
    selected: Boolean,
    price: String,
    badge: Int?,
    onSelect: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .then(if (selected) Modifier.glowBrand(AppShapes.card) else Modifier)
                .clip(AppShapes.card)
                .background(AppColors.bgSurface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AppColors.accentBlue else AppColors.strokeSoft,
                    shape = AppShapes.card,
                )
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.bolt),
                contentDescription = null,
                tint = AppColors.goldLight,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.credit_store_pack, pack.credits),
                style = AppType.titleM,
                color = AppColors.textPrimary,
            )
            Text(text = price, style = AppType.titleM, color = AppColors.textPrimary)
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .offset(y = (-8).dp)
                    .clip(AppShapes.chip)
                    .background(AppGradients.gold)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = stringResource(badge),
                    style = AppType.caption,
                    color = AppColors.textOnAccent,
                )
            }
        }
    }
}

@Composable
private fun FooterText(text: String, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(AppShapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        text = text,
        style = AppType.caption,
        color = AppColors.textTertiary,
    )
}

/** Gói giữa (index 1) là gói đề xuất. */
private const val POPULAR_INDEX = 1

private const val PRIVACY_URL = "https://doc-hosting.flycricket.io/vezora-ai-photo-generator-privacy-policy/df218ef2-ab65-47d7-bfaa-fc12a3b1f948/privacy"
private const val TERMS_URL = "https://bblvn.com/terms"
