package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowSoft
import kotlinx.coroutines.delay

/**
 * Confirmation dialog, built from [Popup] — Material's `Dialog` is never used.
 *
 * Renders a full-window 55% scrim plus a `bg_surface` card. Tapping the scrim or
 * pressing back dismisses (unless [dismissOnOutsideTouch] is false, which the
 * Generating screen uses for the hard cancel confirmation).
 */
@Composable
fun ConfirmPopup(
    visible: Boolean,
    title: String,
    message: String? = null,
    confirmText: String,
    cancelText: String? = null,
    destructive: Boolean = false,
    dismissOnOutsideTouch: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnOutsideTouch,
        ),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.bgScrim.copy(alpha = 0.55f))
                .then(
                    if (dismissOnOutsideTouch) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onDismiss,
                        )
                    } else {
                        Modifier
                    }
                ),
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
                    // Swallow clicks so tapping the card does not dismiss it.
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = AppType.titleM,
                    color = AppColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = AppType.body,
                        color = AppColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (cancelText != null) {
                        SecondaryButton(
                            modifier = Modifier.weight(1f),
                            text = cancelText,
                            fillMaxWidth = true,
                            onClick = onDismiss,
                        )
                    }
                    if (destructive) {
                        SecondaryButton(
                            modifier = Modifier.weight(1f),
                            text = confirmText,
                            fillMaxWidth = true,
                            contentColor = AppColors.stateDanger,
                            onClick = onConfirm,
                        )
                    } else {
                        PrimaryButton(
                            modifier = Modifier.weight(1f),
                            text = confirmText,
                            height = 48.dp,
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lightweight toast, also built from [Popup]. Shows for [durationMillis] then
 * calls [onDismiss]; the caller owns the visibility flag.
 */
@Composable
fun AppToast(
    visible: Boolean,
    text: String,
    @DrawableRes icon: Int? = R.drawable.ic_check_circle,
    iconTint: Color = AppColors.stateSuccess,
    durationMillis: Long = 1800L,
    bottomPaddingPx: Int = 0,
    onDismiss: () -> Unit,
) {
    val dismiss = rememberUpdatedState(onDismiss)
    LaunchedEffect(visible, text) {
        if (visible) {
            delay(durationMillis)
            dismiss.value()
        }
    }

    if (!visible) return

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -bottomPaddingPx),
        properties = PopupProperties(focusable = false),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .glowSoft(AppShapes.chip)
                .clip(AppShapes.chip)
                .background(AppColors.bgElevated)
                .border(1.dp, AppColors.strokeSoft, AppShapes.chip)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AppType.label,
                color = AppColors.textPrimary,
            )
        }
    }
}

/**
 * Anchored menu list (Result "more" menu, Photo Picker album list).
 * Also a [Popup]; entries are plain rows so any icon set can be used.
 */
@Composable
fun MenuPopup(
    visible: Boolean,
    items: List<MenuEntry>,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.TopEnd,
    offset: IntOffset = IntOffset(0, 0),
) {
    if (!visible) return
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .width(220.dp)
                .glowSoft(AppShapes.field)
                .clip(AppShapes.field)
                .background(AppColors.bgElevated)
                .border(1.dp, AppColors.strokeSoft, AppShapes.field)
                .padding(vertical = 4.dp),
        ) {
            items.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            entry.onClick()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.icon != null) {
                        Icon(
                            painter = painterResource(entry.icon),
                            contentDescription = null,
                            tint = if (entry.danger) AppColors.stateDanger else AppColors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = entry.text,
                        style = AppType.label,
                        color = if (entry.danger) AppColors.stateDanger else AppColors.textPrimary,
                    )
                }
            }
        }
    }
}

data class MenuEntry(
    val text: String,
    @param:DrawableRes val icon: Int? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)
