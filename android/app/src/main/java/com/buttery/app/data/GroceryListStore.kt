package com.buttery.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class GroceryListMode {
    TYPE,
    DRAW
}

data class GroceryPoint(
    val x: Float,
    val y: Float
)

data class GroceryStroke(
    val points: List<GroceryPoint>,
    val colorArgb: Long,
    val width: Float,
    val isEraser: Boolean
)

data class GroceryListState(
    val mode: GroceryListMode = GroceryListMode.TYPE,
    val typedText: String = "",
    val strokes: List<GroceryStroke> = emptyList()
)

class GroceryListStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): GroceryListState {
        val mode = runCatching {
            GroceryListMode.valueOf(
                preferences.getString(KEY_MODE, GroceryListMode.TYPE.name)
                    ?: GroceryListMode.TYPE.name
            )
        }.getOrDefault(GroceryListMode.TYPE)

        val storedText = preferences.getString(KEY_TYPED_TEXT, "").orEmpty()
        val typedText = if (preferences.getInt(KEY_TEXT_FORMAT, 1) < TEXT_FORMAT_PLAIN) {
            storedText.lineSequence()
                .joinToString("\n") { line -> line.removePrefix("• ").removePrefix("•") }
                .also {
                    preferences.edit()
                        .putString(KEY_TYPED_TEXT, it)
                        .putInt(KEY_TEXT_FORMAT, TEXT_FORMAT_PLAIN)
                        .apply()
                }
        } else {
            storedText
        }

        return GroceryListState(
            mode = mode,
            typedText = typedText,
            strokes = decodeStrokes(preferences.getString(KEY_STROKES, null))
        )
    }

    fun save(state: GroceryListState) {
        preferences.edit()
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_TYPED_TEXT, state.typedText)
            .putInt(KEY_TEXT_FORMAT, TEXT_FORMAT_PLAIN)
            .putString(KEY_STROKES, encodeStrokes(state.strokes))
            .apply()
    }

    fun clearTypedList() {
        preferences.edit()
            .putString(KEY_TYPED_TEXT, "")
            .putInt(KEY_TEXT_FORMAT, TEXT_FORMAT_PLAIN)
            .apply()
    }

    fun clearDrawing() {
        preferences.edit()
            .putString(KEY_STROKES, encodeStrokes(emptyList()))
            .apply()
    }

    private fun encodeStrokes(strokes: List<GroceryStroke>): String {
        val result = JSONArray()
        strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { point ->
                points.put(
                    JSONObject()
                        .put("x", point.x.toDouble())
                        .put("y", point.y.toDouble())
                )
            }
            result.put(
                JSONObject()
                    .put("points", points)
                    .put("color", stroke.colorArgb)
                    .put("width", stroke.width.toDouble())
                    .put("eraser", stroke.isEraser)
            )
        }
        return result.toString()
    }

    private fun decodeStrokes(value: String?): List<GroceryStroke> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val result = mutableListOf<GroceryStroke>()
            val strokes = JSONArray(value)
            for (strokeIndex in 0 until strokes.length()) {
                val encodedStroke = strokes.getJSONObject(strokeIndex)
                val encodedPoints = encodedStroke.getJSONArray("points")
                val points = buildList {
                    for (pointIndex in 0 until encodedPoints.length()) {
                        val point = encodedPoints.getJSONObject(pointIndex)
                        add(
                            GroceryPoint(
                                x = point.getDouble("x").toFloat(),
                                y = point.getDouble("y").toFloat()
                            )
                        )
                    }
                }
                if (points.isNotEmpty()) {
                    result += GroceryStroke(
                        points = points,
                        colorArgb = encodedStroke.getLong("color"),
                        width = encodedStroke.getDouble("width").toFloat(),
                        isEraser = encodedStroke.optBoolean("eraser", false)
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "grocery_list"
        const val KEY_MODE = "mode"
        const val KEY_TYPED_TEXT = "typed_text"
        const val KEY_TEXT_FORMAT = "text_format"
        const val KEY_STROKES = "strokes"
        const val TEXT_FORMAT_PLAIN = 2
    }
}
