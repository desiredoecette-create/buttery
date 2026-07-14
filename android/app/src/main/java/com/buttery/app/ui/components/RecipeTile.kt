package com.buttery.app.ui.components

import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

data class RecipeTileData(
    val title: String,
    @DrawableRes val imageRes: Int,
    val icon: RecipeTileIcon,
    val photoUri: String? = null,
    val eyebrow: String? = null,
    val subtitle: String? = null,
    val footer: String? = null,
    val action: String = title
)

enum class RecipeTileIcon { Search, Add, Continue, Favorite, Grocery, Settings, Explore }

private fun RecipeTileIcon.imageVector(): ImageVector = when (this) {
    RecipeTileIcon.Search -> Icons.Rounded.Search
    RecipeTileIcon.Add -> Icons.Rounded.Add
    RecipeTileIcon.Continue -> Icons.Rounded.PlayArrow
    RecipeTileIcon.Favorite -> Icons.Rounded.FavoriteBorder
    RecipeTileIcon.Grocery -> Icons.Rounded.ShoppingCart
    RecipeTileIcon.Settings -> Icons.Rounded.Settings
    RecipeTileIcon.Explore -> Icons.Rounded.Public
}

@Composable
fun RecipeTile(
    tile: RecipeTileData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    val isDetailedPhoneTile = isPhone && tile.eyebrow != null
    Surface(
        modifier = modifier
            .aspectRatio(1.58f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF181713),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (tile.photoUri == null) {
                Image(
                    painter = painterResource(tile.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                if (tile.photoUri.startsWith("http://") || tile.photoUri.startsWith("https://")) {
                    AsyncImage(
                        model = tile.photoUri,
                        contentDescription = "Recipe photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                contentDescription = "Recipe photo"
                            }
                        },
                        update = { it.setImageURI(Uri.parse(tile.photoUri)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
            Surface(
                modifier = if (isDetailedPhoneTile) {
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                } else {
                    Modifier
                        .align(Alignment.Center)
                        .padding(bottom = if (isPhone) 18.dp else 0.dp)
                        .size(if (isPhone) 48.dp else 58.dp)
                },
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color(0xFFF4EFE6).copy(alpha = 0.78f))
            ) {
                Icon(
                    imageVector = tile.icon.imageVector(),
                    contentDescription = null,
                    tint = Color(0xFFF4EFE6),
                    modifier = Modifier.padding(
                        if (isPhone) 12.dp else 14.dp
                    )
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = if (isPhone) 8.dp else 12.dp,
                        end = if (isDetailedPhoneTile) 42.dp else if (isPhone) 8.dp else 12.dp,
                        top = if (isPhone) 7.dp else 13.dp,
                        bottom = if (isPhone) 7.dp else 13.dp
                    ),
                horizontalAlignment = if (tile.eyebrow == null) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                }
            ) {
                tile.eyebrow?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFFC857),
                        fontSize = if (isDetailedPhoneTile) 8.sp else if (isPhone) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = tile.title,
                    color = Color(0xFFF4EFE6),
                    fontSize = if (isDetailedPhoneTile) 14.sp else if (isPhone) 16.sp else 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    lineHeight = if (isDetailedPhoneTile) 15.sp else if (isPhone) 18.sp else 22.sp,
                    textAlign = if (tile.eyebrow == null) TextAlign.Center else TextAlign.Start,
                    overflow = TextOverflow.Ellipsis
                )
                tile.subtitle?.let {
                    Text(
                        it,
                        color = Color(0xFFF4EFE6).copy(alpha = 0.8f),
                        fontSize = if (isDetailedPhoneTile) 9.sp else if (isPhone) 11.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                tile.footer?.let {
                    Text(
                        it,
                        color = Color(0xFFFFC857),
                        fontSize = if (isDetailedPhoneTile) 8.sp else if (isPhone) 10.sp else 12.sp,
                        lineHeight = if (isDetailedPhoneTile) 9.sp else if (isPhone) 12.sp else 14.sp,
                        maxLines = if (isDetailedPhoneTile) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
