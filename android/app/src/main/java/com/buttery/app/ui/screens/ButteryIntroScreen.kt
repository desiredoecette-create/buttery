package com.buttery.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.buttery.app.R
import kotlinx.coroutines.delay

private val IntroCharcoal = Color(0xFF121212)

@Composable
fun ButteryIntroScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.9f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(650))
        logoScale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        taglineAlpha.animateTo(1f, tween(500))
        delay(1000)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IntroCharcoal),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.buttery_wordmark),
                contentDescription = "Buttery",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(416.dp)
                    .height(208.dp)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "your personal kitchen dashboard",
                color = Color.White,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}
