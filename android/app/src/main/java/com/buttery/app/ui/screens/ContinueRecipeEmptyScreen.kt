package com.buttery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContinueRecipeEmptyScreen(
    onBrowseRecipes: () -> Unit,
    onAddRecipe: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF49331E), Color(0xFF0A0A08)),
                    radius = 1_600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Icon(
                Icons.Rounded.Restaurant,
                contentDescription = null,
                tint = Color(0xFFFFC857)
            )
            Text(
                "No recipe in progress yet.",
                color = Color(0xFFF4EFE6),
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp
            )
            Text(
                "Open a recipe and Buttery will remember it here.",
                color = Color(0xFFF4EFE6).copy(alpha = 0.72f),
                fontSize = 17.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = onBrowseRecipes) {
                    Text("Browse Recipes", modifier = Modifier.padding(8.dp))
                }
                Button(
                    onClick = onAddRecipe,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB96F3C))
                ) {
                    Text("Add Your First Recipe", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
