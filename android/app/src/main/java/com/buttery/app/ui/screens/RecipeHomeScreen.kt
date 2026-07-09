package com.buttery.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buttery.app.R
import com.buttery.app.domain.Recipe
import com.buttery.app.ui.components.RecipeTile
import com.buttery.app.ui.components.RecipeTileData
import com.buttery.app.ui.components.RecipeTileIcon

@Composable
fun RecipeHomeScreen(
    onTileSelected: (String) -> Unit,
    lastRecipe: Recipe?,
    lastRecipeAlbumName: String?,
    lastOpenedTimestamp: Long?,
    profilePhotoUri: String?,
    hasProfileNotification: Boolean,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles = remember(lastRecipe, lastRecipeAlbumName, lastOpenedTimestamp) {
        listOf(
            RecipeTileData("Browse Recipes", R.drawable.ambient_extra_01, RecipeTileIcon.Search),
            RecipeTileData("Enter New Recipe", R.drawable.ambient_extra_02, RecipeTileIcon.Add),
            if (lastRecipe == null) {
                RecipeTileData("Continue Recipe", R.drawable.ambient_extra_03, RecipeTileIcon.Continue)
            } else {
                RecipeTileData(
                    title = lastRecipe.title,
                    imageRes = R.drawable.ambient_extra_03,
                    icon = RecipeTileIcon.Continue,
                    photoUri = lastRecipe.photoUri,
                    eyebrow = "CONTINUE RECIPE",
                    subtitle = lastRecipeAlbumName,
                    footer = "${relativeLastOpened(lastOpenedTimestamp)}  •  Resume Cooking →",
                    action = "Continue Recipe"
                )
            },
            RecipeTileData("Favorites", R.drawable.ambient_extra_04, RecipeTileIcon.Favorite),
            RecipeTileData("Grocery List", R.drawable.ambient_extra_05, RecipeTileIcon.Grocery),
            RecipeTileData("Settings", R.drawable.ambient_extra_06, RecipeTileIcon.Settings)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF15130F).copy(alpha = 0.97f),
                            Color(0xFF211B13).copy(alpha = 0.9f),
                            Color(0xFF2F2418).copy(alpha = 0.76f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Welcome back!",
                        color = Color(0xFFF4EFE6),
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = "What would you like to cook today?",
                        color = Color(0xFFF4EFE6).copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                }
                Box(modifier = Modifier.size(62.dp)) {
                    IconButton(
                        onClick = onProfile,
                        modifier = Modifier
                            .size(58.dp)
                            .align(Alignment.Center)
                            .background(Color(0xFFF4EFE6), CircleShape)
                            .border(2.dp, Color(0xFFFFC857), CircleShape)
                    ) {
                        ProfileAvatar(profilePhotoUri, 52)
                    }
                    if (hasProfileNotification) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(15.dp)
                                .background(Color(0xFFD7433B), CircleShape)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                tiles.chunked(3).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            RecipeTile(
                                tile = tile,
                                modifier = Modifier.weight(1f),
                                onClick = { onTileSelected(tile.action) }
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 42.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            Image(
                painter = painterResource(R.drawable.buttery_wordmark),
                contentDescription = "Buttery",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(200.dp)
                    .height(100.dp)
            )
            Text(
                text = "your personal kitchen dashboard",
                color = Color.White,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

private fun relativeLastOpened(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "Recently opened"
    val elapsedMinutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
    return when {
        elapsedMinutes < 1 -> "Opened just now"
        elapsedMinutes < 60 -> "Opened $elapsedMinutes ${if (elapsedMinutes == 1L) "minute" else "minutes"} ago"
        elapsedMinutes < 24 * 60 -> {
            val hours = elapsedMinutes / 60
            "Opened $hours ${if (hours == 1L) "hour" else "hours"} ago"
        }
        elapsedMinutes < 48 * 60 -> "Opened yesterday"
        else -> "Opened ${elapsedMinutes / (24 * 60)} days ago"
    }
}
