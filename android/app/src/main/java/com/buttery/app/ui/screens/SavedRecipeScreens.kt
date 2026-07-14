package com.buttery.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeAlbum
import com.buttery.app.domain.RecipeVisibility

private val BrowseBackground = Color(0xFF11110E)
private val BrowsePanel = Color(0xFF1B1A15)
private val BrowseCream = Color(0xFFF4EFE3)
private val BrowseMuted = Color(0xFFC5BBA9)
private val BrowseGold = Color(0xFFC4A46B)
private val Paper = Color(0xFFF4EDDE)
private val PaperInk = Color(0xFF292218)

data class BrowseAlbum(
    val id: Long?,
    val name: String,
    val recipeCount: Int,
    val coverPhotoUri: String?
)

@Composable
fun AllAlbumsScreen(
    recipes: List<Recipe>,
    albums: List<RecipeAlbum>,
    onHome: () -> Unit,
    onAddRecipe: () -> Unit,
    onAlbumSelected: (Long?) -> Unit,
    onUpdateAlbum: (Long, String, String?) -> Unit,
    onDeleteAlbum: (Long) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var managedAlbum by remember { mutableStateOf<RecipeAlbum?>(null) }
    var editingAlbum by remember { mutableStateOf<RecipeAlbum?>(null) }
    var deletingAlbum by remember { mutableStateOf<RecipeAlbum?>(null) }
    val albumCards = remember(recipes, albums, search) {
        buildAlbumCards(recipes, albums).filter { album ->
            search.isBlank() ||
                album.name.contains(search, ignoreCase = true) ||
                recipes.any {
                    (album.id == null || it.albumId == album.id) && it.matchesSearch(search)
                }
        }
    }

    BrowseScaffold {
        BrowseTopBar(
            title = "Your Recipe Albums",
            subtitle = "Select an album to explore",
            centerPhoneHeading = true,
            search = search,
            searchHint = "Search albums or recipes…",
            onSearchChange = { search = it },
            onHome = onHome
        )
        if (albumCards.isEmpty()) {
            BrowseEmptyState(
                title = if (recipes.isEmpty()) "No recipes saved yet." else "No albums or recipes match your search.",
                actionLabel = if (recipes.isEmpty()) "Add Your First Recipe" else null,
                onAction = onAddRecipe
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 270.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(albumCards, key = { it.id ?: Long.MIN_VALUE }) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onAlbumSelected(album.id) },
                        onLongClick = album.id?.let { id ->
                            { managedAlbum = albums.firstOrNull { it.id == id } }
                        }
                    )
                }
                item(key = "add_recipe") {
                    AddRecipeCard(onClick = onAddRecipe)
                }
            }
        }
    }

    managedAlbum?.let { album ->
        AlbumManagementSheet(
            album = album,
            canDelete = true,
            onDismiss = { managedAlbum = null },
            onEdit = {
                managedAlbum = null
                editingAlbum = album
            },
            onDelete = {
                managedAlbum = null
                deletingAlbum = album
            }
        )
    }
    editingAlbum?.let { album ->
        EditAlbumDialog(
            album = album,
            onDismiss = { editingAlbum = null },
            onSave = { name, coverUri ->
                onUpdateAlbum(album.id, name, coverUri)
                editingAlbum = null
            }
        )
    }
    deletingAlbum?.let { album ->
        DeleteConfirmationDialog(
            itemName = album.name,
            itemType = "album",
            onDismiss = { deletingAlbum = null },
            onConfirm = {
                onDeleteAlbum(album.id)
                deletingAlbum = null
            }
        )
    }
}

