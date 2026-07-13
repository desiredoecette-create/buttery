package com.buttery.app.data

import android.content.Context
import android.net.Uri
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeVisibility
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

data class CommunityRecipe(
    val id: String,
    val localRecipeId: Long?,
    val ownerId: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val ownerProfilePhotoUrl: String?,
    val title: String,
    val notes: String,
    val prepTime: String,
    val cookTime: String,
    val totalTime: String,
    val servings: String,
    val ingredients: String,
    val instructions: String,
    val photoUrls: List<String>,
    val videoUrls: List<String>,
    val sourceUrl: String?,
    val likeCount: Int,
    val publicPublishedAt: Long,
    val updatedAt: Long
) {
    val thumbnailUrl: String?
        get() = photoUrls.firstOrNull()
}

data class PublicProfile(
    val userId: String,
    val username: String,
    val displayName: String,
    val profilePhotoUrl: String?,
    val bio: String,
    val subscriberCount: Int,
    val recipeCount: Int,
    val publicRecipeCount: Int
)

class SocialRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _publicRecipes = MutableStateFlow<List<CommunityRecipe>>(emptyList())
    val publicRecipes: StateFlow<List<CommunityRecipe>> = _publicRecipes

    private val _likedRecipeIds = MutableStateFlow<Set<String>>(emptySet())
    val likedRecipeIds: StateFlow<Set<String>> = _likedRecipeIds

    private val _subscribedCreatorIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedCreatorIds: StateFlow<Set<String>> = _subscribedCreatorIds

    suspend fun refresh(currentUserId: String?) {
        refreshPublicRecipes()
        refreshLikes(currentUserId)
        refreshSubscriptions(currentUserId)
    }

    suspend fun refreshPublicRecipes() {
        val profileDataById = database.collection(PUBLIC_PROFILES)
            .get()
            .await()
            .documents
            .associate { it.id to it.data.orEmpty() }
        val snapshot = database.collection(RECIPES)
            .whereEqualTo("visibility", RecipeVisibility.Public.value)
            .get()
            .await()
        _publicRecipes.value = snapshot.documents
            .mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val title = data["title"] as? String ?: return@mapNotNull null
                val ownerId = data["ownerId"] as? String ?: ""
                val profileData = profileDataById[ownerId].orEmpty()
                CommunityRecipe(
                    id = document.id,
                    localRecipeId = (data["localRecipeId"] as? String)?.toLongOrNull()
                        ?: (data["localRecipeId"] as? Number)?.toLong(),
                    ownerId = ownerId,
                    ownerUsername = profileData["username"] as? String ?: data["ownerUsername"] as? String ?: "chef",
                    ownerDisplayName = profileData["displayName"] as? String
                        ?: data["ownerDisplayName"] as? String
                        ?: data["ownerUsername"] as? String
                        ?: "Buttery Chef",
                    ownerProfilePhotoUrl = (profileData["profilePhotoUrl"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (data["ownerProfilePhotoUrl"] as? String)?.takeIf { it.isNotBlank() },
                    title = title,
                    notes = data["notes"] as? String ?: "",
                    prepTime = data["prepTime"] as? String ?: "",
                    cookTime = data["cookTime"] as? String ?: "",
                    totalTime = data["totalTime"] as? String ?: "",
                    servings = data["servings"] as? String ?: "",
                    ingredients = data["ingredients"] as? String ?: "",
                    instructions = data["instructions"] as? String ?: "",
                    photoUrls = (data["photoUrls"] as? List<*>)?.mapNotNull { it as? String }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                        .ifEmpty { listOfNotNull((data["thumbnailUrl"] as? String)?.takeIf { it.isNotBlank() }) },
                    videoUrls = (data["videoUrls"] as? List<*>)?.mapNotNull { it as? String }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                        .ifEmpty { listOfNotNull((data["videoUrl"] as? String)?.takeIf { it.isNotBlank() }) },
                    sourceUrl = (data["sourceUrl"] as? String)?.takeIf { it.isNotBlank() },
                    likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0,
                    publicPublishedAt = (data["publicPublishedAt"] as? Timestamp)?.toDate()?.time
                        ?: (data["publicPublishedAt"] as? Number)?.toLong()
                        ?: 0L,
                    updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time
                        ?: (data["updatedAt"] as? Number)?.toLong()
                        ?: 0L
                )
            }
            .sortedWith(compareByDescending<CommunityRecipe> { it.likeCount }
                .thenByDescending { it.publicPublishedAt.takeIf { value -> value > 0L } ?: it.updatedAt })
    }

    suspend fun refreshLikes(userId: String?) {
        if (userId == null) {
            _likedRecipeIds.value = emptySet()
            return
        }
        val snapshot = database.collection(RECIPE_LIKES)
            .whereEqualTo("userId", userId)
            .get()
            .await()
        _likedRecipeIds.value = snapshot.documents
            .mapNotNull { it.getString("recipeId") }
            .toSet()
    }

    suspend fun refreshSubscriptions(userId: String?) {
        if (userId == null) {
            _subscribedCreatorIds.value = emptySet()
            return
        }
        val snapshot = database.collection(SUBSCRIPTIONS)
            .whereEqualTo("subscriberUserId", userId)
            .get()
            .await()
        _subscribedCreatorIds.value = snapshot.documents
            .mapNotNull { it.getString("creatorUserId") }
            .toSet()
    }

    suspend fun loadProfile(userId: String): PublicProfile? {
        val publicDocument = database.collection(PUBLIC_PROFILES).document(userId).get().await()
        val userDocument = database.collection("users").document(userId).get().await()
        if (!publicDocument.exists() && !userDocument.exists()) return null
        val publicData = publicDocument.data.orEmpty()
        val userData = userDocument.data.orEmpty()
        val publicCount = database.collection(RECIPES)
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("visibility", RecipeVisibility.Public.value)
            .get()
            .await()
            .size()
        val subscriberCount = database.collection(SUBSCRIPTIONS)
            .whereEqualTo("creatorUserId", userId)
            .get()
            .await()
            .size()
        val username = publicData["username"] as? String
            ?: userData["username"] as? String
            ?: "chef"
        return PublicProfile(
            userId = userId,
            username = username,
            displayName = publicData["displayName"] as? String
                ?: userData["displayName"] as? String
                ?: username,
            profilePhotoUrl = (publicData["profilePhotoUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?: (userData["profilePhotoUrl"] as? String)?.takeIf { it.isNotBlank() },
            bio = publicData["bio"] as? String ?: "A warm little recipe book from @$username.",
            subscriberCount = subscriberCount,
            recipeCount = publicCount,
            publicRecipeCount = publicCount
        )
    }

    suspend fun publicRecipesForOwner(ownerId: String): List<CommunityRecipe> {
        val cached = publicRecipes.value.filter { it.ownerId == ownerId }
        if (cached.isNotEmpty()) return cached
        refreshPublicRecipes()
        return publicRecipes.value.filter { it.ownerId == ownerId }
    }

    suspend fun subscribe(currentUser: UserProfile, creator: PublicProfile) {
        if (currentUser.userId == creator.userId) return
        val id = "${currentUser.userId}_${creator.userId}"
        val previousSubscriptions = _subscribedCreatorIds.value
        _subscribedCreatorIds.value = _subscribedCreatorIds.value + creator.userId
        try {
            database.collection(SUBSCRIPTIONS).document(id).set(
                mapOf(
                    "subscriptionId" to id,
                    "subscriberUserId" to currentUser.userId,
                    "subscriberUsername" to currentUser.username,
                    "subscriberDisplayName" to currentUser.displayName,
                    "subscriberProfilePhotoUrl" to (currentUser.profilePhotoUri ?: ""),
                    "creatorUserId" to creator.userId,
                    "creatorUsername" to creator.username,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1
                )
            ).await()
        } catch (error: Throwable) {
            _subscribedCreatorIds.value = previousSubscriptions
            throw error
        }
    }

    suspend fun unsubscribe(currentUserId: String, creatorId: String) {
        val previousSubscriptions = _subscribedCreatorIds.value
        _subscribedCreatorIds.value = _subscribedCreatorIds.value - creatorId
        try {
            database.collection(SUBSCRIPTIONS).document("${currentUserId}_$creatorId").delete().await()
        } catch (error: Throwable) {
            _subscribedCreatorIds.value = previousSubscriptions
            throw error
        }
    }

    suspend fun toggleLike(recipe: CommunityRecipe, userId: String) {
        val likeId = "${recipe.id}_$userId"
        val wasLiked = _likedRecipeIds.value.contains(recipe.id)
        val delta = if (wasLiked) -1 else 1
        _likedRecipeIds.value = if (wasLiked) {
            _likedRecipeIds.value - recipe.id
        } else {
            _likedRecipeIds.value + recipe.id
        }
        updateLocalLikeCount(recipe.id, delta)
        try {
            if (wasLiked) {
                database.collection(RECIPE_LIKES).document(likeId).delete().await()
            } else {
                database.collection(RECIPE_LIKES).document(likeId).set(
                    mapOf(
                        "recipeLikeId" to likeId,
                        "recipeId" to recipe.id,
                        "userId" to userId,
                        "ownerId" to recipe.ownerId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "schemaVersion" to 1
                    )
                ).await()
            }
            database.collection(RECIPES).document(recipe.id).update(
                "likeCount",
                FieldValue.increment(delta.toLong())
            ).await()
        } catch (error: Throwable) {
            _likedRecipeIds.value = if (wasLiked) {
                _likedRecipeIds.value + recipe.id
            } else {
                _likedRecipeIds.value - recipe.id
            }
            updateLocalLikeCount(recipe.id, -delta)
            throw error
        }
    }

    suspend fun publish(recipe: Recipe, profile: UserProfile): String {
        val recipeId = publicRecipeId(profile.userId, recipe.id)
        val media = uploadPublicMedia(recipe, profile.userId, recipeId)
        val now = FieldValue.serverTimestamp()
        database.collection(RECIPES).document(recipeId).set(
            mapOf(
                "id" to recipeId,
                "localRecipeId" to recipe.id.toString(),
                "ownerId" to profile.userId,
                "ownerUsername" to profile.username,
                "ownerDisplayName" to profile.displayName,
                "ownerProfilePhotoUrl" to (profile.profilePhotoUri ?: ""),
                "title" to recipe.title,
                "notes" to recipe.notes,
                "prepTime" to recipe.prepTime,
                "cookTime" to recipe.cookTime,
                "totalTime" to recipe.totalTime,
                "servings" to recipe.servings,
                "ingredients" to recipe.ingredients,
                "instructions" to recipe.instructions,
                "photoUrls" to media.photoUrls,
                "thumbnailUrl" to (media.photoUrls.firstOrNull() ?: ""),
                "videoUrl" to (media.videoUrl ?: ""),
                "videoUrls" to listOfNotNull(media.videoUrl),
                "sourceUrl" to (recipe.sourceUrl ?: ""),
                "visibility" to RecipeVisibility.Public.value,
                "likeCount" to recipe.likeCount,
                "publicPublishedAt" to now,
                "updatedAt" to now,
                "schemaVersion" to 1
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
        database.collection(PUBLIC_PROFILES).document(profile.userId).set(
            mapOf(
                "uid" to profile.userId,
                "username" to profile.username,
                "displayName" to profile.displayName,
                "profilePhotoUrl" to (profile.profilePhotoUri ?: ""),
                "bio" to "A warm little recipe book from @${profile.username}.",
                "updatedAt" to now,
                "schemaVersion" to 1
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
        refreshPublicRecipes()
        return recipeId
    }

    suspend fun unpublish(ownerId: String, localRecipeId: Long) {
        database.collection(RECIPES).document(publicRecipeId(ownerId, localRecipeId)).update(
            mapOf(
                "visibility" to RecipeVisibility.Private.value,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        refreshPublicRecipes()
    }

    fun publicRecipeId(ownerId: String, localRecipeId: Long): String = "${ownerId}_$localRecipeId"

    private fun updateLocalLikeCount(recipeId: String, delta: Int) {
        _publicRecipes.value = _publicRecipes.value
            .map {
                if (it.id == recipeId) it.copy(likeCount = maxOf(0, it.likeCount + delta)) else it
            }
            .sortedWith(compareByDescending<CommunityRecipe> { it.likeCount }
                .thenByDescending { it.publicPublishedAt.takeIf { value -> value > 0L } ?: it.updatedAt })
    }

    private suspend fun uploadPublicMedia(
        recipe: Recipe,
        ownerId: String,
        recipeId: String
    ): UploadedPublicMedia {
        val folder = storage.reference.child("users/$ownerId/recipes/$recipeId/public")
        val photos = mutableListOf<String>()
        recipe.photoUris.distinct().forEachIndexed { index, uri ->
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                photos += uri
            } else {
                val data = runCatching { readMediaBytes(uri, 15 * 1_024 * 1_024) }.getOrNull()
                    ?: return@forEachIndexed
                val item = folder.child("photo_$index.jpg")
                item.putBytes(data, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
                photos += item.downloadUrl.await().toString()
            }
        }
        val videoUrl = recipe.videoUri?.let { uri ->
            if (uri.startsWith("http://") || uri.startsWith("https://")) return@let uri
            val data = runCatching { readMediaBytes(uri, 50 * 1_024 * 1_024) }.getOrNull()
                ?: return@let null
            val item = folder.child("video.mp4")
            item.putBytes(data, StorageMetadata.Builder().setContentType("video/mp4").build()).await()
            item.downloadUrl.await().toString()
        }
        return UploadedPublicMedia(photos, videoUrl)
    }

    private suspend fun readMediaBytes(uriText: String, maximumSize: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriText)
            val bytes = when (uri.scheme?.lowercase()) {
                "http", "https" -> URL(uriText).openStream().use { it.readBytes() }
                else -> appContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open selected media." }.readBytes()
                }
            }
            require(bytes.size <= maximumSize) { "That media file is too large." }
            bytes
        }

    private data class UploadedPublicMedia(val photoUrls: List<String>, val videoUrl: String?)

    companion object {
        private const val RECIPES = "recipes"
        private const val RECIPE_LIKES = "recipeLikes"
        private const val SUBSCRIPTIONS = "subscriptions"
        private const val PUBLIC_PROFILES = "publicProfiles"
    }
}
