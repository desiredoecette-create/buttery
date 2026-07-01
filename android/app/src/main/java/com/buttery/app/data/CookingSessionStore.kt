package com.buttery.app.data

import android.content.Context

data class CookingSession(
    val recipeId: Long,
    val lastOpenedTimestamp: Long,
    val scrollPosition: Int = 0,
    val currentStep: Int? = null
)

class CookingSessionStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): CookingSession? {
        val recipeId = preferences.getLong(KEY_RECIPE_ID, NO_RECIPE)
        if (recipeId == NO_RECIPE) return null

        return CookingSession(
            recipeId = recipeId,
            lastOpenedTimestamp = preferences.getLong(KEY_LAST_OPENED, 0L),
            scrollPosition = preferences.getInt(KEY_SCROLL_POSITION, 0),
            currentStep = preferences.getInt(KEY_CURRENT_STEP, NO_STEP)
                .takeUnless { it == NO_STEP }
        )
    }

    fun recordRecipeOpened(recipeId: Long): CookingSession {
        val previous = load()?.takeIf { it.recipeId == recipeId }
        return CookingSession(
            recipeId = recipeId,
            lastOpenedTimestamp = System.currentTimeMillis(),
            scrollPosition = previous?.scrollPosition ?: 0,
            currentStep = previous?.currentStep
        ).also(::save)
    }

    fun save(session: CookingSession) {
        preferences.edit()
            .putLong(KEY_RECIPE_ID, session.recipeId)
            .putLong(KEY_LAST_OPENED, session.lastOpenedTimestamp)
            .putInt(KEY_SCROLL_POSITION, session.scrollPosition)
            .putInt(KEY_CURRENT_STEP, session.currentStep ?: NO_STEP)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "cooking_session"
        const val KEY_RECIPE_ID = "recipe_id"
        const val KEY_LAST_OPENED = "last_opened_timestamp"
        const val KEY_SCROLL_POSITION = "scroll_position"
        const val KEY_CURRENT_STEP = "current_step"
        const val NO_RECIPE = -1L
        const val NO_STEP = -1
    }
}
