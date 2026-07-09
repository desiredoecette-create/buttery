package com.buttery.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.buttery.app.R
import com.buttery.app.data.AccountResult
import com.buttery.app.data.RecipeShare
import com.buttery.app.data.SharedRecipeSnapshot
import com.buttery.app.data.UserProfile
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeAlbum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val AccountNavy = Color(0xFF111A23)
private val AccountPanel = Color(0xFF1B2732)
private val AccountButter = Color(0xFFFFC857)
private val AccountCream = Color(0xFFF5EEDC)
private val AccountInk = Color(0xFF332D26)

private enum class AuthMode { WELCOME, SIGN_UP, SIGN_IN }

@Composable
fun LoginScreen(
    onSignUp: suspend (String, String, String, String) -> AccountResult,
    onSignIn: suspend (String, String) -> AccountResult,
    onGoogleSignIn: suspend () -> AccountResult,
    onCompleteGoogleSignUp: suspend (String, String, String, String) -> AccountResult
) {
    var mode by remember { mutableStateOf(AuthMode.WELCOME) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var providerSignInRunning by remember { mutableStateOf(false) }
    var emailSignInRunning by remember { mutableStateOf(false) }
    var pendingGoogleAccount by remember {
        mutableStateOf<AccountResult.UsernameRequired?>(null)
    }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF293A43), AccountNavy), radius = 1500f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.82f),
            horizontalArrangement = Arrangement.spacedBy(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.buttery_wordmark),
                    contentDescription = "Buttery",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                )
                Text(
                    "Save recipes. Share the good ones.",
                    color = AccountCream,
                    fontFamily = FontFamily.Serif,
                    fontSize = 23.sp
                )
            }
            Surface(
                modifier = Modifier.weight(0.82f),
                color = AccountCream,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(30.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        when (mode) {
                            AuthMode.WELCOME -> "Welcome to Buttery"
                            AuthMode.SIGN_UP -> "Create your account"
                            AuthMode.SIGN_IN -> "Welcome back"
                        },
                        color = AccountInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 30.sp
                    )
                    if (mode == AuthMode.WELCOME) {
                        ProviderButton("Continue with Google") {
                            if (!providerSignInRunning) {
                                providerSignInRunning = true
                                message = null
                                scope.launch {
                                    val result = onGoogleSignIn()
                                    when (result) {
                                        is AccountResult.UsernameRequired -> {
                                            pendingGoogleAccount = result
                                            displayName = result.displayName
                                        }
                                        is AccountResult.Error -> message = result.message
                                        is AccountResult.Success -> Unit
                                    }
                                    providerSignInRunning = false
                                }
                            }
                        }
                        Button(
                            onClick = { mode = AuthMode.SIGN_UP; message = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            Icon(Icons.Rounded.Person, null, tint = AccountNavy)
                            Text("  Sign up with email", color = AccountNavy)
                        }
                        TextButton(
                            onClick = { mode = AuthMode.SIGN_IN; message = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Log in with email", color = AccountNavy) }
                    } else {
                        if (mode == AuthMode.SIGN_UP) {
                            AccountField(username, { username = it }, "Username")
                            AccountField(displayName, { displayName = it }, "Display name (optional)")
                        }
                        AccountField(email, { email = it }, "Email")
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (!emailSignInRunning) {
                                    emailSignInRunning = true
                                    message = null
                                    scope.launch {
                                        val result = if (mode == AuthMode.SIGN_UP) {
                                            onSignUp(username, email, password, displayName)
                                        } else {
                                            onSignIn(email, password)
                                        }
                                        message = (result as? AccountResult.Error)?.message
                                        emailSignInRunning = false
                                    }
                                }
                            },
                            enabled = !emailSignInRunning,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            Text(if (mode == AuthMode.SIGN_UP) "Create account" else "Log in", color = AccountNavy)
                        }
                        TextButton(onClick = { mode = AuthMode.WELCOME; message = null }) {
                            Text("Back to all sign-in options", color = AccountNavy)
                        }
                    }
                    message?.let {
                        Text(it, color = Color(0xFF9B493D), fontSize = 14.sp)
                    }
                    pendingGoogleAccount?.let { pending ->
                        Text(
                            "Choose a username to finish creating your account.",
                            color = AccountInk,
                            fontSize = 16.sp
                        )
                        AccountField(username, { username = it }, "Username")
                        Button(
                            onClick = {
                                if (!providerSignInRunning) {
                                    providerSignInRunning = true
                                    message = null
                                    scope.launch {
                                        val result = onCompleteGoogleSignUp(
                                            pending.userId,
                                            pending.email,
                                            pending.displayName,
                                            username
                                        )
                                        message = when (result) {
                                            is AccountResult.Error -> result.message
                                            else -> null
                                        }
                                        if (result is AccountResult.Success) pendingGoogleAccount = null
                                        providerSignInRunning = false
                                    }
                                }
                            },
                            enabled = !providerSignInRunning,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            Text("Finish Google sign-up", color = AccountNavy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
    ) {
        Icon(Icons.Rounded.AccountCircle, null)
        Text("  $label")
    }
}

@Composable
private fun AccountField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    inbox: List<RecipeShare>,
    onBack: () -> Unit,
    onSaveProfile: suspend (String, String?) -> AccountResult,
    onSignOut: () -> Unit,
    onViewShare: (RecipeShare) -> Unit,
    onAddShare: (RecipeShare) -> Unit,
    onDismissShare: (RecipeShare) -> Unit
) {
    var displayName by remember(profile.userId, profile.displayName) { mutableStateOf(profile.displayName) }
    var photoUri by remember(profile.userId, profile.profilePhotoUri) {
        mutableStateOf(profile.profilePhotoUri)
    }
    var message by remember { mutableStateOf<String?>(null) }
    var savingProfile by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            photoUri = runCatching {
                val destination = File(
                    context.filesDir,
                    "profile_${profile.userId}_${System.currentTimeMillis()}.jpg"
                )
                context.contentResolver.openInputStream(it).use { input ->
                    requireNotNull(input) { "Could not open selected photo." }
                    destination.outputStream().use(input::copyTo)
                }
                Uri.fromFile(destination).toString()
            }.getOrElse {
                message = "That photo could not be saved. Please choose another image."
                null
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF293A43), AccountNavy), radius = 1500f)
        ).padding(26.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AccountPanel)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    Text("  Home")
                }
                Text(
                    "Your Profile",
                    color = AccountCream,
                    fontFamily = FontFamily.Serif,
                    fontSize = 38.sp,
                    modifier = Modifier.padding(start = 20.dp)
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F3D39))
                ) {
                    Icon(Icons.Rounded.Logout, null)
                    Text("  Sign out")
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(
                    modifier = Modifier.weight(0.72f).fillMaxHeight(),
                    color = AccountCream,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        Modifier.padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ProfileAvatar(photoUri, 112)
                        OutlinedButton(
                            onClick = { picker.launch("image/*") },
                            enabled = !savingProfile
                        ) {
                            Icon(Icons.Rounded.Image, null)
                            Text("  Change photo")
                        }
                        OutlinedTextField(
                            value = profile.username,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Rounded.Person, null) },
                            supportingText = { Text("Usernames cannot be changed.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AccountField(displayName, { displayName = it }, "Display name")
                        OutlinedTextField(
                            value = profile.email,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Rounded.Email, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (!savingProfile) {
                                    savingProfile = true
                                    message = null
                                    scope.launch {
                                        message = when (val result = onSaveProfile(displayName, photoUri)) {
                                            is AccountResult.Success -> "Profile saved."
                                            is AccountResult.UsernameRequired -> "Choose a username to continue."
                                            is AccountResult.Error -> result.message
                                        }
                                        savingProfile = false
                                    }
                                }
                            },
                            enabled = !savingProfile,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            if (savingProfile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccountNavy
                                )
                                Text("  Saving…", color = AccountNavy)
                            } else {
                                Text("Save profile", color = AccountNavy)
                            }
                        }
                        message?.let { Text(it, color = AccountInk) }
                        Text(
                            "Settings and account preferences will live here.",
                            color = AccountInk.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1.28f).fillMaxHeight(),
                    color = AccountCream,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Inbox, null, tint = AccountInk)
                            Text(
                                "  Inbox",
                                color = AccountInk,
                                fontFamily = FontFamily.Serif,
                                fontSize = 28.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${inbox.size} received", color = AccountInk.copy(alpha = 0.6f))
                        }
                        if (inbox.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Shared recipes will appear here.", color = AccountInk.copy(alpha = 0.55f))
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(inbox, key = { it.shareId }) { share ->
                                    InboxCard(share, onViewShare, onAddShare, onDismissShare)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxCard(
    share: RecipeShare,
    onView: (RecipeShare) -> Unit,
    onAdd: (RecipeShare) -> Unit,
    onDismiss: (RecipeShare) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Share, null, tint = Color(0xFF687A48))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (share.status == "pending") {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(12.dp)
                                .background(Color(0xFFD7433B), CircleShape)
                        )
                    }
                    Text(
                        "${share.fromUsername} is sharing ${share.recipe.title} recipe with you!",
                        color = AccountInk,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (share.status == "added") Text("Added to your recipes", color = Color(0xFF687A48))
            }
            TextButton(onClick = { onView(share) }) { Text("View") }
            Button(
                onClick = { onAdd(share) },
                enabled = share.status != "added",
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccountButter,
                    disabledContainerColor = Color(0xFFB7B2A8),
                    disabledContentColor = Color(0xFFEFECE3)
                )
            ) {
                Text(
                    if (share.status == "added") "Added" else "Add to My Recipes",
                    color = if (share.status == "added") Color(0xFFEFECE3) else AccountNavy
                )
            }
            IconButton(onClick = { onDismiss(share) }) {
                Icon(Icons.Rounded.Close, "Dismiss")
            }
        }
    }
}

