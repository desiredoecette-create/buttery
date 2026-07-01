package com.buttery.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.buttery.app.data.CookingSessionStore
import com.buttery.app.data.AccountRepository
import com.buttery.app.data.GroceryListStore
import com.buttery.app.data.RecipeShare
import com.buttery.app.data.RecipeRepository
import com.buttery.app.data.local.RecipeDatabase
import com.buttery.app.domain.ParsedRecipe
import com.buttery.app.ui.screens.AllAlbumsScreen
import com.buttery.app.ui.screens.AlbumPickerDialog
import com.buttery.app.ui.screens.AlbumRecipesScreen
import com.buttery.app.ui.screens.AmbientSlideshowScreen
import com.buttery.app.ui.screens.ButteryIntroScreen
import com.buttery.app.ui.screens.ContinueRecipeEmptyScreen
import com.buttery.app.ui.screens.EditRecipeScreen
import com.buttery.app.ui.screens.FavoritesScreen
import com.buttery.app.ui.screens.GroceryListScreen
import com.buttery.app.ui.screens.ImportRecipeScreen
import com.buttery.app.ui.screens.LoginScreen
import com.buttery.app.ui.screens.ManualRecipeEntryScreen
import com.buttery.app.ui.screens.NewRecipeScreen
import com.buttery.app.ui.screens.RecipeDetailScreen
import com.buttery.app.ui.screens.RecipeHomeScreen
import com.buttery.app.ui.screens.RecipeReviewScreen
import com.buttery.app.ui.screens.ProfileScreen
import com.buttery.app.ui.screens.ShareRecipeDialog
import com.buttery.app.ui.screens.SharedRecipePreviewScreen
import com.buttery.app.ui.theme.RecipeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private sealed interface AppScreen {
    data object Intro : AppScreen
    data object Ambient : AppScreen
    data object Login : AppScreen
    data object Home : AppScreen
    data object Profile : AppScreen
    data class SharedRecipePreview(val share: RecipeShare) : AppScreen
    data object ContinueEmpty : AppScreen
    data object Favorites : AppScreen
    data object GroceryList : AppScreen
    data object AllAlbums : AppScreen
    data class AlbumRecipes(val albumId: Long?) : AppScreen
    data object NewRecipe : AppScreen
    data object ManualEntry : AppScreen
    data object ImportRecipe : AppScreen
    data class RecipeReview(val recipe: ParsedRecipe, val photoUri: String?) : AppScreen
    data class RecipeDetail(val recipeId: Long, val returnTarget: RecipeReturnTarget) : AppScreen
    data class EditRecipe(val recipeId: Long, val fromAlbumId: Long?) : AppScreen
}

private sealed interface RecipeReturnTarget {
    data class Album(val albumId: Long?) : RecipeReturnTarget
    data object Favorites : RecipeReturnTarget
}

private val HomeInactivityTimeout = 15.seconds
private val GroceryInactivityTimeout = 60.seconds

