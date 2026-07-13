package com.buttery.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buttery.app.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun AmbientSlideshowScreen(modifier: Modifier = Modifier) {
    val images = remember {
        listOf(
            R.drawable.ambient_pancakes,
            R.drawable.ambient_salad,
            R.drawable.ambient_salmon,
            R.drawable.ambient_bread,
            R.drawable.ambient_extra_01,
            R.drawable.ambient_extra_02,
            R.drawable.ambient_extra_03,
            R.drawable.ambient_extra_04,
            R.drawable.ambient_extra_05,
            R.drawable.ambient_extra_06,
            R.drawable.ambient_extra_07,
            R.drawable.ambient_extra_08,
            R.drawable.ambient_extra_09,
            R.drawable.ambient_extra_10
        )
    }
    var currentImageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(images) {
        while (true) {
            delay(8.seconds)
            currentImageIndex = (currentImageIndex + 1) % images.size
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Crossfade(
            targetState = currentImageIndex,
            animationSpec = tween(durationMillis = 1500),
            label = "ambient_food_crossfade"
        ) { imageIndex ->
            Image(
                painter = painterResource(images[imageIndex]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.68f)
                        )
                    )
                )
        )
        Text(
            text = "Tap to start cooking",
            color = Color(0xFFF4EFE6),
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
        )
        Image(
            painter = painterResource(R.drawable.buttery_wordmark),
            contentDescription = "Buttery",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .width(126.dp)
                .height(64.dp)
        )
    }
}
