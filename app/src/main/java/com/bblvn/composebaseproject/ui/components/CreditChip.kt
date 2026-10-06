package com.bblvn.composebaseproject.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType

/**
 * Mở màn mua credit. Cung cấp một lần ở `MainContent` để chip (và dialog hết credit) ở bất kỳ màn nào
 * cũng mở được mà không phải truyền callback qua từng màn.
 */
val LocalOpenCreditStore = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Số dư credit ở góc phải top bar: tia chớp + số dư + nút tròn xanh dấu cộng. Cả chip bấm được và mở
 * màn mua credit. Cố ý thấp hơn chip PRO ([AppChipSize.Medium], 36dp): dùng cỡ Small (28dp) của hệ
 * thống chip (R1) nên hai chip cạnh nhau vẫn đúng quy tắc chiều cao.
 */
@Composable
fun CreditChip(modifier: Modifier = Modifier) {
    val balance by AppCapability.creditBalance.collectAsState()
    val openStore = LocalOpenCreditStore.current
    val description = stringResource(R.string.credits_balance_description, balance)
    val size = AppChipSize.Small

    Row(
        modifier = modifier
            .height(size.height)
            .clip(AppShapes.chip)
            .background(AppColors.bgElevated)
            .border(1.dp, AppColors.strokeSoft, AppShapes.chip)
            .clickable(onClickLabel = stringResource(R.string.credits_add_description), onClick = openStore)
            .semantics { contentDescription = description }
            .padding(start = size.horizontalPadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.bolt),
            contentDescription = null,
            // Tia chớp tông vàng (cùng tông chip PRO).
            tint = AppColors.goldLight,
            modifier = Modifier.size(size.iconSize + 2.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = balance.toString(),
            style = size.textStyle,
            color = AppColors.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(AppColors.accentBlue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * Giá tính bằng credit hiện bên trong nút bắt đầu tạo ảnh: viên thuốc nhỏ có tia chớp + số. Màu
 * theo [contentColor] của nút nên tự đổi theo trạng thái (sáng / xám khi vô hiệu).
 */
@Composable
fun CreditCostBadge(
    cost: Int,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AppShapes.chip)
            .background(contentColor.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.bolt),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = cost.toString(),
            style = AppType.label,
            color = contentColor,
            maxLines = 1,
        )
    }
}