@Composable
fun AlbumRecipesScreen(
    albumId: Long?,
    recipes: List<Recipe>,
    albums: List<RecipeAlbum>,
    onBackToAlbums: () -> Unit,
    onHome: () -> Unit,
    onAddRecipe: () -> Unit,
    onRecipeSelected: (Long) -> Unit,
    onFavoriteChanged: (Long, Boolean) -> Unit,
    onEditRecipe: (Long) -> Unit,
    onDeleteRecipe: (Long) -> Unit
) {
    var search by remember(albumId) { mutableStateOf("") }
    var managedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var deletingRecipe by remember { mutableStateOf<Recipe?>(null) }
    val albumName = if (albumId == null) {
        "All Recipes"
    } else {
        albums.firstOrNull { it.id == albumId }?.name ?: "Album"
    }
    val albumRecipes = remember(recipes, albumId, search) {
        recipes.filter { albumId == null || it.albumId == albumId }
            .filter { search.isBlank() || it.matchesSearch(search) }
    }
    val totalCount = recipes.count { albumId == null || it.albumId == albumId }

    BrowseScaffold {
        BrowseTopBar(
            title = albumName,
            subtitle = "$totalCount ${if (totalCount == 1) "recipe" else "recipes"}",
            search = search,
            searchHint = "Search in $albumName…",
            onSearchChange = { search = it },
            onHome = onHome,
            onBack = onBackToAlbums
        )
        if (albumRecipes.isEmpty()) {
            BrowseEmptyState(
                title = if (search.isBlank()) {
                    "No recipes in this album yet."
                } else {
                    "No recipes match your search."
                },
                actionLabel = if (search.isBlank()) "Add Recipe" else null,
                onAction = onAddRecipe
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 285.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(albumRecipes, key = { it.id }) { recipe ->
                    PremiumRecipeCard(
                        recipe = recipe,
                        onClick = { onRecipeSelected(recipe.id) },
                        albumName = albums.firstOrNull { it.id == recipe.albumId }?.name
                            ?: "All Recipes",
                        onFavoriteChanged = { onFavoriteChanged(recipe.id, it) },
                        onLongClick = { managedRecipe = recipe }
                    )
                }
            }
        }
    }

    managedRecipe?.let { recipe ->
        RecipeManagementSheet(
            recipe = recipe,
            onDismiss = { managedRecipe = null },
            onEdit = {
                managedRecipe = null
                onEditRecipe(recipe.id)
            },
            onDelete = {
                managedRecipe = null
                deletingRecipe = recipe
            }
        )
    }
    deletingRecipe?.let { recipe ->
        DeleteConfirmationDialog(
            itemName = recipe.title,
            itemType = "recipe",
            onDismiss = { deletingRecipe = null },
            onConfirm = {
                onDeleteRecipe(recipe.id)
                deletingRecipe = null
            }
        )
    }
}

