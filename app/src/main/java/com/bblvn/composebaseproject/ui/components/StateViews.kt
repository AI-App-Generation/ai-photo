package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType

/**
 * Centred "nothing here yet" state. Pass either [illustration] (a raster asset,
 * e.g. `empty_creations`) or [icon]; the illustration wins when both are given.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    message: String? = null,
    @DrawableRes illustration: Int? = null,
    @DrawableRes icon: Int? = R.drawable.ic_image_off,
    illustrationSize: Dp = 160.dp,
    iconSize: Dp = 44.dp,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                illustration != null -> Image(
                    painter = painterResource(illustration),
                    contentDescription = null,
                    modifier = Modifier.size(illustrationSize),
                )

                icon != null -> Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = AppColors.textTertiary,
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = AppType.titleM,
                color = AppColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = AppType.body,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    modifier = Modifier.width(220.dp),
                    text = actionText,
                    fillMaxWidth = false,
                    height = 48.dp,
                    onClick = onAction,
                )
            }
            if (secondaryActionText != null && onSecondaryAction != null) {
                Spacer(Modifier.height(8.dp))
                TextActionButton(
                    text = secondaryActionText,
                    color = AppColors.textTertiary,
                    onClick = onSecondaryAction,
                )
            }
        }
    }
}

/**
 * Centred failure state with a retry. Same skeleton as [EmptyState] but the
 * icon is an alert and the action is a [SecondaryButton] so a failure never
 * competes visually with a real CTA.
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    title: String,
    message: String? = null,
    @DrawableRes icon: Int = R.drawable.ic_cloud_off,
    iconSize: Dp = 48.dp,
    iconTint: androidx.compose.ui.graphics.Color = AppColors.textTertiary,
    actionText: String? = "Thử lại",
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.height(16.dp))
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
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                SecondaryButton(
                    text = actionText,
                    icon = R.drawable.ic_refresh,
                    onClick = onAction,
                )
            }
            if (secondaryActionText != null && onSecondaryAction != null) {
                Spacer(Modifier.height(8.dp))
                TextActionButton(
                    text = secondaryActionText,
                    color = AppColors.textSecondary,
                    onClick = onSecondaryAction,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 720)
@Composable
private fun StateViewsPreview() {
    AppTheme {
        Column(Modifier.background(AppColors.bgRoot)) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    title = "Chưa có tác phẩm nào",
                    message = "Chọn một template và bắt đầu tạo ảnh đầu tiên của bạn",
                    actionText = "Khám phá template",
                    onAction = {},
                )
            }
            Box(Modifier.weight(1f)) {
                ErrorState(
                    title = "Không tải được bộ sưu tập",
                    message = "Kiểm tra kết nối mạng rồi thử lại.",
                    onAction = {},
                )
            }
        }
    }
}