@Composable
fun ProfileAvatar(photoUri: String?, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(AccountButter),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri.isNullOrBlank()) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = "Profile",
                tint = AccountNavy,
                modifier = Modifier.size((sizeDp * 0.58f).dp)
            )
        } else {
            AsyncImage(
                model = photoUri,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_default_profile),
                error = painterResource(R.drawable.ic_default_profile),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ShareRecipeDialog(
    recipe: Recipe,
    findUser: suspend (String) -> UserProfile?,
    matchingUsernames: suspend (String) -> List<String>,
    onShare: suspend (UserProfile) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var foundUser by remember { mutableStateOf<UserProfile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var shared by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        val clean = username.trim()
        foundUser = null
        shared = false
        if (clean.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        suggestions = runCatching { matchingUsernames(clean) }
            .getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share ${recipe.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountField(username, {
                    username = it
                    message = null
                }, "Recipient username")
                if (suggestions.isNotEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "Matching users",
                                color = AccountInk.copy(alpha = 0.62f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                            suggestions.forEach { suggestion ->
                                TextButton(
                                    onClick = {
                                        username = suggestion
                                        suggestions = emptyList()
                                        foundUser = null
                                        message = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("@$suggestion", color = AccountInk)
                                }
                            }
                        }
                    }
                }
                message?.let {
                    Text(
                        it,
                        color = if (shared) Color(0xFF4F7A43) else Color(0xFF9B493D)
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = {
                    if (!loading) {
                        loading = true
                        message = null
                        scope.launch {
                            try {
                                val recipient = foundUser ?: findUser(username)
                                suggestions = emptyList()
                                if (recipient == null) {
                                    message = "No user found with that username."
                                } else {
                                    foundUser = recipient
                                    message = onShare(recipient).fold(
                                        onSuccess = {
                                            shared = true
                                            "Recipe shared with ${recipient.username}"
                                        },
                                        onFailure = { it.message ?: "Could not share recipe." }
                                    )
                                }
                            } catch (error: Throwable) {
                                message = error.localizedMessage ?: "Could not share recipe."
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                enabled = !shared && !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccountButter,
                    disabledContainerColor = Color(0xFF9A9A9A),
                    disabledContentColor = Color(0xFFE6E6E6)
                )
            ) { Text(if (loading) "Sharing…" else "Share", color = AccountNavy) }
        }
    )
}

@Composable
fun SharedRecipePreviewScreen(
    share: RecipeShare,
    senderProfile: UserProfile?,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    val recipe = share.recipe
    Box(
        Modifier.fillMaxSize().background(AccountNavy).padding(26.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AccountPanel)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                Text("  Inbox")
            }
            Row(
                modifier = Modifier.padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatar(senderProfile?.profilePhotoUri ?: share.fromProfilePhotoUri, 46)
                Column {
                    Text(
                        senderProfile?.displayName?.ifBlank { share.fromDisplayName }
                            ?: share.fromDisplayName.ifBlank { share.fromUsername },
                        color = AccountCream,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Text(
                        "@${share.fromUsername}",
                        color = AccountCream.copy(alpha = 0.68f),
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onAdd,
                enabled = share.status != "added",
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccountButter,
                    disabledContainerColor = Color(0xFFB7B2A8),
                    disabledContentColor = Color(0xFFEFECE3)
                )
            ) {
                Icon(
                    Icons.Rounded.Add,
                    null,
                    tint = if (share.status == "added") Color(0xFFEFECE3) else AccountNavy
                )
                Text(
                    if (share.status == "added") "  Added to My Recipes" else "  Add to My Recipes",
                    color = if (share.status == "added") Color(0xFFEFECE3) else AccountNavy
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.84f).align(Alignment.BottomCenter),
            shape = RoundedCornerShape(22.dp),
            color = AccountCream
        ) {
            LazyColumn(
                Modifier.padding(34.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (recipe.photoUris.isNotEmpty()) {
                    item { SharedPhotoGallery(recipe.photoUris) }
                }
                item {
                    Text(recipe.title, color = AccountInk, fontFamily = FontFamily.Serif, fontSize = 38.sp)
                    Text("Shared by ${share.fromUsername}", color = Color(0xFF745B33))
                }
                item { SnapshotSection("Ingredients", recipe.ingredients) }
                item { SnapshotSection("Instructions", recipe.instructions) }
                if (recipe.notes.isNotBlank()) item { SnapshotSection("Notes", recipe.notes) }
                recipe.videoUri?.let { videoUri ->
                    item { SharedRecipeVideo(videoUri) }
                }
            }
        }
    }
}

@Composable
private fun SharedPhotoGallery(photoUris: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        photoUris.forEach { photoUri ->
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ambient_extra_02),
                error = painterResource(R.drawable.ambient_extra_02),
                modifier = Modifier.size(300.dp, 184.dp).clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun SharedRecipeVideo(videoUri: String) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Video", color = AccountInk, fontFamily = FontFamily.Serif, fontSize = 24.sp)
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun SnapshotSection(title: String, body: String) {
    Text(title, color = AccountInk, fontFamily = FontFamily.Serif, fontSize = 24.sp)
    Text(body.ifBlank { "None provided." }, color = AccountInk, fontSize = 17.sp)
}

@Composable
fun AlbumPickerDialog(
    albums: List<RecipeAlbum>,
    onChoose: (Long?) -> Unit,
    onCreate: suspend (String) -> Long,
    onDismiss: () -> Unit
) {
    var newAlbum by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an album") },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                albums.forEach { album ->
                    OutlinedButton(onClick = { onChoose(album.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(album.name)
                    }
                }
                AccountField(newAlbum, { newAlbum = it }, "New album name")
                Text(
                    "Create the album first, then select it from the list.",
                    color = AccountInk.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        onChoose(onCreate(newAlbum.trim()))
                    }
                },
                enabled = newAlbum.isNotBlank()
            ) { Text("Create album") }
        }
    )
}