@Composable
fun FavoritesScreen(
    recipes: List<Recipe>,
    albums: List<RecipeAlbum>,
    onHome: () -> Unit,
    onBrowseRecipes: () -> Unit,
    onRecipeSelected: (Long) -> Unit,
    onFavoriteChanged: (Long, Boolean) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val favorites = remember(recipes, albums, search) {
        recipes.filter { recipe ->
            recipe.isFavorite && (
                search.isBlank() ||
                    recipe.matchesSearch(search) ||
                    albums.firstOrNull { it.id == recipe.albumId }
                        ?.name
                        ?.contains(search, ignoreCase = true) == true
                )
        }
    }
    val favoriteCount = recipes.count { it.isFavorite }

    BrowseScaffold {
        BrowseTopBar(
            title = "Favorite Recipes",
            subtitle = "$favoriteCount ${if (favoriteCount == 1) "favorite" else "favorites"}",
            search = search,
            searchHint = "Search favorites…",
            onSearchChange = { search = it },
            onHome = onHome,
            onBack = onBrowseRecipes,
            centerPhoneHeading = true,
            phoneTopOffsetFraction = 0.08f
        )

        when {
            favoriteCount == 0 -> FavoritesEmptyState(
                onBrowseRecipes = onBrowseRecipes,
                onHome = onHome
            )
            favorites.isEmpty() -> BrowseEmptyState(
                title = "No favorite recipes match your search.",
                actionLabel = null,
                onAction = onBrowseRecipes
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 285.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(favorites, key = { it.id }) { recipe ->
                    PremiumRecipeCard(
                        recipe = recipe,
                        albumName = albums.firstOrNull { it.id == recipe.albumId }?.name
                            ?: "All Recipes",
                        onClick = { onRecipeSelected(recipe.id) },
                        onFavoriteChanged = { onFavoriteChanged(recipe.id, it) },
                        onLongClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesEmptyState(
    onBrowseRecipes: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.FavoriteBorder,
            contentDescription = null,
            tint = Color(0xFFFFC857),
            modifier = Modifier.size(58.dp)
        )
        Text(
            "No favorite recipes yet.",
            color = BrowseCream,
            fontFamily = FontFamily.Serif,
            fontSize = 32.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Tap the heart on any recipe to save it here.",
            color = BrowseMuted,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(
                onClick = onBrowseRecipes,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB96F3C))
            ) {
                Text("Browse Recipes", modifier = Modifier.padding(8.dp))
            }
            Button(
                onClick = onHome,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF687A48))
            ) {
                Text("Home", modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun BrowseScaffold(content: @Composable ColumnScope.() -> Unit) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3A2A18), BrowseBackground),
                    radius = 1_500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isPhone) 22.dp else 34.dp,
                    end = if (isPhone) 22.dp else 34.dp,
                    top = if (isPhone) 58.dp else 24.dp,
                    bottom = if (isPhone) 22.dp else 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 16.dp else 22.dp),
            content = content
        )
    }
}

@Composable
private fun BrowseTopBar(
    title: String,
    subtitle: String,
    search: String,
    searchHint: String,
    onSearchChange: (String) -> Unit,
    onHome: () -> Unit,
    onBack: (() -> Unit)? = null,
    centerPhoneHeading: Boolean = false,
    phoneTopOffsetFraction: Float = 0f
) {
    val configuration = LocalConfiguration.current
    val isPhone = configuration.screenWidthDp < 700
    val phoneTopOffset = (configuration.screenHeightDp * phoneTopOffsetFraction).dp
    if (isPhone) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = phoneTopOffset),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (centerPhoneHeading) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    RoundBrowseButton(
                        icon = Icons.Rounded.Home,
                        description = "Home",
                        onClick = onHome
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 60.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            title,
                            color = BrowseCream,
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            subtitle,
                            color = BrowseMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        RoundBrowseButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to All Albums", onBack)
                    }
                    RoundBrowseButton(Icons.Rounded.Home, "Home", onHome)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            title,
                            color = BrowseCream,
                            fontFamily = FontFamily.Serif,
                            fontSize = 31.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(subtitle, color = BrowseMuted, fontSize = 14.sp)
                    }
                }
            }
            BrowseSearchField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = searchHint,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                RoundBrowseButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to All Albums", onBack)
            }
            RoundBrowseButton(Icons.Rounded.Home, "Home", onHome)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    color = BrowseCream,
                    fontFamily = FontFamily.Serif,
                    fontSize = 36.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, color = BrowseMuted, fontSize = 16.sp)
            }
            BrowseSearchField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = searchHint,
                modifier = Modifier.fillMaxWidth(0.36f)
            )
        }
    }
}

