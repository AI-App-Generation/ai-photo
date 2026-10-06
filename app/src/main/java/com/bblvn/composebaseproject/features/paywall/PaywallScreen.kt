package com.bblvn.composebaseproject.features.paywall

import androidx.compose.runtime.collectAsState
import com.bblvn.composebaseproject.features.iap.IapController
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.features.iap.Iap
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.ButtonVariant
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.ui.theme.glowGold
import com.bblvn.composebaseproject.utils.IntentUtils

/**
 * Subscription offer.
 *
 * Mua và khôi phục đi qua [IapController]; màn này không tự đổi quyền lợi. Mua xong thì premium
 * bật qua `AppCapability` và MainContent tự đóng paywall.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var selectedPlan by remember { mutableStateOf(PaywallPlan.MONTHLY) }
    // Giá thật từ Google Play (theo tiền tệ của user); chưa có thì hiện "—" thay vì một con số cứng.
    val prices by IapController.prices.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        Image(
            painter = painterResource(R.drawable.bg_paywall_luxe),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
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
                    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppGradients.halo(), CircleShape)
                        )
                        Image(
                            painter = painterResource(R.drawable.logo_pro_crown),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.paywall_title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.paywall_subtitle),
                        style = AppType.body,
                        color = AppColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PAYWALL_BENEFITS.forEach { benefit ->
                        BenefitRow(text = stringResource(benefit))
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PaywallPlan.entries.forEach { plan ->
                        PlanRow(
                            plan = plan,
                            selected = selectedPlan == plan,
                            price = prices[plan.sku] ?: "—",
                            onSelect = { selectedPlan = plan },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(240.dp)) }
        }

        // Visible immediately — no delayed or hidden close button.
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
                variant = ButtonVariant.Gold,
                height = 58.dp,
                onClick = { activity?.let { IapController.buy(it, selectedPlan.sku) } },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FooterLink(stringResource(R.string.paywall_restore)) { IapController.restore() }
                FooterLink(stringResource(R.string.paywall_terms)) {
                    IntentUtils.openUrl(context, TERMS_URL)
                }
                FooterLink(stringResource(R.string.paywall_privacy)) {
                    IntentUtils.openUrl(context, PRIVACY_URL)
                }
            }
        }
    }
}

/**
 * The two subscription offers, mapped to the SKU ids already declared in [Iap] so the screen and the
 * billing layer cannot drift apart. Prices are NOT here: they come from Google Play at runtime.
 */
enum class PaywallPlan(
    val sku: String,
    val nameRes: Int,
    val noteRes: Int,
    val unitRes: Int,
    val highlighted: Boolean = false,
) {
    WEEKLY(
        sku = Iap.weeklyId,
        nameRes = R.string.paywall_plan_weekly,
        noteRes = R.string.paywall_plan_weekly_note,
        unitRes = R.string.paywall_unit_weekly,
    ),
    MONTHLY(
        sku = Iap.monthlyId,
        nameRes = R.string.paywall_plan_monthly,
        noteRes = R.string.paywall_plan_monthly_note,
        unitRes = R.string.paywall_unit_monthly,
        highlighted = true,
    ),
}

@Composable
private fun PlanRow(
    plan: PaywallPlan,
    selected: Boolean,
    price: String,
    onSelect: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .then(if (selected) Modifier.glowBrand(AppShapes.card) else Modifier)
                .clip(AppShapes.card)
                .background(AppColors.bgSurface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AppColors.accentViolet else AppColors.strokeSoft,
                    shape = AppShapes.card,
                )
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(plan.nameRes),
                    style = AppType.titleM,
                    color = AppColors.textPrimary,
                )
                Text(
                    text = stringResource(plan.noteRes),
                    style = AppType.caption,
                    color = AppColors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = price,
                    style = AppType.titleM,
                    color = AppColors.textPrimary,
                )
                Text(
                    text = stringResource(plan.unitRes),
                    style = AppType.caption,
                    color = AppColors.textTertiary,
                )
            }
        }
        if (plan.highlighted) {
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
                    text = stringResource(R.string.paywall_most_popular),
                    style = AppType.caption,
                    color = AppColors.textOnAccent,
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AppColors.goldDeep.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(AppColors.goldLight),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = AppType.body,
            color = AppColors.textPrimary,
        )
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
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

private val PAYWALL_BENEFITS = listOf(
    R.string.paywall_benefit_remove_ads,
    R.string.paywall_benefit_faster,
    R.string.paywall_benefit_no_watermark,
    R.string.paywall_benefit_premium_templates,
)

private const val PRIVACY_URL = "https://doc-hosting.flycricket.io/vezora-ai-photo-generator-privacy-policy/df218ef2-ab65-47d7-bfaa-fc12a3b1f948/privacy"
private const val TERMS_URL = "https://bblvn.com/terms"
