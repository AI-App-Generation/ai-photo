package com.bblvn.composebaseproject.ui.components.ads

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors

@Preview
@Composable
fun ComposeNativeAdDialog(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    LocalActivity.current

    var visible by rememberSaveable { mutableStateOf(true) }

    val isActive by AdsLoader.isActive.collectAsState()

    // Không tô nền ở đây: frame_ad tự có nền tối bo góc, nền trắng sẽ lộ ra ở hai góc bo.
    if (visible && isActive) Box(
        modifier = modifier.fillMaxWidth()
    ) {
        ComposeNativeAdFrame(
            layoutResId = R.layout.frame_ad,
            templateAdLayoutRes = R.layout.template_ad,
            adPlacement = AdsPlacementHolder.NATIVE,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(AppColors.bgElevated.copy(alpha = 0.85f))
                .clickable {
                    visible = false
                    onClose()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
