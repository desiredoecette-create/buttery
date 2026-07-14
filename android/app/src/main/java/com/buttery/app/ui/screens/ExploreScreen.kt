package com.buttery.app.ui.screens

import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.buttery.app.R
import com.buttery.app.data.CommunityRecipe
import com.buttery.app.data.PublicProfile
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeVisibility

private val ExploreNavy = Color(0xFF111A23)
private val ExploreButter = Color(0xFFFFC857)
private val ExploreCream = Color(0xFFF4EFE6)
private val ExploreOlive = Color(0xFF748463)

private enum class ExploreMode { Explore, Following }

private data class ExploreFilter(val label: String, val icon: ImageVector, val keywords: List<String>)

private val ExploreFilters = listOf(
    ExploreFilter("All", Icons.Rounded.AutoAwesome, emptyList()),
    ExploreFilter("Breakfast", Icons.Rounded.WbSunny, listOf("breakfast", "pancake", "waffle", "egg", "toast", "oat")),
    ExploreFilter("Lunch", Icons.Rounded.LunchDining, listOf("lunch", "sandwich", "salad", "soup", "wrap")),
    ExploreFilter("Dinner", Icons.Rounded.DinnerDining, listOf("dinner", "pasta", "chicken", "steak", "rice", "lamb", "fish")),
    ExploreFilter("Desserts", Icons.Rounded.Cake, listOf("dessert", "cake", "cookie", "brownie", "sweet", "pie")),
    ExploreFilter("Drinks", Icons.Rounded.LocalCafe, listOf("drink", "coffee", "tea", "smoothie", "cocktail", "juice"))
)

@Composable
fun ExploreScreen(
    recipes: List<CommunityRecipe>,
    likedRecipeIds: Set<String>,
    subscribedCreatorIds: Set<String>,
    currentUserId: String?,
    onHome: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLike: (CommunityRecipe) -> Unit,
    onSave: (CommunityRecipe) -> Unit,
    onShare: (CommunityRecipe) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    var mode by remember { mutableStateOf(ExploreMode.Explore) }
    var selectedFilter by remember { mutableStateOf(ExploreFilters.first()) }
    var query by remember { mutableStateOf("") }
    var selectedRecipe by remember { mutableStateOf<CommunityRecipe?>(null) }
    var showSearch by remember { mutableStateOf(false) }

    val filtered = remember(recipes, mode, selectedFilter, subscribedCreatorIds) {
        recipes
            .filter { recipe -> mode == ExploreMode.Explore || subscribedCreatorIds.contains(recipe.ownerId) }
            .filter { recipe -> selectedFilter.keywords.isEmpty() || selectedFilter.keywords.any(recipe::matchesKeyword) }
            .sortedWith(compareByDescending<CommunityRecipe> { it.likeCount }
                .thenByDescending { it.publicPublishedAt.takeIf { value -> value > 0L } ?: it.updatedAt })
    }
    val searchResults = remember(recipes, query) {
        if (query.isBlank()) emptyList() else recipes.filter { it.matchesKeyword(query) }.take(12)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF15130F).copy(alpha = 0.97f),
                        Color(0xFF211B13).copy(alpha = 0.92f),
                        Color(0xFF2F2418).copy(alpha = 0.82f)
                    )
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = if (isPhone) 18.dp else 34.dp,
                    end = if (isPhone) 18.dp else 34.dp,
                    top = if (isPhone) 56.dp else 26.dp,
                    bottom = if (isPhone) 16.dp else 26.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 14.dp else 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onHome,
                    modifier = Modifier
                        .size(if (isPhone) 46.dp else 54.dp)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Home, "Home", tint = ExploreCream, modifier = Modifier.size(if (isPhone) 24.dp else 30.dp))
                }
                Spacer(Modifier.weight(1f))
                ExploreToggle(mode = mode, onModeChange = { mode = it })
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { showSearch = true },
                    modifier = Modifier
                        .size(if (isPhone) 46.dp else 54.dp)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Search, "Search", tint = ExploreButter, modifier = Modifier.size(if (isPhone) 28.dp else 32.dp))
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExploreFilters.forEach { filter ->
                    FilterPill(
                        filter = filter,
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (mode == ExploreMode.Following) {
                            "No recipes from subscribed creators yet."
                        } else {
                            "No public recipes match this filter yet."
                        },
                        color = ExploreCream.copy(alpha = 0.78f),
                        fontSize = 22.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { recipe ->
                        ExploreRecipeCard(
                            recipe = recipe,
                            isPhone = isPhone,
                            onClick = { selectedRecipe = recipe },
                            onOpenProfile = { onOpenProfile(recipe.ownerId) }
                        )
                    }
                }
            }
        }
    }

    if (showSearch) {
        SearchRecipeDialog(
            query = query,
            onQueryChange = { query = it },
            results = searchResults,
            onOpen = {
                selectedRecipe = it
                showSearch = false
            },
            onDismiss = { showSearch = false }
        )
    }

    selectedRecipe?.let { selected ->
        val recipe = recipes.firstOrNull { it.id == selected.id } ?: selected
        CommunityRecipeDetailDialog(
            recipe = recipe,
            liked = likedRecipeIds.contains(recipe.id),
            currentUserId = currentUserId,
            onDismiss = { selectedRecipe = null },
            onLike = { onLike(recipe) },
            onSave = { onSave(recipe) },
            onShare = { onShare(recipe) },
            onOpenProfile = { onOpenProfile(recipe.ownerId) }
        )
    }
}

