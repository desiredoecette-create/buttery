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
import androidx.compose.runtime.mutableStateMapOf
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
import com.buttery.app.data.CommunityRecipe
import com.buttery.app.data.GroceryListStore
import com.buttery.app.data.PublicProfile
import com.buttery.app.data.RecipeShare
import com.buttery.app.data.RecipeRepository
import com.buttery.app.data.SocialRepository
import com.buttery.app.data.local.RecipeDatabase
import com.buttery.app.domain.ParsedRecipe
import com.buttery.app.domain.RecipeVisibility
import com.buttery.app.ui.screens.AllAlbumsScreen
import com.buttery.app.ui.screens.AlbumPickerDialog
import com.buttery.app.ui.screens.AlbumRecipesScreen
import com.buttery.app.ui.screens.AmbientSlideshowScreen
import com.buttery.app.ui.screens.ButteryIntroScreen
import com.buttery.app.ui.screens.ContinueRecipeEmptyScreen
import com.buttery.app.ui.screens.EditRecipeScreen
import com.buttery.app.ui.screens.ExploreScreen
import com.buttery.app.ui.screens.FavoritesScreen
import com.buttery.app.ui.screens.GroceryListScreen
import com.buttery.app.ui.screens.ImportRecipeScreen
import com.buttery.app.ui.screens.InboxScreen
import com.buttery.app.ui.screens.LoginScreen
import com.buttery.app.ui.screens.ManualRecipeEntryScreen
import com.buttery.app.ui.screens.NewRecipeScreen
import com.buttery.app.ui.screens.RecipeDetailScreen
import com.buttery.app.ui.screens.RecipeHomeScreen
import com.buttery.app.ui.screens.RecipeReviewScreen
import com.buttery.app.ui.screens.ProfileScreen
import com.buttery.app.ui.screens.ProfileMenuScreen
import com.buttery.app.ui.screens.SettingsScreen
import com.buttery.app.ui.screens.PublicProfileScreen
import com.buttery.app.ui.screens.ShareRecipeDialog
import com.buttery.app.ui.screens.ShareCommunityRecipeDialog
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
    data object ProfileMenu : AppScreen
    data object ProfileSettings : AppScreen
    data object ProfileInbox : AppScreen
    data object Explore : AppScreen
    data class PublicProfileView(val userId: String) : AppScreen
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
fun RecipeTabletApp(layoutMode: ButteryLayoutMode = ButteryLayoutMode.Tablet) {
    val context = LocalContext.current
    val repository = remember {
        val database = RecipeDatabase.getInstance(context)
        RecipeRepository(database.recipeDao(), database.recipeAlbumDao())
    }
    val cookingSessionStore = remember { CookingSessionStore(context.applicationContext) }
    val groceryListStore = remember { GroceryListStore(context.applicationContext) }
    val accountRepository = remember { AccountRepository(context.applicationContext) }
    val socialRepository = remember { SocialRepository(context.applicationContext) }
    val currentUser by accountRepository.currentUser.collectAsState()
    val inbox by accountRepository.inbox.collectAsState()
    val hasInboxNotification by accountRepository.hasInboxNotification.collectAsState()
    val publicRecipes by socialRepository.publicRecipes.collectAsState()
    val likedRecipeIds by socialRepository.likedRecipeIds.collectAsState()
    val subscribedCreatorIds by socialRepository.subscribedCreatorIds.collectAsState()
    val recipes by repository.recipes.collectAsState(initial = emptyList())
    val albums by repository.albums.collectAsState(initial = emptyList())
    var lastCookingSession by remember { mutableStateOf(cookingSessionStore.load()) }
    val lastRecipe = recipes.firstOrNull { it.id == lastCookingSession?.recipeId }
    val scope = rememberCoroutineScope()
    var screen by remember(layoutMode) {
        mutableStateOf<AppScreen>(
            if (layoutMode == ButteryLayoutMode.Phone) AppScreen.Login else AppScreen.Intro
        )
    }
    var ambientReturnScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var recipeToShare by remember { mutableStateOf<com.buttery.app.domain.Recipe?>(null) }
    var communityRecipeToShare by remember { mutableStateOf<CommunityRecipe?>(null) }
    var communityRecipeToSave by remember { mutableStateOf<CommunityRecipe?>(null) }
    var loadedPublicProfile by remember { mutableStateOf<PublicProfile?>(null) }
    var loadedPublicProfileRecipes by remember { mutableStateOf<List<CommunityRecipe>>(emptyList()) }
    val subscriberCountOverrides = remember { mutableStateMapOf<String, Int>() }
    var publicProfileBackScreen by remember { mutableStateOf<AppScreen>(AppScreen.ProfileMenu) }
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
            runCatching { socialRepository.refresh(currentUser?.userId) }
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
                    layoutMode = layoutMode,
                    onSignUp = accountRepository::signUp,
                    onSignIn = accountRepository::signIn,
                    onGoogleSignIn = {
                        accountRepository.signInWithGoogle(context as Activity)
                    },
                    onCompleteGoogleSignUp = accountRepository::completeGoogleSignUp
                )
                AppScreen.Ambient -> AmbientSlideshowScreen()
                AppScreen.Home -> RecipeHomeScreen(
                    layoutMode = layoutMode,
                    lastRecipe = lastRecipe,
                    lastRecipeAlbumName = albums.firstOrNull { it.id == lastRecipe?.albumId }?.name,
                    lastOpenedTimestamp = lastCookingSession?.lastOpenedTimestamp,
                    profilePhotoUri = currentUser?.profilePhotoUri,
                    hasProfileNotification = hasInboxNotification,
                    onProfile = {
                        accountRepository.refreshInbox()
                        accountRepository.acknowledgeInboxNotification()
                        screen = AppScreen.ProfileMenu
                    },
                    onTileSelected = { tile ->
                        lastInteractionAt = System.currentTimeMillis()
                        when (tile) {
                            "My Recipes" -> screen = AppScreen.AllAlbums
                            "Explore Recipes" -> {
                                scope.launch { runCatching { socialRepository.refresh(currentUser?.userId) } }
                                screen = AppScreen.Explore
                            }
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
                        }
                    }
                )
                AppScreen.Explore -> ExploreScreen(
                    recipes = publicRecipes,
                    likedRecipeIds = likedRecipeIds,
                    subscribedCreatorIds = subscribedCreatorIds,
                    currentUserId = currentUser?.userId,
                    onHome = { screen = AppScreen.Home },
                    onOpenProfile = { userId ->
                        loadedPublicProfile = null
                        loadedPublicProfileRecipes = emptyList()
                        publicProfileBackScreen = AppScreen.Explore
                        screen = AppScreen.PublicProfileView(userId)
                    },
                    onLike = { recipe ->
                        currentUser?.let { user ->
                            scope.launch { runCatching { socialRepository.toggleLike(recipe, user.userId) } }
                        }
                    },
                    onSave = { communityRecipeToSave = it },
                    onShare = { communityRecipeToShare = it }
                )
                AppScreen.ProfileMenu -> currentUser?.let { profile ->
                    ProfileMenuScreen(
                        profile = profile,
                        hasInboxNotification = hasInboxNotification,
                        onBack = { screen = AppScreen.Home },
                        onOpenMyProfile = {
                            loadedPublicProfile = null
                            loadedPublicProfileRecipes = emptyList()
                            publicProfileBackScreen = AppScreen.ProfileMenu
                            screen = AppScreen.PublicProfileView(profile.userId)
                        },
                        onOpenInbox = {
                            accountRepository.refreshInbox()
                            accountRepository.acknowledgeInboxNotification()
                            screen = AppScreen.ProfileInbox
                        },
                        onOpenSettings = { screen = AppScreen.ProfileSettings },
                        onSignOut = {
                            accountRepository.signOut()
                            screen = AppScreen.Login
                        }
                    )
                } ?: run {
                    screen = AppScreen.Login
                }
                is AppScreen.PublicProfileView -> {
                    LaunchedEffect(current.userId, publicRecipes, subscribedCreatorIds) {
                        runCatching {
                            val fetchedProfile = socialRepository.loadProfile(current.userId)
                            val previousCount = loadedPublicProfile
                                ?.takeIf { it.userId == current.userId }
                                ?.subscriberCount
                                ?: 0
                            loadedPublicProfile = fetchedProfile?.let { profile ->
                                val overrideCount = subscriberCountOverrides[profile.userId]
                                when {
                                    overrideCount != null -> profile.copy(subscriberCount = overrideCount)
                                    subscribedCreatorIds.contains(profile.userId) ->
                                        profile.copy(subscriberCount = maxOf(profile.subscriberCount, previousCount, 1))
                                    else -> profile
                                }
                            }
                            loadedPublicProfileRecipes = socialRepository.publicRecipesForOwner(current.userId)
                        }.onFailure {
                            val ownerRecipes = publicRecipes.filter { it.ownerId == current.userId }
                            loadedPublicProfileRecipes = ownerRecipes
                            val ownUser = currentUser?.takeIf { it.userId == current.userId }
                            val firstRecipe = ownerRecipes.firstOrNull()
                            loadedPublicProfile = when {
                                ownUser != null -> PublicProfile(
                                    userId = ownUser.userId,
                                    username = ownUser.username,
                                    displayName = ownUser.displayName,
                                    profilePhotoUrl = ownUser.profilePhotoUri,
                                    bio = "A warm little recipe book from @${ownUser.username}.",
                                    subscriberCount = 0,
                                    recipeCount = ownerRecipes.size,
                                    publicRecipeCount = ownerRecipes.size
                                )
                                firstRecipe != null -> PublicProfile(
                                    userId = firstRecipe.ownerId,
                                    username = firstRecipe.ownerUsername,
                                    displayName = firstRecipe.ownerDisplayName,
                                    profilePhotoUrl = firstRecipe.ownerProfilePhotoUrl,
                                    bio = "A warm little recipe book from @${firstRecipe.ownerUsername}.",
                                    subscriberCount = 0,
                                    recipeCount = ownerRecipes.size,
                                    publicRecipeCount = ownerRecipes.size
                                )
                                else -> null
                            }
                        }
                    }
                    val displayedPublicProfile = loadedPublicProfile?.let { profile ->
                        subscriberCountOverrides[profile.userId]?.let { overrideCount ->
                            profile.copy(subscriberCount = overrideCount)
                        } ?: profile
                    }
                    PublicProfileScreen(
                        profile = displayedPublicProfile,
                        recipes = if (current.userId == currentUser?.userId) {
                            val localRecipeIds = recipes.map { it.id }.toSet()
                            loadedPublicProfileRecipes.filter { it.localRecipeId in localRecipeIds }
                        } else {
                            loadedPublicProfileRecipes
                        },
                        privateRecipes = if (current.userId == currentUser?.userId) {
                            recipes.filter { it.visibility == RecipeVisibility.Private }
                        } else {
                            emptyList()
                        },
                        currentUserId = currentUser?.userId,
                        likedRecipeIds = likedRecipeIds,
                        subscribedCreatorIds = subscribedCreatorIds,
                        onBack = { screen = publicProfileBackScreen },
                        onSubscribe = { profile ->
                            currentUser?.let { user ->
                                val previousProfile = loadedPublicProfile
                                val previousOverride = subscriberCountOverrides[profile.userId]
                                val currentProfile = previousProfile
                                    ?.takeIf { it.userId == profile.userId }
                                    ?: profile
                                val newCount = currentProfile.subscriberCount + 1
                                subscriberCountOverrides[profile.userId] = newCount
                                loadedPublicProfile = currentProfile.copy(subscriberCount = newCount)
                                scope.launch {
                                    val result = runCatching {
                                        socialRepository.subscribe(user, profile)
                                    }
                                    if (result.isFailure) {
                                        if (previousOverride == null) {
                                            subscriberCountOverrides.remove(profile.userId)
                                        } else {
                                            subscriberCountOverrides[profile.userId] = previousOverride
                                        }
                                        loadedPublicProfile = previousProfile ?: profile
                                    }
                                }
                            }
                        },
                        onUnsubscribe = { profile ->
                            currentUser?.let { user ->
                                val previousProfile = loadedPublicProfile
                                val previousOverride = subscriberCountOverrides[profile.userId]
                                val currentProfile = previousProfile
                                    ?.takeIf { it.userId == profile.userId }
                                    ?: profile
                                val newCount = maxOf(0, currentProfile.subscriberCount - 1)
                                subscriberCountOverrides[profile.userId] = newCount
                                loadedPublicProfile = currentProfile.copy(subscriberCount = newCount)
                                scope.launch {
                                    val result = runCatching {
                                        socialRepository.unsubscribe(user.userId, profile.userId)
                                    }
                                    if (result.isFailure) {
                                        if (previousOverride == null) {
                                            subscriberCountOverrides.remove(profile.userId)
                                        } else {
                                            subscriberCountOverrides[profile.userId] = previousOverride
                                        }
                                        loadedPublicProfile = previousProfile ?: profile
                                    }
                                }
                            }
                        },
                        onLike = { recipe ->
                            currentUser?.let { user ->
                                scope.launch { runCatching { socialRepository.toggleLike(recipe, user.userId) } }
                            }
                        },
                        onSave = { communityRecipeToSave = it },
                        onShare = { communityRecipeToShare = it },
                        onLocalRecipeVisibilityChanged = { recipe, visibility ->
                            currentUser?.let { profile ->
                                scope.launch {
                                    val updatedRecipe = repository.updateVisibility(recipe.id, visibility)
                                    if (updatedRecipe != null) {
                                        if (visibility == RecipeVisibility.Public) {
                                            socialRepository.publish(updatedRecipe, profile)
                                        } else {
                                            runCatching { socialRepository.unpublish(profile.userId, updatedRecipe.id) }
                                        }
                                    }
                                }
                            }
                        },
                        onPublicRecipeVisibilityChanged = { recipe, visibility ->
                            currentUser?.let { profile ->
                                val localRecipeId = recipe.localRecipeId
                                if (localRecipeId != null) {
                                    scope.launch {
                                        val updatedRecipe = repository.updateVisibility(localRecipeId, visibility)
                                        if (updatedRecipe != null) {
                                            if (visibility == RecipeVisibility.Public) {
                                                socialRepository.publish(updatedRecipe, profile)
                                            } else {
                                                runCatching { socialRepository.unpublish(profile.userId, updatedRecipe.id) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                AppScreen.ProfileInbox -> currentUser?.let {
                    InboxScreen(
                        inbox = inbox,
                        onBack = { screen = AppScreen.ProfileMenu },
                        onViewShare = { share ->
                            scope.launch {
                                accountRepository.markShare(
                                    share.shareId,
                                    if (share.status == AccountRepository.STATUS_PENDING) {
                                        AccountRepository.STATUS_VIEWED
                                    } else {
                                        share.status
                                    }
                                )
                            }
                            screen = AppScreen.SharedRecipePreview(share)
                        },
                        onAddShare = { shareToAdd = it },
                        onDismissShare = {
                            scope.launch {
                                accountRepository.markShare(it.shareId, AccountRepository.STATUS_DISMISSED)
                            }
                        },
                        onOpenPublicProfile = { userId ->
                            loadedPublicProfile = null
                            loadedPublicProfileRecipes = emptyList()
                            publicProfileBackScreen = AppScreen.ProfileInbox
                            screen = AppScreen.PublicProfileView(userId)
                        }
                    )
                } ?: LoginScreen(
                    layoutMode = layoutMode,
                    onSignUp = accountRepository::signUp,
                    onSignIn = accountRepository::signIn,
                    onGoogleSignIn = {
                        accountRepository.signInWithGoogle(context as Activity)
                    },
                    onCompleteGoogleSignUp = accountRepository::completeGoogleSignUp
                )
                AppScreen.ProfileSettings -> currentUser?.let { profile ->
                    SettingsScreen(
                        profile = profile,
                        onBack = { screen = AppScreen.ProfileMenu },
                        onOpenPublicProfile = { userId ->
                            loadedPublicProfile = null
                            loadedPublicProfileRecipes = emptyList()
                            publicProfileBackScreen = AppScreen.ProfileSettings
                            screen = AppScreen.PublicProfileView(userId)
                        },
                        onSaveProfile = accountRepository::updateProfile,
                        onSignOut = {
                            accountRepository.signOut()
                            screen = AppScreen.Login
                        }
                    )
                } ?: LoginScreen(
                    layoutMode = layoutMode,
                    onSignUp = accountRepository::signUp,
                    onSignIn = accountRepository::signIn,
                    onGoogleSignIn = {
                        accountRepository.signInWithGoogle(context as Activity)
                    },
                    onCompleteGoogleSignUp = accountRepository::completeGoogleSignUp
                )
                is AppScreen.SharedRecipePreview -> SharedRecipePreviewScreen(
                    share = current.share,
                    senderProfile = null,
                    onBack = { screen = AppScreen.ProfileInbox },
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
                            currentUser?.let { profile ->
                                runCatching { socialRepository.unpublish(profile.userId, id) }
                            }
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
                    onSave = { recipe, albumId, photoUri, photoUris, videoUri, visibility ->
                        val savedId = repository.saveRecipe(
                            recipe = recipe,
                            albumId = albumId,
                            photoUri = photoUri,
                            videoUri = videoUri,
                            photoUris = photoUris,
                            visibility = visibility
                        )
                        if (visibility == RecipeVisibility.Public) {
                            val profile = currentUser
                            val savedRecipe = repository.getRecipe(savedId)
                            if (profile != null && savedRecipe != null) {
                                socialRepository.publish(savedRecipe, profile)
                            }
                        }
                        savedId
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
                    onSave = { recipe, albumId, photoUri, photoUris, videoUri, visibility ->
                        val savedId = repository.saveRecipe(
                            recipe = recipe,
                            albumId = albumId,
                            photoUri = photoUri,
                            videoUri = videoUri,
                            photoUris = photoUris,
                            visibility = visibility
                        )
                        if (visibility == RecipeVisibility.Public) {
                            val profile = currentUser
                            val savedRecipe = repository.getRecipe(savedId)
                            if (profile != null && savedRecipe != null) {
                                socialRepository.publish(savedRecipe, profile)
                            }
                        }
                        savedId
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
                        onVisibilityChanged = { visibility ->
                            scope.launch {
                                val updatedRecipe = repository.updateVisibility(current.recipeId, visibility)
                                val profile = currentUser
                                if (profile != null && updatedRecipe != null) {
                                    if (visibility == RecipeVisibility.Public) {
                                        socialRepository.publish(updatedRecipe, profile)
                                    } else {
                                        runCatching { socialRepository.unpublish(profile.userId, updatedRecipe.id) }
                                    }
                                }
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
                            onSave = { parsed, albumId, photoUri, photoUris, videoUri, favorite, visibility ->
                                repository.updateRecipe(
                                    recipeId = current.recipeId,
                                    recipe = parsed,
                                    albumId = albumId,
                                    photoUri = photoUri,
                                    videoUri = videoUri,
                                    photoUris = photoUris,
                                    isFavorite = favorite,
                                    visibility = visibility
                                )
                                val profile = currentUser
                                val updatedRecipe = repository.getRecipe(current.recipeId)
                                if (profile != null && updatedRecipe != null) {
                                    if (visibility == RecipeVisibility.Public) {
                                        socialRepository.publish(updatedRecipe, profile)
                                    } else {
                                        runCatching { socialRepository.unpublish(profile.userId, updatedRecipe.id) }
                                    }
                                }
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
                    matchingUsernames = accountRepository::matchingUsernames,
                    onShare = { recipient -> accountRepository.shareRecipe(recipe, recipient) },
                    onDismiss = { recipeToShare = null }
                )
            }
            communityRecipeToShare?.let { recipe ->
                ShareCommunityRecipeDialog(
                    recipe = recipe,
                    findUser = accountRepository::findUser,
                    matchingUsernames = accountRepository::matchingUsernames,
                    onShare = { recipient -> accountRepository.shareCommunityRecipe(recipe, recipient) },
                    onDismiss = { communityRecipeToShare = null }
                )
            }
            communityRecipeToSave?.let { recipe ->
                AlbumPickerDialog(
                    albums = albums,
                    onChoose = { albumId ->
                        scope.launch {
                            repository.saveRecipe(
                                recipe = ParsedRecipe(
                                    title = recipe.title,
                                    ingredients = recipe.ingredients,
                                    instructions = recipe.instructions,
                                    prepTime = recipe.prepTime,
                                    cookTime = recipe.cookTime,
                                    totalTime = recipe.totalTime,
                                    servings = recipe.servings,
                                    notes = recipe.notes,
                                    imageUrl = recipe.thumbnailUrl,
                                    sourceUrl = recipe.sourceUrl,
                                    originalRawText = "Originally by @${recipe.ownerUsername}"
                                ),
                                albumId = albumId,
                                photoUri = recipe.thumbnailUrl,
                                videoUri = recipe.videoUrls.firstOrNull(),
                                photoUris = recipe.photoUrls
                            )
                            communityRecipeToSave = null
                        }
                    },
                    onCreate = repository::createAlbum,
                    onDismiss = { communityRecipeToSave = null }
                )
            }
            shareToAdd?.let { share ->
                AlbumPickerDialog(
                    albums = albums,
                    onChoose = { albumId ->
                        scope.launch {
                            runCatching {
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
                            }
                            shareToAdd = null
                            screen = AppScreen.ProfileInbox
                        }
                    },
                    onCreate = repository::createAlbum,
                    onDismiss = { shareToAdd = null }
                )
            }
        }
    }
}
