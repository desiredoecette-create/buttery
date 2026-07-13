package com.buttery.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ButteryAndroidApp() {
    BoxWithConstraints {
        val layoutMode = if (maxWidth < 700.dp || maxHeight > maxWidth) {
            ButteryLayoutMode.Phone
        } else {
            ButteryLayoutMode.Tablet
        }
        RecipeTabletApp(layoutMode = layoutMode)
    }
}
