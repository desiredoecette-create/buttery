package com.buttery.app.ui.screens

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buttery.app.data.importer.RecipeImportService
import com.buttery.app.data.importer.UrlImportResult
import com.buttery.app.domain.ParsedRecipe
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeAlbum
import com.buttery.app.domain.RecipeParser
import com.buttery.app.domain.RecipeVisibility
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView

private val PageBackground = Color(0xFFF4EFE6)
private val Ink = Color(0xFF211F1B)
private val Accent = Color(0xFFA95D32)

@Composable
fun NewRecipeScreen(
    onBack: () -> Unit,
    onCreateManually: () -> Unit,
    onImportRecipe: () -> Unit
) {
    RecipePage(
        title = "Add a new recipe",
        onBack = onBack,
        phoneVerticalOffsetFraction = 0.07f
    ) {
        val isPhone = LocalConfiguration.current.screenWidthDp < 700
        Text("Choose how you would like to begin.", color = Ink.copy(alpha = 0.7f), fontSize = 19.sp)
        if (isPhone) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChoiceCard(
                    title = "Create Manually",
                    subtitle = "Enter ingredients, instructions, timing, and media.",
                    icon = Icons.Rounded.Add,
                    onClick = onCreateManually,
                    modifier = Modifier.fillMaxWidth()
                )
                ChoiceCard(
                    title = "Import Recipe",
                    subtitle = "Paste recipe text and review the extracted details.",
                    icon = Icons.Rounded.Upload,
                    onClick = onImportRecipe,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            ChoiceCard(
                title = "Create Manually",
                subtitle = "Enter ingredients, instructions, timing, and media.",
                icon = Icons.Rounded.Add,
                onClick = onCreateManually,
                modifier = Modifier.weight(1f)
            )
            ChoiceCard(
                title = "Import Recipe",
                subtitle = "Paste recipe text and review the extracted details.",
                icon = Icons.Rounded.Upload,
                onClick = onImportRecipe,
                modifier = Modifier.weight(1f)
            )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (isPhone) 150.dp else 250.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(if (isPhone) 22.dp else 30.dp),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(shape = RoundedCornerShape(50), color = Accent) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.padding(if (isPhone) 13.dp else 17.dp).size(if (isPhone) 28.dp else 34.dp))
            }
            Text(title, color = Ink, fontFamily = FontFamily.Serif, fontSize = if (isPhone) 27.sp else 30.sp)
            Text(subtitle, color = Ink.copy(alpha = 0.68f), fontSize = if (isPhone) 16.sp else 18.sp, lineHeight = if (isPhone) 22.sp else 26.sp)
        }
    }
}