@Composable
private fun BrowseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 58.dp),
        singleLine = true,
        placeholder = { Text(placeholder, color = BrowseMuted, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = BrowseCream) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Clear, "Clear search", tint = BrowseCream)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BrowseCream,
            unfocusedTextColor = BrowseCream,
            focusedBorderColor = BrowseGold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
            cursorColor = BrowseGold,
            focusedContainerColor = Color.Black.copy(alpha = 0.26f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun AlbumCard(
    album: BrowseAlbum,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrowseGold.copy(alpha = 0.62f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = BrowsePanel)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            ImageCardBackground(album.coverPhotoUri)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.96f)
                        )
                    )
                    .padding(18.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        album.name,
                        color = BrowseCream,
                        fontFamily = FontFamily.Serif,
                        fontSize = 25.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${album.recipeCount} ${if (album.recipeCount == 1) "recipe" else "recipes"}",
                        color = BrowseMuted,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumRecipeCard(
    recipe: Recipe,
    albumName: String,
    onClick: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.28f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrowseGold.copy(alpha = 0.58f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = BrowsePanel)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            ImageCardBackground(recipe.photoUri)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.44f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.97f)
                        )
                    )
                    .padding(18.dp)
            ) {
                IconButton(
                    onClick = { onFavoriteChanged(!recipe.isFavorite) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                ) {
                    Icon(
                        if (recipe.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (recipe.isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (recipe.isFavorite) Color(0xFFD97B68) else BrowseCream
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        recipe.title,
                        color = BrowseCream,
                        fontFamily = FontFamily.Serif,
                        fontSize = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(albumName, color = BrowseGold, fontSize = 14.sp, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (recipe.cookTime.isNotBlank()) {
                            Text("Cook ${recipe.cookTime}", color = BrowseMuted, fontSize = 14.sp)
                        }
                        if (recipe.servings.isNotBlank()) {
                            Text("${recipe.servings} servings", color = BrowseMuted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCardBackground(uri: String?) {
    if (uri == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF70512E), Color(0xFF2E291F), Color(0xFF151510))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Restaurant,
                contentDescription = null,
                tint = BrowseGold.copy(alpha = 0.72f),
                modifier = Modifier.size(64.dp)
            )
        }
    } else {
        RecipeImage(uri, Modifier.fillMaxSize())
    }
}

@Composable
private fun AddRecipeCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.35f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrowseGold.copy(alpha = 0.62f)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Add, null, tint = BrowseCream, modifier = Modifier.size(48.dp))
            Text("Add Recipe", color = BrowseCream, fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumManagementSheet(
    album: RecipeAlbum,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF242119),
        contentColor = BrowseCream
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(album.name, fontFamily = FontFamily.Serif, fontSize = 27.sp)
            ManagementAction(Icons.Rounded.Edit, "Edit Album", BrowseCream, onEdit)
            ManagementAction(
                Icons.Rounded.Delete,
                "Delete Album",
                if (canDelete) Color(0xFFE68B7C) else BrowseMuted.copy(alpha = 0.55f),
                onClick = onDelete,
                enabled = canDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeManagementSheet(
    recipe: Recipe,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF242119),
        contentColor = BrowseCream
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                recipe.title,
                fontFamily = FontFamily.Serif,
                fontSize = 27.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ManagementAction(Icons.Rounded.Edit, "Edit Recipe", BrowseCream, onEdit)
            ManagementAction(Icons.Rounded.Delete, "Delete Recipe", Color(0xFFE68B7C), onDelete)
        }
    }
}

@Composable
private fun ManagementAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        color = Color.Transparent,
        contentColor = tint
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Text(label, fontSize = 19.sp)
        }
    }
}

@Composable
private fun EditAlbumDialog(
    album: RecipeAlbum,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var name by remember(album.id) { mutableStateOf(album.name) }
    var coverUri by remember(album.id) { mutableStateOf(album.customCoverImageUri) }
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            coverUri = uri.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = PaperInk,
        textContentColor = PaperInk,
        title = { Text("Edit Album", fontFamily = FontFamily.Serif, fontSize = 29.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Album name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { coverPicker.launch(arrayOf("image/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF687A48))
                    ) {
                        Icon(Icons.Rounded.Image, null)
                        Text(if (coverUri == null) "  Add Cover Photo" else "  Change Cover")
                    }
                    if (coverUri != null) {
                        TextButton(onClick = { coverUri = null }) {
                            Text("Remove Photo", color = Color(0xFF9A4137))
                        }
                    }
                }
                Text(
                    if (coverUri == null) {
                        "Without a custom cover, the first recipe photo is used."
                    } else {
                        "Custom album cover selected."
                    },
                    color = PaperInk.copy(alpha = 0.64f),
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), coverUri) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF687A48))
            ) {
                Text("Save Changes")
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    itemName: String,
    itemType: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = PaperInk,
        textContentColor = PaperInk,
        title = {
            Text(
                "Delete \"$itemName\"?",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp
            )
        },
        text = {
            Text(
                if (itemType == "album") {
                    "Recipes in this album will remain available in All Recipes."
                } else {
                    "This recipe will be permanently removed from this tablet."
                },
                fontSize = 17.sp
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA493C))
            ) {
                Text("Delete")
            }
        }
    )
}