@Composable
private fun ExploreToggle(mode: ExploreMode, onModeChange: (ExploreMode) -> Unit) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Row(horizontalArrangement = Arrangement.spacedBy(if (isPhone) 20.dp else 26.dp), verticalAlignment = Alignment.CenterVertically) {
        ExploreToggleLabel("Explore", mode == ExploreMode.Explore) { onModeChange(ExploreMode.Explore) }
        ExploreToggleLabel("Following", mode == ExploreMode.Following) { onModeChange(ExploreMode.Following) }
    }
}

@Composable
private fun ExploreToggleLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            label,
            color = if (selected) ExploreCream else ExploreCream.copy(alpha = 0.46f),
            fontWeight = FontWeight.Bold,
            fontSize = if (isPhone) 22.sp else 27.sp
        )
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(width = if (isPhone) 42.dp else 52.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) ExploreButter else Color.Transparent)
        )
    }
}

@Composable
private fun FilterPill(filter: ExploreFilter, selected: Boolean, onClick: () -> Unit) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(34.dp),
        color = if (selected) ExploreButter else Color.Black.copy(alpha = 0.38f),
        modifier = Modifier.heightIn(min = if (isPhone) 44.dp else 56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isPhone) 11.dp else 14.dp, vertical = if (isPhone) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 4.dp else 6.dp)
        ) {
            Icon(
                imageVector = filter.icon,
                contentDescription = null,
                tint = if (selected) Color.Black else Color.White,
                modifier = Modifier.size(if (isPhone) 16.dp else 20.dp)
            )
            Text(
                filter.label,
                color = if (selected) ExploreNavy else ExploreCream,
                fontSize = if (isPhone) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExploreRecipeCard(
    recipe: CommunityRecipe,
    isPhone: Boolean,
    onClick: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(if (isPhone) 1.34f else 2.72f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, ExploreButter.copy(alpha = 0.38f))
    ) {
        Box(Modifier.fillMaxSize()) {
            RecipeRemoteImage(recipe.thumbnailUrl)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(if (isPhone) 18.dp else 26.dp),
                verticalArrangement = Arrangement.spacedBy(if (isPhone) 4.dp else 5.dp)
            ) {
                Text(
                    recipe.title,
                    color = ExploreCream,
                    fontSize = if (isPhone) 24.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileAvatar(recipe.ownerProfilePhotoUrl, 30)
                    Text(
                        "by @${recipe.ownerUsername}",
                        color = ExploreCream.copy(alpha = 0.72f),
                        fontSize = if (isPhone) 15.sp else 18.sp
                    )
                }
                Text("${formatCount(recipe.likeCount)} ♥", color = ExploreCream.copy(alpha = 0.82f), fontSize = if (isPhone) 14.sp else 16.sp)
            }
        }
    }
}

