package com.buttery.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE ownerId = :ownerId ORDER BY updatedAt DESC")
    fun observeAll(ownerId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id AND ownerId = :ownerId")
    fun observeById(id: Long, ownerId: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE id = :id AND ownerId = :ownerId")
    suspend fun getById(id: Long, ownerId: String): RecipeEntity?

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteById(id: Long, ownerId: String)

    @Query("DELETE FROM recipes WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("UPDATE recipes SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateFavorite(id: Long, ownerId: String, isFavorite: Boolean, updatedAt: Long)
}
