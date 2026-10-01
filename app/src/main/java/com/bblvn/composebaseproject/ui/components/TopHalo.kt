package com.bblvn.composebaseproject.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients

/**
 * The ambient violet wash that sits behind the top of Home, drawn with a Brush (R8).
 * Shared by the entry-flow screens so Language / Survey read as the same app as Home.
 * Place it first inside a `Box` so everything else draws over it.
 */
@Composable
fun TopHalo(
    modifier: Modifier = Modifier,
    height: Dp = 320.dp,
    radius: Dp = 520.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawRect(
                    AppGradients.topHalo(
                        radiusPx = radius.toPx(),
                        centerX = size.width / 2f,
                    )
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, AppColors.bgRoot)))
            }
    )
}
