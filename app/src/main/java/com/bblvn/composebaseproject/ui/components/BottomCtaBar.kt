package com.bblvn.composebaseproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.ui.theme.AppGradients

/**
 * Floating bottom CTA area used by Template Detail, Tool Detail and Paywall.
 *
 * A transparent -> `bg_root` fade is drawn behind the content so the button
 * stays readable when it floats over a photo, and the bar applies its own
 * `navigationBarsPadding()` so the CTA can never sit under the gesture bar.
 *
 * Every scrolling screen that uses this also ends its list with
 * `Spacer(240.dp)`, so nothing is ever hidden behind it.
 */
@Composable
fun BottomCtaBar(
    modifier: Modifier = Modifier,
    fadeHeight: Dp = 120.dp,
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(fadeHeight)
                .align(Alignment.BottomCenter)
                .background(AppGradients.bottomCtaFade)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 0.dp)
                .padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
