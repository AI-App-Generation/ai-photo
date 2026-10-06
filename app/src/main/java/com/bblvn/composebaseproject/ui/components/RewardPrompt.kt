package com.bblvn.composebaseproject.ui.components

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.bblvn.composebaseproject.core.AppCapability
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowSoft

/**
 * Bốn lý do hiện dialog "xem quảng cáo thưởng". Mỗi lý do gắn với đúng một placement rewarded và
 * một cặp tiêu đề/mô tả.
 */
enum class RewardKind(
    val placement: String,
    @StringRes val title: Int,
    @StringRes val message: Int,
) {
    /** Dùng một lần template PRO. */
    UnlockTemplate(
        AdsPlacementHolder.REWARDED_UNLOCK_TEMPLATE,
        R.string.reward_template_title,
        R.string.reward_template_message,
    ),

    /** Dùng thử một lần tool PRO (Face swap, Virtual try-on). */
    UnlockTool(
        AdsPlacementHolder.REWARDED_UNLOCK_TOOL,
        R.string.reward_tool_title,
        R.string.reward_tool_message,
    ),

    /** Hết credit: xem ad để nhận credit rồi tạo/Regenerate tiếp. */
    Credits(
        AdsPlacementHolder.REWARDED_REGENERATE,
        R.string.reward_credits_title,
        R.string.reward_credits_message,
    ),

    /** Lưu ảnh không watermark (quyền lợi PRO). */
    HqSave(
        AdsPlacementHolder.REWARDED_HQ_SAVE,
        R.string.reward_save_title,
        R.string.reward_save_message,
    ),
}

/**
 * Dialog dùng chung cho mọi điểm cần "xem quảng cáo thưởng hoặc mở paywall". Luôn hiện khi user
 * chạm điểm chặn (nút khoá PRO, hết credit, Save của user free) thay vì sửa giao diện gốc của màn.
 *
 *  - Nút chính: xem quảng cáo thưởng ([RewardKind.placement]). Chỉ khi xem HẾT mới gọi [onGranted];
 *    ad lỗi hoặc đóng giữa chừng thì báo và dialog vẫn mở để user chọn cách khác.
 *  - Nút thứ hai: mở paywall ([onOpenPaywall]).
 *  - Nút thứ ba (tuỳ chọn, [onSkip]): bỏ qua, chấp nhận hậu quả (vd. "Save with watermark").
 *
 * [kind] null thì không vẽ gì. Người gọi tự đóng bằng [onDismiss] (đặt lại [kind] về null).
 */
@Composable
fun RewardPrompt(
    kind: RewardKind?,
    onGranted: (RewardKind) -> Unit,
    onOpenPaywall: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: (() -> Unit)? = null,
    @StringRes skipText: Int? = null,
) {
    val current = kind ?: return
    val activity = LocalActivity.current
    val context = LocalContext.current
    val openCreditStore = LocalOpenCreditStore.current
    // Hết credit mà cũng hết lượt xem quảng cáo thưởng: chỉ còn đường mua gói credit.
    val rewardedViews by AppCapability.rewardedCreditViews.collectAsState()
    val canWatch = current != RewardKind.Credits || rewardedViews < AppCapability.MAX_REWARDED_CREDIT_VIEWS
    // Chặn bấm "Watch ad" hai lần trong lúc quảng cáo đang nạp/hiện.
    var watching by remember { mutableStateOf(false) }

    // Nạp sẵn ngay khi dialog mở để bấm là có.
    LaunchedEffect(current, canWatch) { if (canWatch) AdsPolicy.loadAd(current.placement) }

    fun toast(@StringRes message: Int) {
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
    }

    fun watch() {
        val host = activity ?: return
        if (watching) return
        watching = true
        AdsPolicy.showRewardAd(
            activity = host,
            adPlacement = current.placement,
            onRewardGranted = {
                watching = false
                onGranted(current)
                onDismiss()
            },
            onAdFailed = {
                watching = false
                toast(R.string.reward_ad_unavailable)
            },
            onDismissedWithoutReward = {
                watching = false
                toast(R.string.reward_incomplete)
            },
        )
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.bgScrim.copy(alpha = 0.55f))
                .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .glowSoft(AppShapes.card)
                    .clip(AppShapes.card)
                    .background(AppColors.bgSurface)
                    .border(1.dp, AppColors.strokeSoft, AppShapes.card)
                    // Nuốt chạm để bấm vào thẻ không đóng dialog.
                    .clickable(interactionSource = interaction, indication = null, onClick = {})
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(current.title),
                    style = AppType.titleM,
                    color = AppColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (canWatch) current.message else R.string.reward_credits_message_no_ad
                    ),
                    style = AppType.body,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                if (canWatch) {
                    PrimaryButton(
                        text = stringResource(R.string.reward_watch_ad),
                        icon = R.drawable.ic_sparkles,
                        height = 48.dp,
                        enabled = !watching,
                        onClick = ::watch,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (current == RewardKind.Credits) {
                    // Hết credit: lối ra là mua gói credit (PRO không kèm credit).
                    SecondaryButton(
                        text = stringResource(R.string.reward_buy_credits),
                        fillMaxWidth = true,
                        onClick = {
                            onDismiss()
                            openCreditStore()
                        },
                    )
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.reward_unlock_pro),
                        icon = R.drawable.ic_crown,
                        variant = ButtonVariant.Gold,
                        height = 48.dp,
                        onClick = onOpenPaywall,
                    )
                }

                if (onSkip != null && skipText != null) {
                    Spacer(Modifier.height(10.dp))
                    // Cùng cỡ với hai nút trên (cao 48dp, full width) và có viền xám để không chìm.
                    SecondaryButton(
                        text = stringResource(skipText),
                        fillMaxWidth = true,
                        contentColor = AppColors.textSecondary,
                        borderColor = AppColors.textTertiary,
                        onClick = onSkip,
                    )
                }
            }
        }
    }
}
