package com.buttery.app.data

import com.buttery.app.data.local.RecipeAlbumDao
import com.buttery.app.data.local.RecipeAlbumEntity
import com.buttery.app.data.local.RecipeDao
import com.buttery.app.data.local.RecipeEntity
import com.buttery.app.domain.ParsedRecipe
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeAlbum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val albumDao: RecipeAlbumDao
) {
    private val activeOwnerId = MutableStateFlow<String?>(null)

    val recipes: Flow<List<Recipe>> = activeOwnerId.flatMapLatest { ownerId ->
        if (ownerId == null) flowOf(emptyList()) else recipeDao.observeAll(ownerId)
    }.map { items ->
        items.map { it.toDomain() }
    }

    val albums: Flow<List<RecipeAlbum>> = activeOwnerId.flatMapLatest { ownerId ->
        if (ownerId == null) flowOf(emptyList()) else albumDao.observeAll(ownerId)
    }.map { items ->
        items.map { it.toDomain() }
    }

    fun observeRecipe(id: Long): Flow<Recipe?> =
        activeOwnerId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(null) else recipeDao.observeById(id, ownerId)
        }.map { it?.toDomain() }

    fun setActiveOwner(ownerId: String?) {
        activeOwnerId.value = ownerId
    }

    private fun requireOwner(): String =
        checkNotNull(activeOwnerId.value) { "A signed-in account is required." }

    suspend fun createAlbum(name: String): Long {
        val ownerId = requireOwner()
        val cleanName = name.trim()
        require(cleanName.isNotEmpty())
        return albumDao.getByName(ownerId, cleanName)?.id
            ?: albumDao.insert(
                RecipeAlbumEntity(
                    ownerId = ownerId,
                    name = cleanName,
                    createdAt = System.currentTimeMillis()
                )
            )
    }

    suspend fun saveRecipe(
        recipe: ParsedRecipe,
        albumId: Long?,
        photoUri: String?,
        videoUri: String?,
        photoUris: List<String> = listOfNotNull(photoUri),
        sourceUrl: String? = recipe.sourceUrl,
        originalRawText: String = recipe.originalRawText
    ): Long {
        val ownerId = requireOwner()
        val savedAlbumId = albumId?.takeIf { albumDao.getById(it, ownerId) != null }
        val now = System.currentTimeMillis()
        return recipeDao.insert(
            RecipeEntity(
                ownerId = ownerId,
                title = recipe.title.trim(),
                notes = recipe.notes.trim(),
                prepTime = recipe.prepTime.trim(),
                cookTime = recipe.cookTime.trim(),
                totalTime = recipe.totalTime.trim(),
                servings = recipe.servings.trim(),
                ingredients = recipe.ingredients.trim(),
                instructions = recipe.instructions.trim(),
                photoUri = photoUri,
                photoUris = photoUris.distinct().joinToString("\n").ifBlank { null },
                videoUri = videoUri,
                imageUrl = recipe.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                sourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
                originalRawText = originalRawText,
                albumId = savedAlbumId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun setFavorite(recipeId: Long, isFavorite: Boolean) {
        recipeDao.updateFavorite(
            recipeId,
            requireOwner(),
            isFavorite,
            System.currentTimeMillis()
        )
    }

    suspend fun updateAlbum(id: Long, name: String, coverUri: String?) {
        val ownerId = requireOwner()
        val cleanName = name.trim()
        require(cleanName.isNotEmpty())
        albumDao.update(id, ownerId, cleanName, coverUri)
    }

    suspend fun deleteAlbum(id: Long) {
        val ownerId = requireOwner()
        if (albumDao.getById(id, ownerId) == null) return
        albumDao.moveRecipesAndDelete(ownerId, id, null)
    }

    suspend fun updateRecipe(
        recipeId: Long,
        recipe: ParsedRecipe,
        albumId: Long?,
        photoUri: String?,
        videoUri: String?,
        photoUris: List<String>,
        isFavorite: Boolean
    ) {
        val ownerId = requireOwner()
        val existing = recipeDao.getById(recipeId, ownerId) ?: return
        val savedAlbumId = albumId?.takeIf { albumDao.getById(it, ownerId) != null }
        recipeDao.update(
            existing.copy(
                title = recipe.title.trim(),
                notes = recipe.notes.trim(),
                prepTime = recipe.prepTime.trim(),
                cookTime = recipe.cookTime.trim(),
                totalTime = recipe.totalTime.trim(),
                servings = recipe.servings.trim(),
                ingredients = recipe.ingredients.trim(),
                instructions = recipe.instructions.trim(),
                photoUri = photoUri,
                photoUris = photoUris.distinct().joinToString("\n").ifBlank { null },
                videoUri = videoUri,
                imageUrl = recipe.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                sourceUrl = recipe.sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
                originalRawText = recipe.originalRawText,
                albumId = savedAlbumId,
                updatedAt = System.currentTimeMillis(),
                isFavorite = isFavorite
            )
        )
    }

    suspend fun deleteRecipe(id: Long) {
        recipeDao.deleteById(id, requireOwner())
    }

    private fun RecipeEntity.toDomain() = Recipe(
        id = id,
        title = title,
        notes = notes,
        prepTime = prepTime,
        cookTime = cookTime,
        totalTime = totalTime,
        servings = servings,
        ingredients = ingredients,
        instructions = instructions,
        photoUri = photoUri,
        photoUris = photoUris
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.toList()
            .orEmpty()
            .ifEmpty { listOfNotNull(photoUri) },
        videoUri = videoUri,
        imageUrl = imageUrl,
        sourceUrl = sourceUrl,
        originalRawText = originalRawText,
        albumId = albumId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite
    )

    private fun RecipeAlbumEntity.toDomain() = RecipeAlbum(
        id = id,
        name = name,
        createdAt = createdAt,
        customCoverImageUri = customCoverImageUri
    )
}
