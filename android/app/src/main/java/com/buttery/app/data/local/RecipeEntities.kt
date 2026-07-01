package com.buttery.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_albums",
    indices = [Index("ownerId")]
)
data class RecipeAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val name: String,
    val createdAt: Long,
    val customCoverImageUri: String? = null
)

@Entity(
    tableName = "recipes",
    foreignKeys = [
        ForeignKey(
            entity = RecipeAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("albumId"), Index("ownerId")]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val title: String,
    val notes: String,
    val prepTime: String,
    val cookTime: String,
    val totalTime: String,
    val servings: String,
    val ingredients: String,
    val instructions: String,
    val photoUri: String?,
    val photoUris: String?,
    val videoUri: String?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val originalRawText: String,
    val albumId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false
)