@Composable
private fun CommunityRecipeDetailDialog(
    recipe: CommunityRecipe,
    liked: Boolean,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenProfile: () -> Unit,
    visibilityActionLabel: String? = null,
    onVisibilityToggle: (() -> Unit)? = null
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    if (isPhone) {
        PhoneCommunityRecipeDetailDialog(
            recipe = recipe,
            liked = liked,
            currentUserId = currentUserId,
            onDismiss = onDismiss,
            onLike = onLike,
            onSave = onSave,
            onShare = onShare,
            onOpenProfile = onOpenProfile,
            visibilityActionLabel = visibilityActionLabel,
            onVisibilityToggle = onVisibilityToggle
        )
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 30.dp)
        ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(Color(0xFF49331E), Color(0xE60A0A08)), radius = 1_900f),
                        RoundedCornerShape(28.dp)
                    ).padding(22.dp)
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.42f), CircleShape)
                            ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                        }
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            Box(
                                Modifier
                                    .weight(0.9f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(22.dp))
                            ) {
                                RecipeVerticalMediaGallery(
                                    photoUrls = recipe.photoUrls.ifEmpty {
                                        listOfNotNull(recipe.thumbnailUrl)
                                    },
                                    videoUrls = recipe.videoUrls
                                )
                            }
                            Surface(
                                modifier = Modifier.weight(1.18f).fillMaxSize(),
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFFF4EDDE),
                                shadowElevation = 18.dp
                            ) {
                                Box {
                                    BinderHoles(Modifier.align(Alignment.CenterStart))
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize().padding(start = 68.dp, end = 104.dp, top = 42.dp, bottom = 42.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        item {
                                            Text(
                                                recipe.title,
                                                color = Color(0xFF292218),
                                                fontFamily = FontFamily.Serif,
                                                fontSize = 44.sp,
                                                lineHeight = 48.sp
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .padding(top = 8.dp)
                                                    .clickable(onClick = onOpenProfile),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                ProfileAvatar(recipe.ownerProfilePhotoUrl, 36)
                                                Text(
                                                    "@${recipe.ownerUsername}",
                                                    color = ExploreButter,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (recipe.notes.isNotBlank()) {
                                            item { Text(recipe.notes, color = Color(0xFF292218).copy(alpha = 0.78f), fontSize = 18.sp, lineHeight = 27.sp) }
                                        }
                                        item {
                                            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                                DetailTinyFact("Prep", recipe.prepTime)
                                                DetailTinyFact("Cook", recipe.cookTime)
                                                DetailTinyFact("Serves", recipe.servings)
                                            }
                                        }
                                        item { FullRecipeSection("Ingredients", recipe.ingredients.ifBlank { "No ingredients provided." }) }
                                        item { FullRecipeSection("Instructions", recipe.instructions.ifBlank { "No instructions provided." }) }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(18.dp)
                                            .background(Color.White.copy(alpha = 0.38f), RoundedCornerShape(28.dp))
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        MinimalActionButton(
                                            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            label = formatCount(recipe.likeCount),
                                            tint = if (liked) Color(0xFFD66A5D) else Color(0xFF4E4A43),
                                            enabled = currentUserId != null,
                                            onClick = onLike
                                        )
                                        MinimalActionButton(Icons.Rounded.Share, "Share", Color(0xFF4E4A43), true, onShare)
                                        MinimalActionButton(Icons.Rounded.BookmarkBorder, "Save", Color(0xFF4E4A43), true, onSave)
                                        if (visibilityActionLabel != null && onVisibilityToggle != null) {
                                            TextButton(onClick = onVisibilityToggle) {
                                                Text(
                                                    visibilityActionLabel,
                                                    color = Color(0xFF4E4A43),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun PhoneCommunityRecipeDetailDialog(
    recipe: CommunityRecipe,
    liked: Boolean,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenProfile: () -> Unit,
    visibilityActionLabel: String?,
    onVisibilityToggle: (() -> Unit)?
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 18.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.44f), CircleShape)
                    ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(22.dp)) }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFFF4EDDE),
                    shadowElevation = 18.dp
                ) {
                    Box {
                        TopBinderHoles(Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 22.dp, end = 22.dp, top = 44.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(238.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                ) {
                                    RecipeHorizontalMediaGallery(
                                        photoUrls = recipe.photoUrls.ifEmpty { listOfNotNull(recipe.thumbnailUrl) },
                                        videoUrls = recipe.videoUrls
                                    )
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                        shape = RoundedCornerShape(28.dp),
                                        color = Color.White.copy(alpha = 0.30f),
                                        shadowElevation = 14.dp,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.42f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            MinimalActionButton(
                                                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                label = formatCount(recipe.likeCount),
                                                tint = if (liked) Color(0xFFD66A5D) else Color.White,
                                                enabled = currentUserId != null,
                                                compact = true,
                                                onClick = onLike
                                            )
                                            MinimalActionButton(Icons.Rounded.Share, "Share", Color.White, true, onShare, compact = true)
                                            MinimalActionButton(Icons.Rounded.BookmarkBorder, "Save", Color.White, true, onSave, compact = true)
                                        }
                                    }
                                }
                            }
                            item {
                                Text(
                                    recipe.title,
                                    color = Color(0xFF292218),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 33.sp,
                                    lineHeight = 37.sp
                                )
                                Row(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .clickable(onClick = onOpenProfile),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProfileAvatar(recipe.ownerProfilePhotoUrl, 30)
                                    Text(
                                        "@${recipe.ownerUsername}",
                                        color = ExploreButter,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (recipe.notes.isNotBlank()) {
                                item { Text(recipe.notes, color = Color(0xFF292218).copy(alpha = 0.78f), fontSize = 16.sp, lineHeight = 23.sp) }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    DetailTinyFact("Prep", recipe.prepTime)
                                    DetailTinyFact("Cook", recipe.cookTime)
                                    DetailTinyFact("Serves", recipe.servings)
                                }
                            }
                            item { FullRecipeSection("Ingredients", recipe.ingredients.ifBlank { "No ingredients provided." }) }
                            item { FullRecipeSection("Instructions", recipe.instructions.ifBlank { "No instructions provided." }) }
                            if (visibilityActionLabel != null && onVisibilityToggle != null) {
                                item {
                                    Button(
                                        onClick = onVisibilityToggle,
                                        colors = ButtonDefaults.buttonColors(containerColor = ExploreOlive),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(visibilityActionLabel, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PublicProfileScreen(
    profile: PublicProfile?,
    recipes: List<CommunityRecipe>,
    privateRecipes: List<Recipe> = emptyList(),
    currentUserId: String?,
    likedRecipeIds: Set<String>,
    subscribedCreatorIds: Set<String>,
    onBack: () -> Unit,
    onSubscribe: (PublicProfile) -> Unit,
    onUnsubscribe: (PublicProfile) -> Unit,
    onLike: (CommunityRecipe) -> Unit,
    onSave: (CommunityRecipe) -> Unit,
    onShare: (CommunityRecipe) -> Unit,
    onLocalRecipeVisibilityChanged: (Recipe, RecipeVisibility) -> Unit,
    onPublicRecipeVisibilityChanged: (CommunityRecipe, RecipeVisibility) -> Unit
) {
    var selectedRecipe by remember { mutableStateOf<CommunityRecipe?>(null) }
    var selectedPrivateRecipe by remember { mutableStateOf<Recipe?>(null) }
    var tab by remember(profile?.userId) { mutableStateOf("Public") }
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    val isOwnProfile = profile?.userId == currentUserId
    Box(
        Modifier
            .fillMaxSize()
            .background(ExploreNavy)
            .padding(
                start = if (isPhone) 18.dp else 28.dp,
                end = if (isPhone) 18.dp else 28.dp,
                top = if (isPhone) 54.dp else 28.dp,
                bottom = if (isPhone) 18.dp else 28.dp
            )
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (isPhone) 16.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(if (isPhone) 48.dp else 56.dp).background(Color.Black.copy(alpha = 0.42f), CircleShape)
                ) { Icon(Icons.Rounded.Close, "Back", tint = ExploreCream) }
                Text(
                    if (isOwnProfile) "My Profile" else "Profile",
                    color = ExploreCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isPhone) 27.sp else 32.sp,
                    modifier = Modifier.padding(start = 18.dp)
                )
            }
            if (profile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading profile…", color = ExploreCream, fontSize = 20.sp)
                }
            } else {
                Surface(shape = RoundedCornerShape(28.dp), color = ExploreCream, modifier = Modifier.fillMaxWidth()) {
                    if (isPhone) {
                        Column(
                            Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ProfileAvatar(profile.profilePhotoUrl, 92)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        profile.displayName,
                                        color = Color(0xFF211F1B),
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 34.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("@${profile.username}", color = ExploreButter, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(profile.bio, color = Color(0xFF211F1B).copy(alpha = 0.66f), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                ProfileStat(profile.subscriberCount, "Subscribers")
                                ProfileStat(profile.recipeCount, "Recipes")
                                ProfileStat(profile.publicRecipeCount, "Public")
                            }
                            if (!isOwnProfile) {
                                val subscribed = subscribedCreatorIds.contains(profile.userId)
                                Button(
                                    onClick = {
                                        if (subscribed) onUnsubscribe(profile) else onSubscribe(profile)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExploreOlive),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text(if (subscribed) "Unsubscribe" else "Subscribe", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            ProfileAvatar(profile.profilePhotoUrl, 120)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(profile.displayName, color = Color(0xFF211F1B), fontFamily = FontFamily.Serif, fontSize = 38.sp)
                                Text("@${profile.username}", color = ExploreButter, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(profile.bio, color = Color(0xFF211F1B).copy(alpha = 0.66f), fontSize = 17.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                ProfileStat(profile.subscriberCount, "Subscribers")
                                ProfileStat(profile.recipeCount, "Recipes")
                                ProfileStat(profile.publicRecipeCount, "Public")
                            }
                            if (!isOwnProfile) {
                                val subscribed = subscribedCreatorIds.contains(profile.userId)
                                Button(
                                    onClick = {
                                        if (subscribed) onUnsubscribe(profile) else onSubscribe(profile)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExploreOlive),
                                    modifier = Modifier.heightIn(min = 54.dp)
                                ) {
                                    Text(if (subscribed) "Unsubscribe" else "Subscribe", color = Color.White)
                                }
                            }
                        }
                    }
                }
                if (isOwnProfile) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProfileTab("Public", tab == "Public") { tab = "Public" }
                        ProfileTab("Private", tab == "Private") { tab = "Private" }
                    }
                }
                LazyVerticalGrid(
                    columns = if (isPhone) GridCells.Fixed(3) else GridCells.Adaptive(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isOwnProfile || tab == "Public") {
                        items(recipes, key = { it.id }) { recipe ->
                            Card(
                                onClick = { selectedRecipe = recipe },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.32f))
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    RecipeRemoteImage(recipe.thumbnailUrl)
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
                                }
                            }
                        }
                    } else {
                        items(privateRecipes, key = { it.id }) { recipe ->
                        Card(
                            onClick = { selectedPrivateRecipe = recipe },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.32f))
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                RecipeRemoteImage(recipe.photoUri ?: recipe.imageUrl)
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
                                Text(
                                    recipe.title,
                                    color = ExploreCream,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }
    selectedRecipe?.let { selected ->
        val recipe = recipes.firstOrNull { it.id == selected.id } ?: selected
        CommunityRecipeDetailDialog(
            recipe = recipe,
            liked = likedRecipeIds.contains(recipe.id),
            currentUserId = currentUserId,
            onDismiss = { selectedRecipe = null },
            onLike = { onLike(recipe) },
            onSave = { onSave(recipe) },
            onShare = { onShare(recipe) },
            onOpenProfile = {},
            visibilityActionLabel = if (isOwnProfile && recipe.localRecipeId != null) "Make Private" else null,
            onVisibilityToggle = if (isOwnProfile && recipe.localRecipeId != null) {
                {
                    onPublicRecipeVisibilityChanged(recipe, RecipeVisibility.Private)
                    selectedRecipe = null
                }
            } else {
                null
            }
        )
    }
    selectedPrivateRecipe?.let { selected ->
        val recipe = privateRecipes.firstOrNull { it.id == selected.id } ?: selected
        LocalRecipeProfileDialog(
            recipe = recipe,
            onDismiss = { selectedPrivateRecipe = null },
            onVisibilityToggle = {
                onLocalRecipeVisibilityChanged(
                    recipe,
                    if (recipe.visibility == RecipeVisibility.Public) RecipeVisibility.Private else RecipeVisibility.Public
                )
                selectedPrivateRecipe = null
            }
        )
    }
}

@Composable
private fun LocalRecipeProfileDialog(
    recipe: Recipe,
    onDismiss: () -> Unit,
    onVisibilityToggle: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 30.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF49331E), Color(0xE60A0A08)), radius = 1_900f),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(22.dp)
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                        Button(
                            onClick = onVisibilityToggle,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (recipe.visibility == RecipeVisibility.Public) ExploreOlive else ExploreButter
                            )
                        ) {
                            Text(
                                if (recipe.visibility == RecipeVisibility.Public) "Make Private" else "Make Public",
                                color = if (recipe.visibility == RecipeVisibility.Public) Color.White else ExploreNavy,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Box(
                            Modifier
                                .weight(0.9f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(22.dp))
                        ) {
                            RecipeVerticalMediaGallery(
                                photoUrls = recipe.photoUris.ifEmpty {
                                    listOfNotNull(recipe.photoUri ?: recipe.imageUrl)
                                },
                                videoUrls = listOfNotNull(recipe.videoUri)
                            )
                        }
                        Surface(
                            modifier = Modifier.weight(1.18f).fillMaxSize(),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFFF4EDDE),
                            shadowElevation = 18.dp
                        ) {
                            Box {
                                BinderHoles(Modifier.align(Alignment.CenterStart))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 68.dp, end = 44.dp, top = 42.dp, bottom = 42.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    item {
                                        Text(
                                            recipe.title,
                                            color = Color(0xFF292218),
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 44.sp,
                                            lineHeight = 48.sp
                                        )
                                        Text(
                                            if (recipe.visibility == RecipeVisibility.Public) "Public recipe" else "Private recipe",
                                            color = if (recipe.visibility == RecipeVisibility.Public) ExploreOlive else ExploreButter,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                    if (recipe.notes.isNotBlank()) {
                                        item { Text(recipe.notes, color = Color(0xFF292218).copy(alpha = 0.78f), fontSize = 18.sp, lineHeight = 27.sp) }
                                    }
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                            DetailTinyFact("Prep", recipe.prepTime)
                                            DetailTinyFact("Cook", recipe.cookTime)
                                            DetailTinyFact("Serves", recipe.servings)
                                        }
                                    }
                                    item { FullRecipeSection("Ingredients", recipe.ingredients.ifBlank { "No ingredients provided." }) }
                                    item { FullRecipeSection("Instructions", recipe.instructions.ifBlank { "No instructions provided." }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (selected) ExploreButter else Color.White.copy(alpha = 0.16f),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 34.dp)) {
            Text(label, color = if (selected) ExploreNavy else ExploreCream, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(value), color = Color(0xFF211F1B), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFF211F1B).copy(alpha = 0.62f), fontSize = 13.sp)
    }
}

@Composable
private fun BinderHoles(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(88.dp)) {
        repeat(3) {
            Box(
                Modifier.size(18.dp).background(Color(0xFF5A4630), CircleShape)
                    .padding(4.dp).background(Color(0xFF16130F), CircleShape)
            )
        }
    }
}

@Composable
private fun TopBinderHoles(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(46.dp)) {
        repeat(3) {
            Box(
                Modifier.size(18.dp).background(Color(0xFF5A4630), CircleShape)
                    .padding(4.dp).background(Color(0xFF16130F), CircleShape)
            )
        }
    }
}

@Composable
private fun MinimalActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(if (compact) 46.dp else 56.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(if (compact) 21.dp else 25.dp))
            Text(label, color = tint, fontSize = if (compact) 9.sp else 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SearchRecipeDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<CommunityRecipe>,
    onOpen: (CommunityRecipe) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search recipes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(min = 520.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search recipes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                results.forEach { recipe ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(recipe) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))) {
                            RecipeRemoteImage(recipe.thumbnailUrl)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(recipe.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${recipe.ownerUsername}", color = Color.Gray)
                        }
                    }
                }
                if (query.isNotBlank() && results.isEmpty()) {
                    Text("No matching recipes yet.", color = Color.Gray)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun RecipeVerticalMediaGallery(photoUrls: List<String>, videoUrls: List<String> = emptyList()) {
    val cleanPhotos = photoUrls.filter { it.isNotBlank() }.distinct()
    val cleanVideos = videoUrls.filter { it.isNotBlank() }.distinct()
    var selectedVideoUrl by remember { mutableStateOf<String?>(null) }
    if (cleanPhotos.isEmpty() && cleanVideos.isEmpty()) {
        RecipeRemoteImage(null)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(cleanPhotos, key = { it }) { photoUrl ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillParentMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
            ) {
                RecipeRemoteImage(photoUrl)
            }
        }
        items(cleanVideos, key = { "video-$it" }) { videoUrl ->
            VideoPreviewTile(onClick = { selectedVideoUrl = videoUrl })
        }
    }
    selectedVideoUrl?.let { videoUrl ->
        CommunityVideoDialog(
            videoUrl = videoUrl,
            onDismiss = { selectedVideoUrl = null }
        )
    }
}

@Composable
private fun RecipeHorizontalMediaGallery(photoUrls: List<String>, videoUrls: List<String> = emptyList()) {
    val cleanPhotos = photoUrls.filter { it.isNotBlank() }.distinct()
    val cleanVideos = videoUrls.filter { it.isNotBlank() }.distinct()
    var selectedVideoUrl by remember { mutableStateOf<String?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (cleanPhotos.isEmpty() && cleanVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 238.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                RecipeRemoteImage(null)
            }
        }
        cleanPhotos.forEach { photoUrl ->
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 238.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                RecipeRemoteImage(photoUrl)
            }
        }
        cleanVideos.forEach { videoUrl ->
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 238.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                VideoPreviewTile(onClick = { selectedVideoUrl = videoUrl })
            }
        }
    }
    selectedVideoUrl?.let { videoUrl ->
        CommunityVideoDialog(videoUrl = videoUrl, onDismiss = { selectedVideoUrl = null })
    }
}

@Composable
private fun VideoPreviewTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6A5D4D), Color(0xFF221E18), Color.Black)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, "Play video", tint = ExploreButter, modifier = Modifier.size(58.dp))
            }
            Text(
                "Tap to view full video",
                color = ExploreCream,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CommunityVideoDialog(videoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(38.dp)
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                update = { it.player = player },
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
                Icon(Icons.Rounded.Close, contentDescription = "Close video", tint = Color.White)
            }
        }
    }
}

