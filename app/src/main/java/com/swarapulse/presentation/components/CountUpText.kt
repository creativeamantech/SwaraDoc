package com.swarapulse.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

@Composable
fun CountUpText(
    targetValue: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    durationMillis: Int = 1000
) {
    val animatedValue = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        animatedValue.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(durationMillis = durationMillis)
        )
    }

    Text(
        text = animatedValue.value.toInt().toString(),
        modifier = modifier,
        style = style
    )
}
