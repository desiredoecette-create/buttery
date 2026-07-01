package com.buttery.app.data.importer

import android.content.Context
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.buttery.app.domain.ParsedRecipe
import com.buttery.app.domain.RecipeParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ExtractionMethod(val label: String) {
    Structured("Structured recipe data found"),
    Fallback("Fallback text parsing used")
}

data class UrlImportResult(
    val recipe: ParsedRecipe,
    val method: ExtractionMethod
)

object RecipeImportService {
    suspend fun recognizeText(context: Context, uri: Uri): String {
        val image = withContext(Dispatchers.IO) {
            InputImage.fromFilePath(context, uri)
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    suspend fun importFromUrl(rawUrl: String): UrlImportResult = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(rawUrl)
        val html = fetchHtml(normalizedUrl)

        extractJsonLdRecipe(html, normalizedUrl)?.let {
            if (it.title.isNotBlank() && (it.ingredients.isNotBlank() || it.instructions.isNotBlank())) {
                return@withContext UrlImportResult(it, ExtractionMethod.Structured)
            }
        }

        val fallbackText = isolateRecipeText(html)
        val fallbackRecipe = RecipeParser.parse(fallbackText, normalizedUrl).copy(
            extractionMethod = ExtractionMethod.Fallback.label,
            originalRawText = fallbackText
        )
        if (
            fallbackRecipe.title.isBlank() ||
            (fallbackRecipe.ingredients.isBlank() && fallbackRecipe.instructions.isBlank())
        ) {
            error("No clean recipe found")
        }
        UrlImportResult(fallbackRecipe, ExtractionMethod.Fallback)
    }

    private fun normalizeUrl(rawUrl: String): String {
        val normalized = rawUrl.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        val uri = URI(normalized)
        require(uri.scheme == "http" || uri.scheme == "https")
        return normalized
    }

    private fun fetchHtml(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) RecipeTablet/1.0")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            if (!connection.contentType.orEmpty().contains("text/html")) {
                error("Unsupported content type")
            }
            return connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                val result = StringBuilder()
                while (result.length < MAX_RESPONSE_CHARS) {
                    val count = reader.read(
                        buffer,
                        0,
                        minOf(buffer.size, MAX_RESPONSE_CHARS - result.length)
                    )
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractJsonLdRecipe(html: String, sourceUrl: String): ParsedRecipe? {
        val scripts = JSON_LD_SCRIPT.findAll(html).map { it.groupValues[1].trim() }
        scripts.forEach { script ->
            val json = runCatching {
                JSONTokener(
                    script
                        .removePrefix("<!--")
                        .removeSuffix("-->")
                        .trim()
                ).nextValue()
            }.getOrNull() ?: return@forEach
            findRecipeObject(json)?.let { recipe ->
                return recipeObjectToParsed(recipe, sourceUrl, script)
            }
        }
        return null
    }

    private fun findRecipeObject(value: Any?): JSONObject? {
        return when (value) {
            is JSONObject -> {
                if (isRecipeType(value.opt("@type"))) return value
                value.keys().asSequence()
                    .mapNotNull { key -> findRecipeObject(value.opt(key)) }
                    .firstOrNull()
            }
            is JSONArray -> (0 until value.length())
                .asSequence()
                .mapNotNull { findRecipeObject(value.opt(it)) }
                .firstOrNull()
            else -> null
        }
    }

    private fun isRecipeType(type: Any?): Boolean = when (type) {
        is String -> type.equals("Recipe", ignoreCase = true) ||
            type.endsWith("/Recipe", ignoreCase = true)
        is JSONArray -> (0 until type.length()).any { isRecipeType(type.opt(it)) }
        else -> false
    }

    private fun recipeObjectToParsed(
        json: JSONObject,
        sourceUrl: String,
        originalJson: String
    ): ParsedRecipe {
        val ingredients = json.optJSONArray("recipeIngredient")
            ?.stringValues()
            .orEmpty()
            .filter { it.isNotBlank() }
        val instructionSteps = mutableListOf<String>()
        collectInstructions(json.opt("recipeInstructions"), instructionSteps)
        val cleanSteps = instructionSteps.map { cleanText(it) }.filter { it.isNotBlank() }

        return ParsedRecipe(
            title = cleanText(json.optString("name")),
            notes = cleanText(json.optString("description")).take(MAX_DESCRIPTION_CHARS),
            prepTime = formatDuration(json.optString("prepTime")),
            cookTime = formatDuration(json.optString("cookTime")),
            totalTime = formatDuration(json.optString("totalTime")),
            servings = readYield(json.opt("recipeYield")),
            ingredients = ingredients.joinToString("\n") { cleanText(it) },
            instructions = cleanSteps.mapIndexed { index, step -> "${index + 1}. $step" }
                .joinToString("\n"),
            imageUrl = readImageUrl(json.opt("image")),
            sourceUrl = sourceUrl,
            originalRawText = originalJson.take(MAX_STORED_RAW_CHARS),
            extractionMethod = ExtractionMethod.Structured.label
        )
    }

    private fun collectInstructions(value: Any?, output: MutableList<String>) {
        when (value) {
            is String -> value.lines().filter { it.isNotBlank() }.forEach(output::add)
            is JSONArray -> (0 until value.length()).forEach { collectInstructions(value.opt(it), output) }
            is JSONObject -> {
                when {
                    value.has("itemListElement") ->
                        collectInstructions(value.opt("itemListElement"), output)
                    value.has("text") -> output += value.optString("text")
                    value.has("name") -> output += value.optString("name")
                }
            }
        }
    }

    private fun isolateRecipeText(html: String): String {
        val title = extractMeta(html, "og:title")
            ?: TITLE_TAG.find(html)?.groupValues?.get(1)?.let(::cleanText)
            .orEmpty()
        val description = extractMeta(html, "og:description")
            ?.let(::cleanText)
            ?.take(MAX_DESCRIPTION_CHARS)
            .orEmpty()
        val visibleText = html
            .replace(REMOVE_ELEMENTS, " ")
            .replace(BLOCK_ELEMENTS, "\n")
            .let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
        val lines = visibleText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isWebClutter(it) }
            .distinct()

        val ingredientStart = lines.indexOfFirst { INGREDIENT_HEADING.matches(it) }
        val instructionStart = lines.indexOfFirst { INSTRUCTION_HEADING.matches(it) }
        val notesStart = lines.indexOfFirst { NOTES_HEADING.matches(it) }
        if (ingredientStart < 0 || instructionStart < 0) return ""

        val ingredientsEnd = listOf(instructionStart, notesStart)
            .filter { it > ingredientStart }
            .minOrNull() ?: lines.size
        val instructionsEnd = listOf(notesStart)
            .filter { it > instructionStart }
            .minOrNull() ?: lines.size
        val ingredients = lines.subList(ingredientStart, ingredientsEnd).take(MAX_SECTION_LINES)
        val instructions = lines.subList(instructionStart, instructionsEnd).take(MAX_SECTION_LINES)
        val notes = if (notesStart >= 0) lines.drop(notesStart).take(MAX_NOTES_LINES) else emptyList()

        return buildList {
            if (title.isNotBlank()) add(title)
            if (description.isNotBlank()) {
                add("Notes")
                add(description)
            }
            addAll(ingredients)
            addAll(instructions)
            addAll(notes)
        }.joinToString("\n")
    }