@Composable
fun RecipeTabletApp() {
    val context = LocalContext.current
    val repository = remember {
        val database = RecipeDatabase.getInstance(context)
        RecipeRepository(database.recipeDao(), database.recipeAlbumDao())
    }
    val cookingSessionStore = remember { CookingSessionStore(context.applicationContext) }
    val groceryListStore = remember { GroceryListStore(context.applicationContext) }
    val accountRepository = remember { AccountRepository(context.applicationContext) }
    val currentUser by accountRepository.currentUser.collectAsState()
    val inbox by accountRepository.inbox.collectAsState()
    val hasInboxNotification by accountRepository.hasInboxNotification.collectAsState()
    val recipes by repository.recipes.collectAsState(initial = emptyList())
    val albums by repository.albums.collectAsState(initial = emptyList())
    var lastCookingSession by remember { mutableStateOf(cookingSessionStore.load()) }
    val lastRecipe = recipes.firstOrNull { it.id == lastCookingSession?.recipeId }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Intro) }
    var ambientReturnScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var recipeToShare by remember { mutableStateOf<com.buttery.app.domain.Recipe?>(null) }
    var shareToAdd by remember { mutableStateOf<RecipeShare?>(null) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isEligibleForAmbientMode =
        screen == AppScreen.Home ||
            screen == AppScreen.Favorites ||
            screen == AppScreen.GroceryList
    val keepScreenAwake = true
    val view = LocalView.current

    DisposableEffect(view, keepScreenAwake) {
        view.keepScreenOn = keepScreenAwake
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(currentUser?.userId) {
        repository.setActiveOwner(currentUser?.userId)
        if (currentUser != null) {
        }
    }

    LaunchedEffect(currentUser, screen) {
        if (screen == AppScreen.Login && currentUser != null) {
            lastInteractionAt = System.currentTimeMillis()
            screen = AppScreen.Home
        }
    }

    LaunchedEffect(screen, lastInteractionAt) {
        if (!isEligibleForAmbientMode) return@LaunchedEffect

        val inactivityTimeout = if (screen == AppScreen.GroceryList) {
            GroceryInactivityTimeout
        } else {
            HomeInactivityTimeout
        }
        delay(inactivityTimeout)
        if (System.currentTimeMillis() - lastInteractionAt >= inactivityTimeout.inWholeMilliseconds) {
            ambientReturnScreen = screen
            screen = AppScreen.Ambient
        }
    }

    RecipeAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screen) {
                    awaitEachGesture {
                        val event = awaitPointerEvent()
                        if (event.changes.isNotEmpty()) {
                            when (screen) {
                                AppScreen.Ambient -> {
                                    lastInteractionAt = System.currentTimeMillis()
                                    screen = ambientReturnScreen
                                }
                                AppScreen.Home,
                                AppScreen.Favorites,
                                AppScreen.GroceryList -> lastInteractionAt = System.currentTimeMillis()
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    when {
                        targetState is AppScreen.RecipeDetail ->
                            (slideInVertically { it / 3 } + fadeIn() + scaleIn(initialScale = 0.96f))
                                .togetherWith(fadeOut() + scaleOut(targetScale = 0.98f))
                        initialState is AppScreen.RecipeDetail ->
                            (fadeIn() + scaleIn(initialScale = 0.98f))
                                .togetherWith(slideOutVertically { it / 3 } + fadeOut())
                        targetState is AppScreen.AlbumRecipes ->
                            (fadeIn() + scaleIn(initialScale = 0.94f))
                                .togetherWith(fadeOut() + scaleOut(targetScale = 1.04f))
                        initialState is AppScreen.AlbumRecipes ->
                            (fadeIn() + scaleIn(initialScale = 1.04f))
                                .togetherWith(fadeOut() + scaleOut(targetScale = 0.94f))
                        else -> fadeIn().togetherWith(fadeOut())
                    }
                },
                label = "recipe_navigation"
            ) { current ->
                when (current) {
                AppScreen.Intro -> ButteryIntroScreen(
                    onFinished = {
                        lastInteractionAt = System.currentTimeMillis()
                        screen = if (currentUser == null) AppScreen.Login else AppScreen.Home
                    }
                )
                AppScreen.Login -> LoginScreen(
                    onSignUp = accountRepository::signUp,
                    onSignIn = accountRepository::signIn,
                    onGoogleSignIn = {
                        accountRepository.signInWithGoogle(context as Activity)
                    },
                    onCompleteGoogleSignUp = accountRepository::completeGoogleSignUp
                )
                AppScreen.Ambient -> AmbientSlideshowScreen()
                AppScreen.Home -> RecipeHomeScreen(
                    lastRecipe = lastRecipe,
                    lastRecipeAlbumName = albums.firstOrNull { it.id == lastRecipe?.albumId }?.name,
                    lastOpenedTimestamp = lastCookingSession?.lastOpenedTimestamp,
                    profilePhotoUri = currentUser?.profilePhotoUri,
                    hasProfileNotification = hasInboxNotification,
                    onProfile = {
                        accountRepository.refreshInbox()
                        accountRepository.acknowledgeInboxNotification()
                        screen = AppScreen.Profile
                    },
                    onTileSelected = { tile ->
                        lastInteractionAt = System.currentTimeMillis()
                        when (tile) {
                            "Browse Recipes" -> screen = AppScreen.AllAlbums
                            "Enter New Recipe" -> screen = AppScreen.NewRecipe
                            "Continue Recipe" -> {
                                screen = if (lastRecipe == null) {
                                    AppScreen.ContinueEmpty
                                } else {
                                    AppScreen.RecipeDetail(
                                        lastRecipe.id,
                                        RecipeReturnTarget.Album(lastRecipe.albumId)
                                    )
                                }
                            }
                            "Favorites" -> {
                                lastInteractionAt = System.currentTimeMillis()
                                screen = AppScreen.Favorites
                            }
                            "Grocery List" -> {
                                lastInteractionAt = System.currentTimeMillis()
                                screen = AppScreen.GroceryList
                            }
                            "Settings" -> {
                                accountRepository.refreshInbox()
                                accountRepository.acknowledgeInboxNotification()
                                screen = AppScreen.Profile
                            }
                        }
                    }
                )
                AppScreen.Profile -> currentUser?.let { profile ->
                    ProfileScreen(
                        profile = profile,
                        inbox = inbox,
                        onBack = { screen = AppScreen.Home },
                        onSaveProfile = accountRepository::updateProfile,
                        onSignOut = {
                            accountRepository.signOut()
                            screen = AppScreen.Login
                        },
                        onViewShare = { share ->
                            accountRepository.markShare(
                                share.shareId,
                                if (share.status == AccountRepository.STATUS_PENDING) {
                                    AccountRepository.STATUS_VIEWED
                                } else {
                                    share.status
                                }
                            )
                            screen = AppScreen.SharedRecipePreview(share)
                        },
                        onAddShare = { shareToAdd = it },
                        onDismissShare = {
                            accountRepository.markShare(it.shareId, AccountRepository.STATUS_DISMISSED)
                        }
                    )
                } ?: LoginScreen(
                    onSignUp = accountRepository::signUp,
                    onSignIn = accountRepository::signIn,
                    onGoogleSignIn = {
                        accountRepository.signInWithGoogle(context as Activity)
                    },
                    onCompleteGoogleSignUp = accountRepository::completeGoogleSignUp
                )
                is AppScreen.SharedRecipePreview -> SharedRecipePreviewScreen(
                    share = current.share,
                    senderProfile = accountRepository.findUser(current.share.fromUsername),
                    onBack = { screen = AppScreen.Profile },
                    onAdd = { shareToAdd = current.share }
                )
                AppScreen.Favorites -> FavoritesScreen(
                    recipes = recipes,
                    albums = albums,
                    onHome = { screen = AppScreen.Home },
                    onBrowseRecipes = { screen = AppScreen.AllAlbums },
                    onRecipeSelected = {
                        screen = AppScreen.RecipeDetail(it, RecipeReturnTarget.Favorites)
                    },
                    onFavoriteChanged = { id, favorite ->
                        scope.launch { repository.setFavorite(id, favorite) }
                    }
                )
                AppScreen.GroceryList -> GroceryListScreen(
                    store = groceryListStore,
                    onHome = {
                        lastInteractionAt = System.currentTimeMillis()
                        screen = AppScreen.Home
                    }
                )
                AppScreen.ContinueEmpty -> ContinueRecipeEmptyScreen(
                    onBrowseRecipes = { screen = AppScreen.AllAlbums },
                    onAddRecipe = { screen = AppScreen.NewRecipe }
                )
                AppScreen.AllAlbums -> AllAlbumsScreen(
                    recipes = recipes,
                    albums = albums,
                    onHome = { screen = AppScreen.Home },
                    onAddRecipe = { screen = AppScreen.NewRecipe },
                    onAlbumSelected = { screen = AppScreen.AlbumRecipes(it) },
                    onUpdateAlbum = { id, name, coverUri ->
                        scope.launch { repository.updateAlbum(id, name, coverUri) }
                    },
                    onDeleteAlbum = { id ->
                        scope.launch { repository.deleteAlbum(id) }
                    }
                )
                is AppScreen.AlbumRecipes -> AlbumRecipesScreen(
                    albumId = current.albumId,
                    recipes = recipes,
                    albums = albums,
                    onBackToAlbums = { screen = AppScreen.AllAlbums },
                    onHome = { screen = AppScreen.Home },
                    onAddRecipe = { screen = AppScreen.NewRecipe },
                    onRecipeSelected = {
                        screen = AppScreen.RecipeDetail(
                            it,
                            RecipeReturnTarget.Album(current.albumId)
                        )
                    },
                    onFavoriteChanged = { id, favorite ->
                        scope.launch { repository.setFavorite(id, favorite) }
                    },
                    onEditRecipe = {
                        screen = AppScreen.EditRecipe(it, current.albumId)
                    },
                    onDeleteRecipe = { id ->
                        scope.launch {
                            repository.deleteRecipe(id)
                            if (lastCookingSession?.recipeId == id) {
                                cookingSessionStore.clear()
                                lastCookingSession = null
                            }
                        }
                    }
                )
                AppScreen.NewRecipe -> NewRecipeScreen(
                    onBack = { screen = AppScreen.Home },
                    onCreateManually = { screen = AppScreen.ManualEntry },
                    onImportRecipe = { screen = AppScreen.ImportRecipe }
                )
                AppScreen.ManualEntry -> ManualRecipeEntryScreen(
                    albums = albums,
                    onBack = { screen = AppScreen.NewRecipe },
                    onCreateAlbum = repository::createAlbum,
                    onSave = { recipe, albumId, photoUri, photoUris, videoUri ->
                        repository.saveRecipe(recipe, albumId, photoUri, videoUri, photoUris)
                    },
                    onViewRecipe = {
                        screen = AppScreen.RecipeDetail(it, RecipeReturnTarget.Album(null))
                    }
                )
                AppScreen.ImportRecipe -> ImportRecipeScreen(
                    onBack = { screen = AppScreen.NewRecipe },
                    onExtract = { recipe, photoUri ->
                        screen = AppScreen.RecipeReview(
                            recipe = recipe,
                            photoUri = photoUri
                        )
                    }
                )
                is AppScreen.RecipeReview -> RecipeReviewScreen(
                    initialRecipe = current.recipe,
                    initialPhotoUri = current.photoUri,
                    albums = albums,
                    onBack = { screen = AppScreen.ImportRecipe },
                    onCreateAlbum = repository::createAlbum,
                    onSave = { recipe, albumId, photoUri, photoUris, videoUri ->
                        repository.saveRecipe(
                            recipe = recipe,
                            albumId = albumId,
                            photoUri = photoUri,
                            videoUri = videoUri,
                            photoUris = photoUris
                        )
                    },
                    onViewRecipe = {
                        screen = AppScreen.RecipeDetail(it, RecipeReturnTarget.Album(null))
                    }
                )
                is AppScreen.RecipeDetail -> {
                    LaunchedEffect(current.recipeId) {
                        lastCookingSession =
                            cookingSessionStore.recordRecipeOpened(current.recipeId)
                    }
                    val recipe by remember(current.recipeId) {
                        repository.observeRecipe(current.recipeId)
                    }.collectAsState(initial = null)
                    RecipeDetailScreen(
                        recipe = recipe,
                        albumName = albums.firstOrNull { it.id == recipe?.albumId }?.name
                            ?: "All Recipes",
                        backLabel = if (current.returnTarget == RecipeReturnTarget.Favorites) {
                            "Back to Favorites"
                        } else {
                            "Back to Album"
                        },
                        onBackToAlbum = {
                            screen = when (val target = current.returnTarget) {
                                is RecipeReturnTarget.Album ->
                                    AppScreen.AlbumRecipes(target.albumId)
                                RecipeReturnTarget.Favorites -> AppScreen.Favorites
                            }
                        },
                        onHome = { screen = AppScreen.Home },
                        onFavoriteChanged = { favorite ->
                            scope.launch {
                                repository.setFavorite(current.recipeId, favorite)
                            }
                        },
                        onEdit = {
                            screen = AppScreen.EditRecipe(it.id, it.albumId)
                        },
                        onShare = { recipeToShare = it }
                    )
                }
                is AppScreen.EditRecipe -> {
                    val recipe by remember(current.recipeId) {
                        repository.observeRecipe(current.recipeId)
                    }.collectAsState(initial = null)
                    recipe?.let { existing ->
                        EditRecipeScreen(
                            existingRecipe = existing,
                            albums = albums,
                            onBack = { screen = AppScreen.AlbumRecipes(current.fromAlbumId) },
                            onSave = { parsed, albumId, photoUri, photoUris, videoUri, favorite ->
                                repository.updateRecipe(
                                    recipeId = current.recipeId,
                                    recipe = parsed,
                                    albumId = albumId,
                                    photoUri = photoUri,
                                    videoUri = videoUri,
                                    photoUris = photoUris,
                                    isFavorite = favorite
                                )
                            },
                            onSaved = {
                                screen = AppScreen.RecipeDetail(
                                    current.recipeId,
                                    RecipeReturnTarget.Album(current.fromAlbumId)
                                )
                            }
                        )
                    }
                }
            }
            }
            recipeToShare?.let { recipe ->
                ShareRecipeDialog(
                    recipe = recipe,
                    findUser = accountRepository::findUser,
                    onShare = { recipient -> accountRepository.shareRecipe(recipe, recipient) },
                    onDismiss = { recipeToShare = null }
                )
            }
            shareToAdd?.let { share ->
                AlbumPickerDialog(
                    albums = albums,
                    onChoose = { albumId ->
                        scope.launch {
                            val snapshot = share.recipe
                            repository.saveRecipe(
                                recipe = ParsedRecipe(
                                    title = snapshot.title,
                                    ingredients = snapshot.ingredients,
                                    instructions = snapshot.instructions,
                                    prepTime = snapshot.prepTime,
                                    cookTime = snapshot.cookTime,
                                    totalTime = snapshot.totalTime,
                                    servings = snapshot.servings,
                                    notes = snapshot.notes,
                                    imageUrl = snapshot.imageUrl,
                                    sourceUrl = snapshot.sourceUrl,
                                    originalRawText = snapshot.originalRawText
                                ),
                                albumId = albumId,
                                photoUri = snapshot.photoUri,
                                videoUri = snapshot.videoUri,
                                photoUris = snapshot.photoUris
                            )
                            accountRepository.markShare(
                                share.shareId,
                                AccountRepository.STATUS_ADDED
                            )
                            shareToAdd = null
                            screen = AppScreen.Profile
                        }
                    },
                    onCreate = repository::createAlbum,
                    onDismiss = { shareToAdd = null }
                )
            }
        }
    }
}