@Composable
private fun BrowseEmptyState(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Restaurant, null, tint = BrowseGold, modifier = Modifier.size(76.dp))
        Text(
            title,
            color = BrowseCream,
            fontFamily = FontFamily.Serif,
            fontSize = 29.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 20.dp)
        )
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF687A48)),
                modifier = Modifier.heightIn(min = 58.dp)
            ) {
                Icon(Icons.Rounded.Add, null)
                Text("  $actionLabel", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun RecipeDetailScreen(
    recipe: Recipe?,
    albumName: String,
    backLabel: String,
    onBackToAlbum: () -> Unit,
    onHome: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onVisibilityChanged: (RecipeVisibility) -> Unit,
    onEdit: (Recipe) -> Unit,
    onShare: (Recipe) -> Unit
) {
    var enlargedPhotoUri by remember(recipe?.id) { mutableStateOf<String?>(null) }
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    if (isPhone) {
        PhoneRecipeDetailScreen(
            recipe = recipe,
            albumName = albumName,
            backLabel = backLabel,
            onBackToAlbum = onBackToAlbum,
            onHome = onHome,
            onFavoriteChanged = onFavoriteChanged,
            onVisibilityChanged = onVisibilityChanged,
            onEdit = onEdit,
            onShare = onShare,
            enlargedPhotoUri = enlargedPhotoUri,
            onPhotoClick = { enlargedPhotoUri = it },
            onDismissPhoto = { enlargedPhotoUri = null }
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF49331E), Color(0xE60A0A08)),
                    radius = 1_600f
                )
            )
            .padding(26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onBackToAlbum,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                Text("  $backLabel")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF687A48),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        "🍳  Cooking Mode Active",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                    )
                }
                RoundBrowseButton(Icons.Rounded.Edit, "Edit") { recipe?.let(onEdit) }
                RoundBrowseButton(
                    if (recipe?.visibility == RecipeVisibility.Public) Icons.Rounded.Lock else Icons.Rounded.Public,
                    if (recipe?.visibility == RecipeVisibility.Public) "Make private" else "Make public"
                ) {
                    recipe?.let {
                        onVisibilityChanged(
                            if (it.visibility == RecipeVisibility.Public) {
                                RecipeVisibility.Private
                            } else {
                                RecipeVisibility.Public
                            }
                        )
                    }
                }
                RoundBrowseButton(Icons.Rounded.Share, "Share") { recipe?.let(onShare) }
                RoundBrowseButton(Icons.Rounded.Home, "Home", onHome)
                RoundBrowseButton(Icons.Rounded.Close, "Close", onBackToAlbum)
            }
        }

        if (recipe == null) {
            Text(
                "Recipe not found.",
                color = BrowseCream,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.87f)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(22.dp),
            color = Paper,
            shadowElevation = 18.dp
        ) {
            Box {
                BinderHoles(Modifier.align(Alignment.CenterStart))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 62.dp, end = 38.dp, top = 34.dp, bottom = 38.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    if (recipe.photoUris.isNotEmpty()) {
                        RecipePhotoGallery(
                            photoUris = recipe.photoUris,
                            onPhotoClick = { enlargedPhotoUri = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.05f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                recipe.title,
                                color = PaperInk,
                                fontFamily = FontFamily.Serif,
                                fontSize = 39.sp,
                                lineHeight = 43.sp
                            )
                            Text(albumName, color = Color(0xFF745B33), fontSize = 17.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                DetailFact("Prep", recipe.prepTime)
                                DetailFact("Cook", recipe.cookTime)
                                DetailFact("Serves", recipe.servings)
                            }
                            if (recipe.notes.isNotBlank()) {
                                Text(
                                    recipe.notes,
                                    color = PaperInk.copy(alpha = 0.78f),
                                    fontSize = 17.sp,
                                    lineHeight = 25.sp,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(0.35f)) {
                            IconButton(
                                onClick = { onFavoriteChanged(!recipe.isFavorite) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .background(Paper.copy(alpha = 0.88f), CircleShape)
                            ) {
                                Icon(
                                    if (recipe.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Toggle favorite",
                                    tint = if (recipe.isFavorite) Color(0xFFB95143) else PaperInk
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(34.dp)
                    ) {
                        PaperSection(
                            title = "Ingredients",
                            body = recipe.ingredients.ifBlank { "No ingredients added." },
                            modifier = Modifier.weight(1f)
                        )
                        PaperSection(
                            title = "Instructions",
                            body = recipe.instructions.ifBlank { "No instructions added." },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (recipe.videoUri != null) {
                        RecipeVideo(recipe.videoUri)
                    }
                }
            }
        }
        enlargedPhotoUri?.let { photoUri ->
            EnlargedRecipePhotoDialog(
                photoUri = photoUri,
                onDismiss = { enlargedPhotoUri = null }
            )
        }
    }
}

@Composable
private fun PhoneRecipeDetailScreen(
    recipe: Recipe?,
    albumName: String,
    backLabel: String,
    onBackToAlbum: () -> Unit,
    onHome: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onVisibilityChanged: (RecipeVisibility) -> Unit,
    onEdit: (Recipe) -> Unit,
    onShare: (Recipe) -> Unit,
    enlargedPhotoUri: String?,
    onPhotoClick: (String) -> Unit,
    onDismissPhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF49331E), Color(0xE60A0A08)),
                    radius = 1_300f
                )
            )
            .padding(start = 18.dp, end = 18.dp, top = 54.dp, bottom = 18.dp)
    ) {
        if (recipe == null) {
            Text(
                "Recipe not found.",
                color = BrowseCream,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackToAlbum,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(17.dp))
                    Text("  $backLabel", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                }
                RoundBrowseButton(Icons.Rounded.Edit, "Edit") { onEdit(recipe) }
                RoundBrowseButton(Icons.Rounded.Home, "Home", onHome)
            }

            if (recipe.photoUris.isNotEmpty()) {
                RecipePhotoGallery(
                    photoUris = recipe.photoUris,
                    onPhotoClick = onPhotoClick
                )
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                color = Paper,
                shadowElevation = 18.dp
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF687A48),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                "🍳  Cooking Mode Active",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                        Text(
                            recipe.title,
                            color = PaperInk,
                            fontFamily = FontFamily.Serif,
                            fontSize = 34.sp,
                            lineHeight = 38.sp
                        )
                        Text(albumName, color = Color(0xFF745B33), fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            DetailFact("Prep", recipe.prepTime)
                            DetailFact("Cook", recipe.cookTime)
                            DetailFact("Serves", recipe.servings)
                        }
                        if (recipe.notes.isNotBlank()) {
                            Text(
                                recipe.notes,
                                color = PaperInk.copy(alpha = 0.78f),
                                fontSize = 16.sp,
                                lineHeight = 23.sp
                            )
                        }
                        PaperSection("Ingredients", recipe.ingredients.ifBlank { "No ingredients added." })
                        PaperSection("Instructions", recipe.instructions.ifBlank { "No instructions added." })
                        if (recipe.videoUri != null) {
                            RecipeVideo(recipe.videoUri)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Paper.copy(alpha = 0.78f), RoundedCornerShape(26.dp))
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(onClick = { onFavoriteChanged(!recipe.isFavorite) }, modifier = Modifier.size(42.dp)) {
                            Icon(
                                if (recipe.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Toggle favorite",
                                tint = if (recipe.isFavorite) Color(0xFFB95143) else PaperInk,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { onShare(recipe) }, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Rounded.Share, "Share", tint = PaperInk, modifier = Modifier.size(21.dp))
                        }
                        IconButton(
                            onClick = {
                                onVisibilityChanged(
                                    if (recipe.visibility == RecipeVisibility.Public) RecipeVisibility.Private else RecipeVisibility.Public
                                )
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                if (recipe.visibility == RecipeVisibility.Public) Icons.Rounded.Lock else Icons.Rounded.Public,
                                contentDescription = "Change visibility",
                                tint = PaperInk,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }

        enlargedPhotoUri?.let { photoUri ->
            EnlargedRecipePhotoDialog(photoUri = photoUri, onDismiss = onDismissPhoto)
        }
    }
}

@Composable
private fun RecipePhotoGallery(photoUris: List<String>, onPhotoClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        photoUris.forEach { photoUri ->
            RecipeImage(
                uri = photoUri,
                modifier = Modifier
                    .size(310.dp, 190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onPhotoClick(photoUri) }
            )
        }
    }
}

@Composable
private fun EnlargedRecipePhotoDialog(photoUri: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.86f))
                .padding(34.dp)
                .clickable(onClick = onDismiss)
        ) {
            RecipeImage(
                uri = photoUri,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.58f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close photo", tint = Color.White)
            }
        }
    }
}

