package com.buttery.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecipeEntity::class, RecipeAlbumEntity::class],
    version = 9,
    exportSchema = false
)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeAlbumDao(): RecipeAlbumDao

    companion object {
        @Volatile
        private var instance: RecipeDatabase? = null

        fun getInstance(context: Context): RecipeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecipeDatabase::class.java,
                    "recipes.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9
                )
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN sourceUrl TEXT")
                db.execSQL(
                    "ALTER TABLE recipes ADD COLUMN originalRawText TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recipes ADD COLUMN totalTime TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("ALTER TABLE recipes ADD COLUMN imageUrl TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipe_albums ADD COLUMN customCoverImageUri TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasFavoriteColumn = db.query("PRAGMA table_info(recipes)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "isFavorite") {
                            found = true
                            break
                        }
                    }
                    found
                }
                if (!hasFavoriteColumn) {
                    db.execSQL(
                        "ALTER TABLE recipes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recipe_albums ADD COLUMN ownerId TEXT NOT NULL DEFAULT '__legacy_local__'"
                )
                db.execSQL(
                    "ALTER TABLE recipes ADD COLUMN ownerId TEXT NOT NULL DEFAULT '__legacy_local__'"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipe_albums_ownerId ON recipe_albums(ownerId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipes_ownerId ON recipes(ownerId)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN photoUris TEXT")
                db.execSQL("UPDATE recipes SET photoUris = photoUri WHERE photoUri IS NOT NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE recipes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        prepTime TEXT NOT NULL,
                        cookTime TEXT NOT NULL,
                        totalTime TEXT NOT NULL,
                        servings TEXT NOT NULL,
                        ingredients TEXT NOT NULL,
                        instructions TEXT NOT NULL,
                        photoUri TEXT,
                        photoUris TEXT,
                        videoUri TEXT,
                        imageUrl TEXT,
                        sourceUrl TEXT,
                        originalRawText TEXT NOT NULL,
                        albumId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        FOREIGN KEY(albumId) REFERENCES recipe_albums(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO recipes_new (
                        id, ownerId, title, notes, prepTime, cookTime, totalTime,
                        servings, ingredients, instructions, photoUri, photoUris,
                        videoUri, imageUrl, sourceUrl, originalRawText, albumId,
                        createdAt, updatedAt, isFavorite
                    )
                    SELECT
                        r.id, r.ownerId, r.title, r.notes, r.prepTime, r.cookTime,
                        r.totalTime, r.servings, r.ingredients, r.instructions,
                        r.photoUri, r.photoUris, r.videoUri, r.imageUrl, r.sourceUrl,
                        r.originalRawText,
                        CASE WHEN a.name = 'Uncategorized' THEN NULL ELSE r.albumId END,
                        r.createdAt, r.updatedAt, r.isFavorite
                    FROM recipes r
                    LEFT JOIN recipe_albums a ON a.id = r.albumId
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE recipes")
                db.execSQL("ALTER TABLE recipes_new RENAME TO recipes")
                db.execSQL("CREATE INDEX index_recipes_albumId ON recipes(albumId)")
                db.execSQL("CREATE INDEX index_recipes_ownerId ON recipes(ownerId)")
                db.execSQL("DELETE FROM recipe_albums WHERE name = 'Uncategorized'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recipes ADD COLUMN visibility TEXT NOT NULL DEFAULT 'private'"
                )
                db.execSQL(
                    "ALTER TABLE recipes ADD COLUMN likeCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE recipes ADD COLUMN publicPublishedAt INTEGER")
            }
        }
    }
}
