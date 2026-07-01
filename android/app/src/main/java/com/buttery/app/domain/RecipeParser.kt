package com.buttery.app.domain

data class ParsedRecipe(
    val title: String = "",
    val ingredients: String = "",
    val instructions: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val servings: String = "",
    val notes: String = "",
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val originalRawText: String = "",
    val extractionMethod: String? = null
)

object RecipeParser {
    private val ingredientHeading = Regex("""(?i)^\s*ingredients?\s*:?\s*$""")
    private val instructionHeading =
        Regex("""(?i)^\s*(directions?|instructions?|method|steps?)\s*:?\s*$""")
    private val notesHeading = Regex("""(?i)^\s*(notes?|tips?)\s*:?\s*$""")
    private val metadataLine = Regex(
        """(?i)^\s*(prep(\s*time)?|cook(\s*time)?|total(\s*time)?|servings?|serves|yield)\s*:?\s*(.+)$"""
    )
    private val ingredientPattern = Regex(
        """(?i)(\d|[½¼¾⅓⅔⅛]|\b(one|two|three)\b).*\b(cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lbs?|pounds?|grams?|g|kg|ml|liters?|cloves?|cans?|packages?|pinch)\b"""
    )
    private val instructionPattern = Regex(
        """(?i)^\s*((step\s*)?\d+[.):]|[-*•])?\s*(add|mix|stir|bake|cook|heat|simmer|whisk|combine|pour|chop|slice|season|preheat|serve|place|bring|remove|fold|blend|roast)\b"""
    )
    private val numberedOrBulleted = Regex("""(?i)^\s*((step\s*)?\d+[.):]|[-*•])\s+.+""")
    private val socialClutter = Regex(
        """(?i)^\s*(like|share|follow|comment|save this recipe|full recipe below|join my group)(\s+.*)?$"""
    )
    private val hashtagOnly = Regex("""^\s*(#[\p{L}\p{N}_-]+\s*)+$""")
    private val emojiOnly = Regex("""^[\s\p{So}\p{Sk}]+$""")

    fun parse(rawText: String, sourceUrl: String? = null): ParsedRecipe {
        val normalized = rawText
            .replace("\r\n", "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        val lines = normalized.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isClutter(it) }
        if (lines.isEmpty()) {
            return ParsedRecipe(sourceUrl = sourceUrl, originalRawText = normalized)
        }

        val title = findTitle(lines)
        val ingredients = mutableListOf<String>()
        val instructions = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var prepTime = ""
        var cookTime = ""
        var totalTime = ""
        var servings = ""
        var section = Section.Unknown

        lines.forEach { line ->
            val metadata = metadataLine.matchEntire(line)
            when {
                line == title -> Unit
                ingredientHeading.matches(line) -> section = Section.Ingredients
                instructionHeading.matches(line) -> section = Section.Instructions
                notesHeading.matches(line) -> section = Section.Notes
                metadata != null -> {
                    val key = metadata.groupValues[1].lowercase()
                    val value = metadata.groupValues.last().trim()
                    when {
                        key.startsWith("prep") -> prepTime = value
                        key.startsWith("cook") -> cookTime = value
                        key.startsWith("serv") || key == "yield" -> servings = value
                        key.startsWith("total") -> totalTime = value
                    }
                }
                section == Section.Ingredients && isSafeRecipeLine(line) ->
                    ingredients += cleanListMarker(line)
                section == Section.Instructions && isSafeRecipeLine(line) ->
                    instructions += cleanListMarker(line)
                section == Section.Notes && isSafeRecipeLine(line) -> notes += line
                ingredientPattern.containsMatchIn(line) -> ingredients += cleanListMarker(line)
                instructionPattern.containsMatchIn(line) || numberedOrBulleted.matches(line) ->
                    instructions += cleanListMarker(line)
                else -> notes += line
            }
        }

        return ParsedRecipe(
            title = title,
            ingredients = ingredients.joinToString("\n"),
            instructions = instructions.joinToString("\n"),
            prepTime = prepTime,
            cookTime = cookTime,
            totalTime = totalTime,
            servings = servings,
            notes = notes.joinToString("\n").take(1_500),
            sourceUrl = sourceUrl,
            originalRawText = normalized
        )
    }

    private fun findTitle(lines: List<String>): String {
        return lines.firstOrNull { line ->
            line.length in 2..90 &&
                !isHeading(line) &&
                metadataLine.matchEntire(line) == null &&
                !ingredientPattern.containsMatchIn(line) &&
                !instructionPattern.containsMatchIn(line) &&
                !numberedOrBulleted.matches(line)
        }.orEmpty()
    }

    private fun isHeading(line: String) =
        ingredientHeading.matches(line) ||
            instructionHeading.matches(line) ||
            notesHeading.matches(line)

    private fun isClutter(line: String) =
        socialClutter.matches(line) || hashtagOnly.matches(line) || emojiOnly.matches(line)

    private fun isSafeRecipeLine(line: String) =
        line.length <= 500 && !isClutter(line)

    private fun cleanListMarker(line: String) =
        line.replace(Regex("""(?i)^\s*((step\s*)?\d+[.):]|[-*•])\s*"""), "").trim()

    private enum class Section { Unknown, Ingredients, Instructions, Notes }
}