@Composable
private fun RecipeImage(uri: String, modifier: Modifier = Modifier) {
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        AsyncImage(
            model = uri,
            contentDescription = "Recipe photo",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
    } else {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Recipe photo"
                }
            },
            update = { it.setImageURI(Uri.parse(uri)) },
            modifier = modifier
        )
    }
}

@Composable
private fun RecipeVideo(videoUri: String) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Video", color = PaperInk, fontFamily = FontFamily.Serif, fontSize = 27.sp)
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(Color.Black).clip(RoundedCornerShape(18.dp))
        )
    }
}

@Composable
private fun BinderHoles(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(start = 18.dp),
        verticalArrangement = Arrangement.spacedBy(110.dp)
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(Color(0xFF5A4630), CircleShape)
                    .padding(4.dp)
                    .background(Color(0xFF16130F), CircleShape)
            )
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    if (value.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = PaperInk.copy(alpha = 0.55f), fontSize = 13.sp)
            Text(value, color = PaperInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PaperSection(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = PaperInk, fontFamily = FontFamily.Serif, fontSize = 26.sp)
        Text(body, color = PaperInk.copy(alpha = 0.88f), fontSize = 17.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun RoundBrowseButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(Color.Black.copy(alpha = 0.46f), CircleShape)
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

private fun buildAlbumCards(
    recipes: List<Recipe>,
    albums: List<RecipeAlbum>
): List<BrowseAlbum> {
    val allRecipes = BrowseAlbum(
        id = null,
        name = "All Recipes",
        recipeCount = recipes.size,
        coverPhotoUri = recipes.firstNotNullOfOrNull { it.photoUri }
    )
    val storedAlbums = albums.mapNotNull { album ->
        val albumRecipes = recipes.filter { it.albumId == album.id }
        BrowseAlbum(
            id = album.id,
            name = album.name,
            recipeCount = albumRecipes.size,
            coverPhotoUri = album.customCoverImageUri
                ?: albumRecipes.firstNotNullOfOrNull { it.photoUri }
        )
    }
    return listOf(allRecipes) + storedAlbums
}

private fun Recipe.matchesSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        ingredients.contains(query, ignoreCase = true) ||
        notes.contains(query, ignoreCase = true)
