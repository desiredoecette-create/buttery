package com.buttery.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeAlbumDao {
    @Query("SELECT * FROM recipe_albums WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE")
    fun observeAll(ownerId: String): Flow<List<RecipeAlbumEntity>>

    @Query("SELECT * FROM recipe_albums WHERE id = :id AND ownerId = :ownerId")
    suspend fun getById(id: Long, ownerId: String): RecipeAlbumEntity?

    @Query("SELECT * FROM recipe_albums WHERE ownerId = :ownerId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(ownerId: String, name: String): RecipeAlbumEntity?

    @Insert
    suspend fun insert(album: RecipeAlbumEntity): Long

    @Query("UPDATE recipe_albums SET name = :name, customCoverImageUri = :coverUri WHERE id = :id AND ownerId = :ownerId")
    suspend fun update(id: Long, ownerId: String, name: String, coverUri: String?)

    @Query("UPDATE recipes SET albumId = :destinationAlbumId, updatedAt = :updatedAt WHERE albumId = :albumId AND ownerId = :ownerId")
    suspend fun moveRecipes(ownerId: String, albumId: Long, destinationAlbumId: Long?, updatedAt: Long)

    @Query("DELETE FROM recipe_albums WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteById(id: Long, ownerId: String)

    @Query("DELETE FROM recipe_albums WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Transaction
    suspend fun moveRecipesAndDelete(ownerId: String, albumId: Long, destinationAlbumId: Long?) {
        moveRecipes(ownerId, albumId, destinationAlbumId, System.currentTimeMillis())
        deleteById(albumId, ownerId)
    }
}