@Composable
private fun RecipeRemoteImage(url: String?) {
    if (url.isNullOrBlank()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF6A5D4D), Color(0xFF221E18), Color.Black))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Restaurant, null, tint = ExploreButter.copy(alpha = 0.72f), modifier = Modifier.size(66.dp))
        }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { RecipeImagePlaceholder() },
            error = { RecipeImagePlaceholder() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RecipeImagePlaceholder() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF6A5D4D), Color(0xFF221E18), Color.Black))
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Restaurant, null, tint = ExploreButter.copy(alpha = 0.72f), modifier = Modifier.size(54.dp))
    }
}

@Composable
private fun DetailTinyFact(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, color = Color(0xFF211F1B).copy(alpha = 0.5f), fontSize = 12.sp)
        Text(value, color = Color(0xFF211F1B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun FullRecipeSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFF211F1B), fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Text(body, color = Color(0xFF211F1B).copy(alpha = 0.88f), fontSize = 17.sp, lineHeight = 26.sp)
    }
}

private fun CommunityRecipe.matchesKeyword(keyword: String): Boolean {
    val clean = keyword.trim()
    if (clean.isBlank()) return true
    return title.contains(clean, ignoreCase = true) ||
        notes.contains(clean, ignoreCase = true) ||
        ingredients.contains(clean, ignoreCase = true) ||
        instructions.contains(clean, ignoreCase = true) ||
        ownerUsername.contains(clean, ignoreCase = true)
}

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}.${(value % 1_000_000) / 100_000}m"
    value >= 10_000 -> "${value / 1_000}k"
    value >= 1_000 -> "${value / 1_000}.${(value % 1_000) / 100}k"
    else -> value.toString()
}