@Composable
fun ManualRecipeEntryScreen(
    albums: List<RecipeAlbum>,
    onBack: () -> Unit,
    onCreateAlbum: suspend (String) -> Long,
    onSave: suspend (ParsedRecipe, Long?, String?, List<String>, String?, RecipeVisibility) -> Long,
    onViewRecipe: (Long) -> Unit
) {
    var recipe by remember { mutableStateOf(ParsedRecipe()) }
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var mediaMessage by remember { mutableStateOf<String?>(null) }
    var selectedAlbumId by remember { mutableStateOf<Long?>(null) }
    var visibility by remember { mutableStateOf(RecipeVisibility.Private) }
    var newAlbumName by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var savedRecipeId by remember { mutableStateOf<Long?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun retainMediaPermission(uri: Uri?) {
        if (uri == null) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach(::retainMediaPermission)
        val combined = (photoUris + uris).distinct()
        photoUris = combined
        if (photoUri == null) photoUri = combined.firstOrNull()
    }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && mediaSize(context, uri) > MAX_VIDEO_BYTES) {
            mediaMessage = "Video must be 50 MB or smaller."
        } else {
            retainMediaPermission(uri)
            videoUri = uri
            mediaMessage = null
        }
    }

    RecipePage(title = "Create manually", onBack = onBack, scrollable = true) {
        RecipeForm(
            recipe = recipe,
            onRecipeChange = { recipe = it },
            includeMedia = true,
            photoName = if (photoUris.isEmpty()) null else "${photoUris.size} photo${if (photoUris.size == 1) "" else "s"}",
            videoName = videoUri?.lastPathSegment,
            onAddPhoto = {
                photoPicker.launch(arrayOf("image/*"))
            },
            onAddVideo = {
                videoPicker.launch(arrayOf("video/*"))
            }
        )
        PhotoSelection(
            photos = photoUris,
            mainPhoto = photoUri,
            onSelectMain = { photoUri = it },
            onRemove = { removed ->
                photoUris = photoUris - removed
                if (photoUri == removed) photoUri = photoUris.firstOrNull()
            }
        )
        mediaMessage?.let { Text(it, color = Color(0xFF9B2C2C), fontSize = 17.sp) }
        AlbumSection(
            albums = albums,
            selectedAlbumId = selectedAlbumId,
            onAlbumSelected = { selectedAlbumId = it },
            newAlbumName = newAlbumName,
            onNewAlbumNameChange = { newAlbumName = it },
            onCreateAlbum = {
                if (newAlbumName.isNotBlank()) {
                    scope.launch {
                        selectedAlbumId = onCreateAlbum(newAlbumName)
                        newAlbumName = ""
                    }
                }
            }
        )
        VisibilityChooser(visibility = visibility, onVisibilityChange = { visibility = it })
        SaveButton(
            enabled = !isSaving && savedRecipeId == null,
            isSaving = isSaving,
            onClick = {
                validationMessage = when {
                    recipe.title.isBlank() -> "Add a recipe title before saving."
                    recipe.ingredients.isBlank() && recipe.instructions.isBlank() ->
                        "Add ingredients or instructions before saving."
                    else -> null
                }
                if (validationMessage == null) {
                    scope.launch {
                        isSaving = true
                        savedRecipeId = onSave(
                            recipe,
                            selectedAlbumId,
                            photoUri?.toString(),
                            photoUris.map(Uri::toString),
                            videoUri?.toString(),
                            visibility
                        )
                        isSaving = false
                        savedRecipeId?.let(onViewRecipe)
                    }
                }
            }
        )
        validationMessage?.let {
            Text(it, color = Color(0xFF9B2C2C), fontSize = 18.sp)
        }
        savedRecipeId?.let { id ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    "Recipe saved to ${
                        albums.firstOrNull { it.id == selectedAlbumId }?.name
                            ?: "All Recipes"
                    }.",
                    color = Color(0xFF55704C),
                    fontSize = 18.sp
                )
                OutlinedButton(onClick = { onViewRecipe(id) }) {
                    Text("View Recipe", fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
fun EditRecipeScreen(
    existingRecipe: Recipe,
    albums: List<RecipeAlbum>,
    onBack: () -> Unit,
    onSave: suspend (ParsedRecipe, Long?, String?, List<String>, String?, Boolean, RecipeVisibility) -> Unit,
    onSaved: () -> Unit
) {
    var recipe by remember(existingRecipe.id) {
        mutableStateOf(
            ParsedRecipe(
                title = existingRecipe.title,
                notes = existingRecipe.notes,
                prepTime = existingRecipe.prepTime,
                cookTime = existingRecipe.cookTime,
                totalTime = existingRecipe.totalTime,
                servings = existingRecipe.servings,
                ingredients = existingRecipe.ingredients,
                instructions = existingRecipe.instructions,
                imageUrl = existingRecipe.imageUrl,
                sourceUrl = existingRecipe.sourceUrl,
                originalRawText = existingRecipe.originalRawText
            )
        )
    }
    var photoUris by remember(existingRecipe.id) {
        mutableStateOf(existingRecipe.photoUris.map(Uri::parse))
    }
    var photoUri by remember(existingRecipe.id) {
        mutableStateOf(existingRecipe.photoUri?.let(Uri::parse))
    }
    var videoUri by remember(existingRecipe.id) {
        mutableStateOf(existingRecipe.videoUri?.let(Uri::parse))
    }
    var selectedAlbumId by remember(existingRecipe.id) { mutableStateOf<Long?>(existingRecipe.albumId) }
    var isFavorite by remember(existingRecipe.id) { mutableStateOf(existingRecipe.isFavorite) }
    var visibility by remember(existingRecipe.id) { mutableStateOf(existingRecipe.visibility) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var mediaMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun retainMediaPermission(uri: Uri?) {
        if (uri == null) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(::retainMediaPermission)
        val combined = (photoUris + uris).distinct()
        photoUris = combined
        if (photoUri == null) photoUri = combined.firstOrNull()
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && mediaSize(context, uri) > MAX_VIDEO_BYTES) {
            mediaMessage = "Video must be 50 MB or smaller."
        } else if (uri != null) {
            retainMediaPermission(uri)
            videoUri = uri
            mediaMessage = null
        }
    }

    RecipePage(title = "Edit recipe", onBack = onBack, scrollable = true) {
        RecipeForm(
            recipe = recipe,
            onRecipeChange = { recipe = it },
            includeMedia = true,
            photoName = if (photoUris.isEmpty()) null else "${photoUris.size} photo${if (photoUris.size == 1) "" else "s"}",
            videoName = videoUri?.lastPathSegment,
            onAddPhoto = { photoPicker.launch(arrayOf("image/*")) },
            onAddVideo = { videoPicker.launch(arrayOf("video/*")) }
        )
        PhotoSelection(
            photos = photoUris,
            mainPhoto = photoUri,
            onSelectMain = { photoUri = it },
            onRemove = { removed ->
                photoUris = photoUris - removed
                if (photoUri == removed) photoUri = photoUris.firstOrNull()
            }
        )
        mediaMessage?.let { Text(it, color = Color(0xFF9B2C2C), fontSize = 17.sp) }
        Text("Album", color = Ink, fontFamily = FontFamily.Serif, fontSize = 27.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                FilterChip(
                    selected = selectedAlbumId == null,
                    onClick = { selectedAlbumId = null },
                    label = { Text("All Recipes", fontSize = 16.sp) }
                )
            }
            items(albums, key = { it.id }) { album ->
                FilterChip(
                    selected = selectedAlbumId == album.id,
                    onClick = { selectedAlbumId = album.id },
                    label = { Text(album.name, fontSize = 16.sp) }
                )
            }
        }
        FilterChip(
            selected = isFavorite,
            onClick = { isFavorite = !isFavorite },
            label = { Text(if (isFavorite) "Favorite" else "Mark as favorite", fontSize = 16.sp) }
        )
        VisibilityChooser(visibility = visibility, onVisibilityChange = { visibility = it })
        SaveButton(
            enabled = !isSaving,
            isSaving = isSaving,
            onClick = {
                validationMessage = when {
                    recipe.title.isBlank() -> "Add a recipe title before saving."
                    recipe.ingredients.isBlank() && recipe.instructions.isBlank() ->
                        "Add ingredients or instructions before saving."
                    else -> null
                }
                if (validationMessage == null) {
                    scope.launch {
                        isSaving = true
                        onSave(
                            recipe,
                            selectedAlbumId,
                            photoUri?.toString(),
                            photoUris.map(Uri::toString),
                            videoUri?.toString(),
                            isFavorite,
                            visibility
                        )
                        isSaving = false
                        onSaved()
                    }
                }
            }
        )
        validationMessage?.let {
            Text(it, color = Color(0xFF9B2C2C), fontSize = 18.sp)
        }
    }
}

@Composable
private fun AlbumSection(
    albums: List<RecipeAlbum>,
    selectedAlbumId: Long?,
    onAlbumSelected: (Long?) -> Unit,
    newAlbumName: String,
    onNewAlbumNameChange: (String) -> Unit,
    onCreateAlbum: () -> Unit
) {
    Text("Album", color = Ink, fontFamily = FontFamily.Serif, fontSize = 27.sp)
    if (albums.isEmpty()) {
        Text(
            "Recipes are saved to All Recipes by default. You can also create albums such as Dinner, Desserts, or Meal Prep.",
            color = Ink.copy(alpha = 0.7f),
            fontSize = 17.sp
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                FilterChip(
                    selected = selectedAlbumId == null,
                    onClick = { onAlbumSelected(null) },
                    label = { Text("All Recipes", fontSize = 16.sp) }
                )
            }
            items(albums, key = { it.id }) { album ->
                FilterChip(
                    selected = selectedAlbumId == album.id,
                    onClick = { onAlbumSelected(album.id) },
                    label = { Text(album.name, fontSize = 16.sp) }
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newAlbumName,
            onValueChange = onNewAlbumNameChange,
            label = { Text("New album name") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onCreateAlbum,
            enabled = newAlbumName.isNotBlank(),
            modifier = Modifier.heightIn(min = 56.dp)
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("  Create Album", fontSize = 17.sp)
        }
    }
}

@Composable
fun ImportRecipeScreen(
    onBack: () -> Unit,
    onExtract: (recipe: ParsedRecipe, photoUri: String?) -> Unit
) {
    var importMode by remember { mutableStateOf(ImportMode.PasteText) }
    var rawText by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var useImageAsPhoto by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var urlImportResult by remember { mutableStateOf<UrlImportResult?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                isLoading = true
                message = null
                runCatching { RecipeImportService.recognizeText(context, uri) }
                    .onSuccess {
                        rawText = it
                        message = if (it.isBlank()) {
                            "No readable text was found. Try a clearer image or paste the text."
                        } else {
                            "Text extracted. Review it below before continuing."
                        }
                    }
                    .onFailure {
                        message = "Text could not be read from this image. Try a clearer image."
                    }
                isLoading = false
            }
        }
    }

    RecipePage(title = "Import recipe", onBack = onBack, scrollable = true) {
        Text(
            "Choose a source. You can review the extracted text before creating the recipe.",
            color = Ink.copy(alpha = 0.7f),
            fontSize = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImportMode.entries.forEach { mode ->
                FilterChip(
                    selected = importMode == mode,
                    onClick = {
                        importMode = mode
                        message = null
                    },
                    label = { Text(mode.label, fontSize = 16.sp) },
                    leadingIcon = { Icon(mode.icon, null, modifier = Modifier.size(22.dp)) }
                )
            }
        }
        when (importMode) {
            ImportMode.PasteText -> Text(
                "Paste recipe text from a website, social post, email, or document.",
                color = Ink.copy(alpha = 0.7f),
                fontSize = 17.sp
            )
            ImportMode.Photo -> {
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier.heightIn(min = 58.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null)
                    Text(
                        if (selectedImageUri == null) "  Choose Screenshot or Photo" else "  Choose Another Image",
                        fontSize = 17.sp
                    )
                }
                if (selectedImageUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useImageAsPhoto,
                            onCheckedChange = { useImageAsPhoto = it }
                        )
                        Text("Use this image as the recipe photo", fontSize = 17.sp, color = Ink)
                    }
                }
            }
            ImportMode.Link -> {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlImportResult = null
                    },
                    label = { Text("Recipe URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            message = null
                            runCatching { RecipeImportService.importFromUrl(url) }
                                .onSuccess { result ->
                                    urlImportResult = result
                                    rawText = result.recipe.toEditableImportText()
                                    message = result.method.label
                                }
                                .onFailure {
                                    urlImportResult = null
                                    rawText = ""
                                    message = "This page could not be imported cleanly. Try copying and pasting the recipe text instead."
                                }
                            isLoading = false
                        }
                    },
                    enabled = url.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.heightIn(min = 58.dp)
                ) {
                    Icon(Icons.Rounded.Link, null)
                    Text("  Import from Link", fontSize = 17.sp)
                }
            }
        }
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent)
                Text(
                    if (importMode == ImportMode.Photo) "Reading text from image…" else "Importing link…",
                    color = Ink,
                    fontSize = 17.sp
                )
            }
        }
        message?.let {
            Text(
                it,
                color = if (it.startsWith("This page") || it.startsWith("Text could") || it.startsWith("No readable")) {
                    Color(0xFF9B2C2C)
                } else {
                    Color(0xFF55704C)
                },
                fontSize = 17.sp
            )
        }
        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            label = { Text("Recipe text") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
        )
        Button(
            onClick = {
                val parsed = if (importMode == ImportMode.Link && urlImportResult != null) {
                    urlImportResult!!.recipe
                } else {
                    RecipeParser.parse(rawText)
                }
                onExtract(
                    parsed,
                    selectedImageUri?.toString()
                        .takeIf { importMode == ImportMode.Photo && useImageAsPhoto }
                )
            },
            enabled = rawText.isNotBlank() && !isLoading,
            modifier = Modifier.heightIn(min = 58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Icon(Icons.Rounded.AutoAwesome, null)
            Text("  Extract Recipe", fontSize = 18.sp)
        }
    }
}