    private fun extractMeta(html: String, property: String): String? {
        val tags = META_TAG.findAll(html)
        return tags.firstNotNullOfOrNull { match ->
            val tag = match.value
            val key = META_KEY.find(tag)?.groupValues?.get(3)
            if (key.equals(property, ignoreCase = true)) {
                META_CONTENT.find(tag)?.groupValues?.get(2)
            } else {
                null
            }
        }
    }

    private fun isWebClutter(line: String): Boolean =
        WEB_CLUTTER.containsMatchIn(line) || line.length > 500

    private fun readYield(value: Any?): String = when (value) {
        is JSONArray -> value.stringValues().joinToString(", ")
        is Number -> value.toString()
        is String -> cleanText(value)
        else -> ""
    }

    private fun readImageUrl(value: Any?): String? = when (value) {
        is String -> value
        is JSONObject -> value.optString("url").takeIf { it.isNotBlank() }
            ?: value.optString("contentUrl").takeIf { it.isNotBlank() }
        is JSONArray -> (0 until value.length())
            .asSequence()
            .mapNotNull { readImageUrl(value.opt(it)) }
            .firstOrNull()
        else -> null
    }

    private fun formatDuration(value: String): String {
        if (!value.startsWith("P", ignoreCase = true)) return cleanText(value)
        val match = ISO_DURATION.matchEntire(value) ?: return value
        return buildList {
            match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { add("$it days") }
            match.groupValues[2].toIntOrNull()?.takeIf { it > 0 }?.let { add("$it hr") }
            match.groupValues[3].toIntOrNull()?.takeIf { it > 0 }?.let { add("$it min") }
        }.joinToString(" ")
    }

    private fun cleanText(value: String): String =
        HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun JSONArray.stringValues(): List<String> =
        (0 until length()).mapNotNull { opt(it)?.toString() }

    private val JSON_LD_SCRIPT = Regex(
        """(?is)<script\b(?=[^>]*\btype\s*=\s*["']application/ld\+json["'])[^>]*>(.*?)</script>"""
    )
    private val REMOVE_ELEMENTS = Regex(
        """(?is)<(script|style|nav|footer|aside|form|iframe|noscript)\b[^>]*>.*?</\1>"""
    )
    private val BLOCK_ELEMENTS = Regex("""(?is)<(br|p|div|li|h[1-6]|section|article)\b[^>]*>""")
    private val TITLE_TAG = Regex("""(?is)<title\b[^>]*>(.*?)</title>""")
    private val META_TAG = Regex("""(?is)<meta\b[^>]*>""")
    private val META_KEY = Regex("""(?i)\b(property|name)\s*=\s*(["'])(.*?)\2""")
    private val META_CONTENT = Regex("""(?i)\bcontent\s*=\s*(["'])(.*?)\1""")
    private val INGREDIENT_HEADING = Regex("""(?i)^\s*ingredients?\s*:?\s*$""")
    private val INSTRUCTION_HEADING =
        Regex("""(?i)^\s*(instructions?|directions?|method|steps?)\s*:?\s*$""")
    private val NOTES_HEADING = Regex("""(?i)^\s*(recipe\s+)?notes?\s*:?\s*$""")
    private val WEB_CLUTTER = Regex(
        """(?i)\b(jump to recipe|print recipe|newsletter|subscribe|sign up|advertisement|related posts?|comments?|leave a reply|share on|author bio|frequently asked questions?|privacy policy|cookie policy)\b"""
    )
    private val ISO_DURATION = Regex("""(?i)P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?""")
    private const val MAX_RESPONSE_CHARS = 2_000_000
    private const val MAX_STORED_RAW_CHARS = 100_000
    private const val MAX_DESCRIPTION_CHARS = 600
    private const val MAX_SECTION_LINES = 80
    private const val MAX_NOTES_LINES = 20
}
