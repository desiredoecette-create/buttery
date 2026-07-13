package com.buttery.app.domain

data class Recipe(
    val id: Long,
    val title: String,
    val notes: String,
    val prepTime: String,
    val cookTime: String,
    val totalTime: String,
    val servings: String,
    val ingredients: String,
    val instructions: String,
    val photoUri: String?,
    val photoUris: List<String>,
    val videoUri: String?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val originalRawText: String,
    val albumId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false,
    val visibility: RecipeVisibility = RecipeVisibility.Private,
    val likeCount: Int = 0,
    val publicPublishedAt: Long? = null
)

data class RecipeAlbum(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val customCoverImageUri: String?
)

enum class RecipeVisibility(val value: String) {
    Private("private"),
    Public("public"),
    Shared("shared");

    companion object {
        fun from(value: String?): RecipeVisibility =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Private
    }
}