private fun ParsedRecipe.toEditableImportText(): String = buildList {
    if (title.isNotBlank()) add(title)
    if (notes.isNotBlank()) add(notes)
    if (prepTime.isNotBlank()) add("Prep time: $prepTime")
    if (cookTime.isNotBlank()) add("Cook time: $cookTime")
    if (totalTime.isNotBlank()) add("Total time: $totalTime")
    if (servings.isNotBlank()) add("Servings: $servings")
    if (ingredients.isNotBlank()) {
        add("Ingredients")
        add(ingredients)
    }
    if (instructions.isNotBlank()) {
        add("Instructions")
        add(instructions)
    }
}.joinToString("\n\n")

private enum class ImportMode(val label: String, val icon: ImageVector) {
    PasteText("Paste Text", Icons.Rounded.ContentPaste),
    Photo("Screenshot / Photo", Icons.Rounded.PhotoLibrary),
    Link("Import from Link", Icons.Rounded.Link)
}

@Composable
fun RecipeReviewScreen(
    initialRecipe: ParsedRecipe,
    initialPhotoUri: String?,
    albums: List<RecipeAlbum>,
    onBack: () -> Unit,
    onCreateAlbum: suspend (String) -> Long,
    onSave: suspend (ParsedRecipe, Long?, String?, List<String>, String?, RecipeVisibility) -> Long,
    onViewRecipe: (Long) -> Unit
) {
    var recipe by remember(initialRecipe) { mutableStateOf(initialRecipe) }
    var photoUris by remember(initialPhotoUri) {
        mutableStateOf(listOfNotNull(initialPhotoUri?.let(Uri::parse)))
    }
    var photoUri by remember(initialPhotoUri) {
        mutableStateOf(initialPhotoUri?.let(Uri::parse))
    }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAlbumId by remember { mutableStateOf<Long?>(null) }
    var visibility by remember { mutableStateOf(RecipeVisibility.Private) }
    var newAlbumName by remember { mutableStateOf("") }
    var showOriginalText by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var savedRecipeId by remember { mutableStateOf<Long?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var mediaMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun retainMediaPermission(uri: Uri?) {
        if (uri == null) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach(::retainMediaPermission)
        val combined = (photoUris + uris).distinct()
        photoUris = combined
        if (photoUri == null) photoUri = combined.firstOrNull()
    }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && mediaSize(context, uri) > MAX_VIDEO_BYTES) {
            mediaMessage = "Video must be 50 MB or smaller."
        } else if (uri != null) {
            retainMediaPermission(uri)
            videoUri = uri
            mediaMessage = null
        }
    }

    RecipePage(title = "Review imported recipe", onBack = onBack, scrollable = true) {
        Text("Correct anything the local parser did not identify accurately.", color = Ink.copy(alpha = 0.7f), fontSize = 18.sp)
        recipe.extractionMethod?.let {
            Surface(
                color = Color(0xFFE3EBDD),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    it,
                    color = Color(0xFF3F5F38),
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        RecipeForm(
            recipe = recipe,
            onRecipeChange = { recipe = it },
            includeMedia = true,
            photoName = if (photoUris.isEmpty()) null else "${photoUris.size} photo${if (photoUris.size == 1) "" else "s"}",
            videoName = videoUri?.lastPathSegment,
            onAddPhoto = { photoPicker.launch(arrayOf("image/*")) },
            onAddVideo = { videoPicker.launch(arrayOf("video/*")) }
        )
        PhotoSelection(
            photos = photoUris,
            mainPhoto = photoUri,
            onSelectMain = { photoUri = it },
            onRemove = { removed ->
                photoUris = photoUris - removed
                if (photoUri == removed) photoUri = photoUris.firstOrNull()
            }
        )
        mediaMessage?.let { Text(it, color = Color(0xFF9B2C2C), fontSize = 17.sp) }
        if (recipe.imageUrl != null) {
            RecipeField("Image URL", recipe.imageUrl.orEmpty()) {
                recipe = recipe.copy(imageUrl = it.ifBlank { null })
            }
        }
        if (recipe.sourceUrl != null) {
            RecipeField("Source URL", recipe.sourceUrl.orEmpty()) {
                recipe = recipe.copy(sourceUrl = it.ifBlank { null })
            }
        }
        OutlinedButton(onClick = { showOriginalText = !showOriginalText }) {
            Icon(Icons.Rounded.ExpandMore, null)
            Text(
                if (showOriginalText) "  Hide Original Text" else "  Show Original Text",
                fontSize = 17.sp
            )
        }
        if (showOriginalText) {
            OutlinedTextField(
                value = recipe.originalRawText,
                onValueChange = { recipe = recipe.copy(originalRawText = it) },
                label = { Text("Original Text") },
                minLines = 7,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AlbumSection(
            albums = albums,
            selectedAlbumId = selectedAlbumId,
            onAlbumSelected = { selectedAlbumId = it },
            newAlbumName = newAlbumName,
            onNewAlbumNameChange = { newAlbumName = it },
            onCreateAlbum = {
                if (newAlbumName.isNotBlank()) {
                    scope.launch {
                        selectedAlbumId = onCreateAlbum(newAlbumName)
                        newAlbumName = ""
                    }
                }
            }
        )
        VisibilityChooser(visibility = visibility, onVisibilityChange = { visibility = it })
        SaveButton(
            enabled = !isSaving && savedRecipeId == null,
            isSaving = isSaving,
            onClick = {
                validationMessage = when {
                    recipe.title.isBlank() -> "Add a recipe title before saving."
                    recipe.ingredients.isBlank() && recipe.instructions.isBlank() ->
                        "Add ingredients or instructions before saving."
                    else -> null
                }
                if (validationMessage == null) {
                    scope.launch {
                        isSaving = true
                        savedRecipeId = onSave(
                            recipe,
                            selectedAlbumId,
                            photoUri?.toString(),
                            photoUris.map(Uri::toString),
                            videoUri?.toString(),
                            visibility
                        )
                        isSaving = false
                        savedRecipeId?.let(onViewRecipe)
                    }
                }
            }
        )
        validationMessage?.let {
            Text(it, color = Color(0xFF9B2C2C), fontSize = 18.sp)
        }
        savedRecipeId?.let { id ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "Recipe saved to ${
                        albums.firstOrNull { it.id == selectedAlbumId }?.name
                            ?: "All Recipes"
                    }.",
                    color = Color(0xFF55704C),
                    fontSize = 18.sp
                )
                OutlinedButton(onClick = { onViewRecipe(id) }) {
                    Text("View Recipe", fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun RecipeForm(
    recipe: ParsedRecipe,
    onRecipeChange: (ParsedRecipe) -> Unit,
    includeMedia: Boolean = false,
    photoName: String? = null,
    videoName: String? = null,
    onAddPhoto: () -> Unit = {},
    onAddVideo: () -> Unit = {}
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    RecipeField("Recipe title", recipe.title) { onRecipeChange(recipe.copy(title = it)) }
    RecipeField("Description / notes", recipe.notes, minLines = 3) { onRecipeChange(recipe.copy(notes = it)) }
    if (isPhone) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { RecipeField("Prep time", recipe.prepTime) { onRecipeChange(recipe.copy(prepTime = it)) } }
                Box(Modifier.weight(1f)) { RecipeField("Cook time", recipe.cookTime) { onRecipeChange(recipe.copy(cookTime = it)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { RecipeField("Total time", recipe.totalTime) { onRecipeChange(recipe.copy(totalTime = it)) } }
                Box(Modifier.weight(1f)) { RecipeField("Servings", recipe.servings) { onRecipeChange(recipe.copy(servings = it)) } }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) { RecipeField("Prep time", recipe.prepTime) { onRecipeChange(recipe.copy(prepTime = it)) } }
            Box(Modifier.weight(1f)) { RecipeField("Cook time", recipe.cookTime) { onRecipeChange(recipe.copy(cookTime = it)) } }
            Box(Modifier.weight(1f)) { RecipeField("Total time", recipe.totalTime) { onRecipeChange(recipe.copy(totalTime = it)) } }
            Box(Modifier.weight(1f)) { RecipeField("Servings", recipe.servings) { onRecipeChange(recipe.copy(servings = it)) } }
        }
    }
    RecipeField("Ingredients", recipe.ingredients, minLines = 6) { onRecipeChange(recipe.copy(ingredients = it)) }
    RecipeField("Instructions", recipe.instructions, minLines = 7) { onRecipeChange(recipe.copy(instructions = it)) }
    if (includeMedia) {
        if (isPhone) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MediaButton(photoName ?: "Add photo", Icons.Rounded.Image, onAddPhoto, modifier = Modifier.fillMaxWidth())
                MediaButton(videoName ?: "Add video", Icons.Rounded.Videocam, onAddVideo, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MediaButton(photoName ?: "Add photo", Icons.Rounded.Image, onAddPhoto)
                MediaButton(videoName ?: "Add video", Icons.Rounded.Videocam, onAddVideo)
            }
        }
    }
}

@Composable
private fun VisibilityChooser(
    visibility: RecipeVisibility,
    onVisibilityChange: (RecipeVisibility) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Visibility", color = Ink, fontFamily = FontFamily.Serif, fontSize = 27.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VisibilityOption(
                label = "Private",
                selected = visibility == RecipeVisibility.Private,
                modifier = Modifier.weight(1f),
                onClick = { onVisibilityChange(RecipeVisibility.Private) }
            )
            VisibilityOption(
                label = "Public",
                selected = visibility == RecipeVisibility.Public,
                modifier = Modifier.weight(1f),
                onClick = { onVisibilityChange(RecipeVisibility.Public) }
            )
        }
        Text(
            if (visibility == RecipeVisibility.Public) {
                "Public recipes can appear on your profile and Explore."
            } else {
                "Private recipes stay in your recipe albums."
            },
            color = Ink.copy(alpha = 0.62f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun VisibilityOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF748463).copy(alpha = 0.82f) else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = if (selected) Color.White else Ink.copy(alpha = 0.74f),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun PhotoSelection(
    photos: List<Uri>,
    mainPhoto: Uri?,
    onSelectMain: (Uri) -> Unit,
    onRemove: (Uri) -> Unit
) {
    if (photos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose the main photo for the recipe tile", color = Ink, fontSize = 18.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(photos, key = { it.toString() }) { uri ->
                val selected = uri == mainPhoto
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { onSelectMain(uri) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier
                            .size(150.dp, 105.dp)
                            .border(
                                width = if (selected) 4.dp else 1.dp,
                                color = if (selected) Accent else Ink.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { it.setImageURI(uri) },
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selected) "Main photo" else "Select as main", fontSize = 14.sp)
                        IconButton(onClick = { onRemove(uri) }) {
                            Icon(Icons.Rounded.Delete, "Remove photo", tint = Color(0xFF9B2C2C))
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L

private fun mediaSize(context: android.content.Context, uri: Uri): Long {
    val descriptorLength = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull() ?: -1L
    if (descriptorLength >= 0) return descriptorLength
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)
}

@Composable
private fun RecipeField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
    )
}

@Composable
private fun MediaButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(icon, null)
        Text("  $label", fontSize = 17.sp, maxLines = 1)
    }
}

@Composable
private fun SaveButton(enabled: Boolean = true, isSaving: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
            Text("  Saving Recipe…", fontSize = 19.sp)
        } else {
            Text("Save Recipe", fontSize = 19.sp)
        }
    }
}

@Composable
private fun RecipePage(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = false,
    phoneVerticalOffsetFraction: Float = 0f,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPhone = configuration.screenWidthDp < 700
    val phoneVerticalOffset = (configuration.screenHeightDp * phoneVerticalOffsetFraction).dp
    val bodyModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(
                start = if (isPhone) 22.dp else 40.dp,
                end = if (isPhone) 22.dp else 40.dp,
                top = if (isPhone) 54.dp + phoneVerticalOffset else 24.dp,
                bottom = if (isPhone) 18.dp else 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (isPhone) 16.dp else 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(if (isPhone) 46.dp else 56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Ink, modifier = Modifier.size(if (isPhone) 28.dp else 32.dp))
            }
            Text(title, color = Ink, fontFamily = FontFamily.Serif, fontSize = if (isPhone) 31.sp else 36.sp)
        }
        Column(
            modifier = bodyModifier.fillMaxWidth().padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}
