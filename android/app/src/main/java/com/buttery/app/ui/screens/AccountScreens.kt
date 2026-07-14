package com.buttery.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.buttery.app.R
import com.buttery.app.data.AccountResult
import com.buttery.app.data.CommunityRecipe
import com.buttery.app.data.RecipeShare
import com.buttery.app.data.SharedRecipeSnapshot
import com.buttery.app.data.SubscriberNotification
import com.buttery.app.data.UserProfile
import com.buttery.app.domain.Recipe
import com.buttery.app.domain.RecipeAlbum
import com.buttery.app.ui.ButteryLayoutMode
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
fun ProfileMenuScreen(
    profile: UserProfile,
    hasInboxNotification: Boolean,
    onBack: () -> Unit,
    onOpenMyProfile: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF293A43), AccountNavy), radius = 1500f)
        ).padding(horizontal = if (isPhone) 22.dp else 30.dp, vertical = if (isPhone) 56.dp else 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = AccountCream,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth(if (isPhone) 0.96f else 0.58f)
        ) {
            Column(
                Modifier.padding(if (isPhone) 22.dp else 30.dp),
                verticalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(profile.profilePhotoUri, if (isPhone) 78 else 104)
                Text(
                    profile.displayName.ifBlank { profile.username },
                    color = AccountInk,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (isPhone) 29.sp else 34.sp
                )
                ProfileMenuButton("@${profile.username}", Icons.Rounded.Person, onOpenMyProfile)
                ProfileMenuButton(
                    label = if (hasInboxNotification) "Inbox • New" else "Inbox",
                    icon = Icons.Rounded.Inbox,
                    onClick = onOpenInbox
                )
                ProfileMenuButton("Settings", Icons.Rounded.Settings, onOpenSettings)
                ProfileMenuButton("Sign out", Icons.Rounded.Logout, onSignOut, danger = true)
                TextButton(onClick = onBack) { Text("Back to dashboard", color = AccountNavy) }
            }
        }
    }
}

@Composable
private fun ProfileMenuButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Color(0xFF6F3D39) else AccountButter
        )
    ) {
        Icon(icon, null, tint = if (danger) Color.White else AccountNavy)
        Text("  $label", color = if (danger) Color.White else AccountNavy, fontSize = 18.sp)
    }
}

@Composable
fun LoginScreen(
    layoutMode: ButteryLayoutMode = ButteryLayoutMode.Tablet,
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
    var termsAccepted by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var pendingGoogleAccount by remember {
        mutableStateOf<AccountResult.UsernameRequired?>(null)
    }
    val scope = rememberCoroutineScope()

    if (layoutMode == ButteryLayoutMode.Phone) {
        PhoneLoginScreenContent(
            mode = mode,
            onModeChange = { mode = it; message = null },
            username = username,
            onUsernameChange = { username = it },
            displayName = displayName,
            onDisplayNameChange = { displayName = it },
            email = email,
            onEmailChange = { email = it },
            password = password,
            onPasswordChange = { password = it },
            message = message,
            providerSignInRunning = providerSignInRunning,
            emailSignInRunning = emailSignInRunning,
            pendingGoogleAccount = pendingGoogleAccount,
            termsAccepted = termsAccepted,
            onTermsAcceptedChange = { termsAccepted = it },
            onShowTerms = { showTerms = true },
            onGoogleClick = {
                if (!providerSignInRunning) {
                    providerSignInRunning = true
                    message = "Signing you in…"
                    scope.launch {
                        val result = onGoogleSignIn()
                        when (result) {
                            is AccountResult.UsernameRequired -> {
                                pendingGoogleAccount = result
                                displayName = result.displayName
                                message = "Choose a username to finish Google sign-up."
                            }
                            is AccountResult.Error -> message = result.message
                            is AccountResult.Success -> message = null
                        }
                        providerSignInRunning = false
                    }
                }
            },
            onEmailSubmit = {
                if (!emailSignInRunning) {
                    if (mode == AuthMode.SIGN_UP && !termsAccepted) {
                        message = "Accept the Terms of Use to create your account."
                        return@PhoneLoginScreenContent
                    }
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
            onCompleteGoogle = {
                val pending = pendingGoogleAccount ?: return@PhoneLoginScreenContent
                if (!providerSignInRunning) {
                    if (!termsAccepted) {
                        message = "Accept the Terms of Use to create your account."
                        return@PhoneLoginScreenContent
                    }
                    providerSignInRunning = true
                    message = "Finishing sign-up…"
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
            }
        )
        if (showTerms) {
            TermsOfUseDialog(onDismiss = { showTerms = false })
        }
        return
    }

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
                        if (mode == AuthMode.SIGN_UP) {
                            TermsAcceptanceRow(
                                accepted = termsAccepted,
                                onAcceptedChange = { termsAccepted = it },
                                onShowTerms = { showTerms = true }
                            )
                        }
                        Button(
                            onClick = {
                                if (!emailSignInRunning) {
                                    emailSignInRunning = true
                                    message = null
                                    scope.launch {
                                        val result = if (mode == AuthMode.SIGN_UP) {
                                            if (!termsAccepted) {
                                                AccountResult.Error("Accept the Terms of Use to create your account.")
                                            } else {
                                                onSignUp(username, email, password, displayName)
                                            }
                                        } else {
                                            onSignIn(email, password)
                                        }
                                        message = (result as? AccountResult.Error)?.message
                                        emailSignInRunning = false
                                    }
                                }
                            },
                            enabled = !emailSignInRunning && (mode != AuthMode.SIGN_UP || termsAccepted),
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
                        TermsAcceptanceRow(
                            accepted = termsAccepted,
                            onAcceptedChange = { termsAccepted = it },
                            onShowTerms = { showTerms = true }
                        )
                        Button(
                            onClick = {
                                if (!providerSignInRunning && termsAccepted) {
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
                            enabled = !providerSignInRunning && termsAccepted,
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
    if (showTerms) {
        TermsOfUseDialog(onDismiss = { showTerms = false })
    }
}

@Composable
private fun PhoneLoginScreenContent(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    message: String?,
    providerSignInRunning: Boolean,
    emailSignInRunning: Boolean,
    pendingGoogleAccount: AccountResult.UsernameRequired?,
    termsAccepted: Boolean,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onShowTerms: () -> Unit,
    onGoogleClick: () -> Unit,
    onEmailSubmit: () -> Unit,
    onCompleteGoogle: () -> Unit
) {
    val loginVerticalOffset = (LocalConfiguration.current.screenHeightDp * 0.07f).dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF15130F), AccountNavy, Color(0xFF293A43))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 40.dp + loginVerticalOffset,
                    bottom = 40.dp
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.buttery_wordmark),
                contentDescription = "Buttery",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .heightIn(max = 150.dp)
            )
            Text(
                "Glad to see you!",
                color = AccountCream,
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp
            )
            Text(
                "Sign in to save, share, and discover recipes.",
                color = AccountCream.copy(alpha = 0.78f),
                fontSize = 16.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AccountCream,
                shape = RoundedCornerShape(30.dp),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        when {
                            pendingGoogleAccount != null -> "Finish Google sign-up"
                            mode == AuthMode.WELCOME -> "Choose how to continue"
                            mode == AuthMode.SIGN_UP -> "Sign up with email"
                            else -> "Log in with email"
                        },
                        color = AccountInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pendingGoogleAccount != null) {
                        Text(
                            "Choose a username so others can find your recipe book.",
                            color = AccountInk.copy(alpha = 0.72f),
                            fontSize = 15.sp
                        )
                        AccountField(username, onUsernameChange, "Username")
                        TermsAcceptanceRow(
                            accepted = termsAccepted,
                            onAcceptedChange = onTermsAcceptedChange,
                            onShowTerms = onShowTerms
                        )
                        Button(
                            onClick = onCompleteGoogle,
                            enabled = !providerSignInRunning && termsAccepted,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            if (providerSignInRunning) {
                                CircularProgressIndicator(
                                    color = AccountNavy,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("  Finishing…", color = AccountNavy)
                            } else {
                                Text("Finish Google sign-up", color = AccountNavy)
                            }
                        }
                    } else if (mode == AuthMode.WELCOME) {
                        ProviderButton(if (providerSignInRunning) "Signing you in…" else "Continue with Google") {
                            onGoogleClick()
                        }
                        Button(
                            onClick = { onModeChange(AuthMode.SIGN_UP) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            Icon(Icons.Rounded.Person, null, tint = AccountNavy)
                            Text("  Sign up with email", color = AccountNavy)
                        }
                        TextButton(
                            onClick = { onModeChange(AuthMode.SIGN_IN) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Log in with email", color = AccountNavy)
                        }
                    } else {
                        if (mode == AuthMode.SIGN_UP) {
                            AccountField(username, onUsernameChange, "Username")
                            AccountField(displayName, onDisplayNameChange, "Display name (optional)")
                        }
                        AccountField(email, onEmailChange, "Email")
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (mode == AuthMode.SIGN_UP) {
                            TermsAcceptanceRow(
                                accepted = termsAccepted,
                                onAcceptedChange = onTermsAcceptedChange,
                                onShowTerms = onShowTerms
                            )
                        }
                        Button(
                            onClick = onEmailSubmit,
                            enabled = !emailSignInRunning && (mode != AuthMode.SIGN_UP || termsAccepted),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            if (emailSignInRunning) {
                                CircularProgressIndicator(
                                    color = AccountNavy,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("  Working…", color = AccountNavy)
                            } else {
                                Text(if (mode == AuthMode.SIGN_UP) "Create account" else "Log in", color = AccountNavy)
                            }
                        }
                        TextButton(onClick = { onModeChange(AuthMode.WELCOME) }) {
                            Text("Back to all sign-in options", color = AccountNavy)
                        }
                    }

                    message?.let {
                        Text(
                            it,
                            color = if (it.contains("Signing", ignoreCase = true) || it.contains("Finishing", ignoreCase = true)) {
                                AccountInk.copy(alpha = 0.7f)
                            } else {
                                Color(0xFF9B493D)
                            },
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsAcceptanceRow(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onShowTerms: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text("I agree to Buttery's Terms of Use.", color = AccountInk, fontSize = 14.sp)
            TextButton(onClick = onShowTerms) {
                Text("Read Terms of Use", color = AccountNavy)
            }
        }
    }
}

@Composable
private fun TermsOfUseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buttery Terms of Use") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Effective July 13, 2026")
                Text("You must be at least 13 years old to create a Buttery account and must provide accurate account information.")
                Text("You keep ownership of recipes and media you submit. You give Buttery permission to store, process, and display content as needed to provide sharing, profiles, subscriptions, and recipe discovery.")
                Text("Do not upload unlawful, abusive, misleading, infringing, sexually explicit, dangerous, or privacy-violating content. Do not harass other users or misuse sharing and subscription features.")
                Text("Buttery may remove content or suspend accounts to protect users, comply with law, or enforce these terms. Features may change, and the service is provided without a guarantee that it will always be uninterrupted or error-free.")
                Text("You may permanently delete your account and associated Buttery cloud data from Settings. These terms are governed by applicable law where you live.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccountButter)) {
                Text("Close", color = AccountNavy)
            }
        }
    )
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
    onOpenPublicProfile: (String) -> Unit,
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
                        Button(
                            onClick = { onOpenPublicProfile(profile.userId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                        ) {
                            Icon(Icons.Rounded.Person, null, tint = AccountNavy)
                            Text("  View @${profile.username}", color = AccountNavy)
                        }
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
                                    InboxCard(
                                        share = share,
                                        isPhone = false,
                                        onView = onViewShare,
                                        onAdd = onAddShare,
                                        onDismiss = onDismissShare,
                                        onOpenSenderProfile = { onOpenPublicProfile(share.fromUserId) }
                                    )
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
fun SettingsScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    onSaveProfile: suspend (String, String?) -> AccountResult,
    onDeleteAccount: suspend () -> Result<Unit>,
    onAccountDeleted: () -> Unit,
    onSignOut: () -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    var displayName by remember(profile.userId, profile.displayName) { mutableStateOf(profile.displayName) }
    var photoUri by remember(profile.userId, profile.profilePhotoUri) { mutableStateOf(profile.profilePhotoUri) }
    var message by remember { mutableStateOf<String?>(null) }
    var savingProfile by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            photoUri = runCatching {
                val destination = File(context.filesDir, "profile_${profile.userId}_${System.currentTimeMillis()}.jpg")
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
        ).padding(
            start = if (isPhone) 18.dp else 26.dp,
            end = if (isPhone) 18.dp else 26.dp,
            top = if (isPhone) 54.dp else 26.dp,
            bottom = if (isPhone) 18.dp else 26.dp
        )
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 14.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AccountPanel)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    if (!isPhone) Text("  Menu")
                }
                Text(
                    "Settings",
                    color = AccountCream,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (isPhone) 34.sp else 38.sp,
                    modifier = Modifier.padding(start = if (isPhone) 14.dp else 20.dp)
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onSignOut, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F3D39))) {
                    Icon(Icons.Rounded.Logout, null)
                    if (!isPhone) Text("  Sign out")
                }
            }
            Surface(
                modifier = if (isPhone) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth(0.62f).fillMaxHeight().align(Alignment.CenterHorizontally)
                },
                color = AccountCream,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(if (isPhone) 20.dp else 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 14.dp)
                ) {
                    ProfileAvatar(photoUri, if (isPhone) 92 else 122)
                    Button(
                        onClick = { onOpenPublicProfile(profile.userId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = AccountNavy)
                        Text("  View @${profile.username}", color = AccountNavy)
                    }
                    OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !savingProfile) {
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
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AccountNavy)
                            Text("  Saving…", color = AccountNavy)
                        } else {
                            Text("Save profile", color = AccountNavy)
                        }
                    }
                    message?.let { Text(it, color = AccountInk) }
                    Spacer(Modifier.size(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF6F3D39).copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Delete Account",
                                color = Color(0xFF6F3D39),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Permanently deletes your Buttery account, profile, recipes on this device, shared-recipe records, public recipes, subscriptions, likes, and uploaded media. This cannot be undone.",
                                color = AccountInk.copy(alpha = 0.72f),
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !deletingAccount,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F3D39))
                            ) {
                                if (deletingAccount) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Text("  Deleting…", color = Color.White)
                                } else {
                                    Icon(Icons.Rounded.Delete, null, tint = Color.White)
                                    Text("  Delete My Account", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!deletingAccount) showDeleteConfirmation = false },
            title = { Text("Permanently delete your account?") },
            text = {
                Text("Your account and associated Buttery data will be permanently deleted. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingAccount = true
                        showDeleteConfirmation = false
                        message = null
                        scope.launch {
                            val result = onDeleteAccount()
                            if (result.isSuccess) {
                                onAccountDeleted()
                            } else {
                                message = result.exceptionOrNull()?.localizedMessage
                                    ?: "Your account could not be deleted. Please try again."
                                deletingAccount = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F3D39))
                ) {
                    Text("Delete Account Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun InboxScreen(
    inbox: List<RecipeShare>,
    subscriberNotifications: List<SubscriberNotification>,
    onBack: () -> Unit,
    onViewShare: (RecipeShare) -> Unit,
    onAddShare: (RecipeShare) -> Unit,
    onDismissShare: (RecipeShare) -> Unit,
    onOpenPublicProfile: (String) -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF293A43), AccountNavy), radius = 1500f)
        ).padding(
            start = if (isPhone) 18.dp else 26.dp,
            end = if (isPhone) 18.dp else 26.dp,
            top = if (isPhone) 54.dp else 26.dp,
            bottom = if (isPhone) 18.dp else 26.dp
        )
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (isPhone) 14.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AccountPanel)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    Text(if (isPhone) "" else "  Menu")
                }
                Text(
                    "Inbox",
                    color = AccountCream,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (isPhone) 34.sp else 38.sp,
                    modifier = Modifier.padding(start = 20.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${inbox.size + subscriberNotifications.size} received",
                    color = AccountCream.copy(alpha = 0.72f),
                    fontSize = if (isPhone) 14.sp else 18.sp
                )
            }
            Surface(modifier = Modifier.fillMaxSize(), color = AccountCream, shape = RoundedCornerShape(22.dp)) {
                if (inbox.isEmpty() && subscriberNotifications.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Notifications and shared recipes will appear here.",
                            color = AccountInk.copy(alpha = 0.55f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(if (isPhone) 14.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            subscriberNotifications,
                            key = { "subscription_${it.subscriptionId}" }
                        ) { notification ->
                            SubscriberInboxCard(
                                notification = notification,
                                isPhone = isPhone,
                                onOpenProfile = {
                                    onOpenPublicProfile(notification.subscriberUserId)
                                }
                            )
                        }
                        items(inbox, key = { it.shareId }) { share ->
                            InboxCard(
                                share = share,
                                isPhone = isPhone,
                                onView = onViewShare,
                                onAdd = onAddShare,
                                onDismiss = onDismissShare,
                                onOpenSenderProfile = { onOpenPublicProfile(share.fromUserId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriberInboxCard(
    notification: SubscriberNotification,
    isPhone: Boolean,
    onOpenProfile: () -> Unit
) {
    Card(
        onClick = onOpenProfile,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 14.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileAvatar(notification.subscriberProfilePhotoUri, if (isPhone) 44 else 46)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${notification.subscriberDisplayName} subscribed to you",
                    color = AccountInk,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    "@${notification.subscriberUsername}",
                    color = AccountInk.copy(alpha = 0.62f),
                    fontSize = if (isPhone) 13.sp else 14.sp
                )
            }
            Icon(Icons.Rounded.Person, contentDescription = null, tint = AccountNavy)
        }
    }
}

@Composable
private fun InboxCard(
    share: RecipeShare,
    isPhone: Boolean,
    onView: (RecipeShare) -> Unit,
    onAdd: (RecipeShare) -> Unit,
    onDismiss: (RecipeShare) -> Unit,
    onOpenSenderProfile: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f))) {
        if (isPhone) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.clickable(onClick = onOpenSenderProfile)) {
                        ProfileAvatar(share.fromProfilePhotoUri, 44)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (share.status == "pending") {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(10.dp)
                                        .background(Color(0xFFD7433B), CircleShape)
                                )
                            }
                            Text(
                                "${share.fromUsername} shared ${share.recipe.title} with you",
                                color = AccountInk,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        }
                        if (share.status == "added") Text("Added to your recipes", color = Color(0xFF687A48), fontSize = 13.sp)
                    }
                    IconButton(onClick = { onDismiss(share) }) {
                        Icon(Icons.Rounded.Close, "Dismiss")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onView(share) }, modifier = Modifier.weight(1f)) { Text("View") }
                    Button(
                        onClick = { onAdd(share) },
                        enabled = share.status != "added",
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccountButter,
                            disabledContainerColor = Color(0xFFB7B2A8),
                            disabledContentColor = Color(0xFFEFECE3)
                        )
                    ) {
                        Text(
                            if (share.status == "added") "Added" else "Add",
                            color = if (share.status == "added") Color(0xFFEFECE3) else AccountNavy
                        )
                    }
                }
            }
            return@Card
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.clickable(onClick = onOpenSenderProfile)) {
                ProfileAvatar(share.fromProfilePhotoUri, 46)
            }
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
fun ShareCommunityRecipeDialog(
    recipe: CommunityRecipe,
    findUser: suspend (String) -> UserProfile?,
    matchingUsernames: suspend (String) -> List<String>,
    onShare: suspend (UserProfile) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var shared by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        val clean = username.trim()
        if (clean.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        suggestions = runCatching { matchingUsernames(clean) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share ${recipe.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountField(username, {
                    username = it
                    message = null
                    shared = false
                }, "Recipient username")
                suggestions.takeIf { it.isNotEmpty() }?.let { matches ->
                    Surface(
                        color = Color.White.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            matches.forEach { suggestion ->
                                TextButton(
                                    onClick = {
                                        username = suggestion
                                        suggestions = emptyList()
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
                    Text(it, color = if (shared) Color(0xFF4F7A43) else Color(0xFF9B493D))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = {
                    if (!loading) {
                        loading = true
                        scope.launch {
                            val recipient = runCatching { findUser(username) }.getOrNull()
                            message = if (recipient == null) {
                                "No user found with that username."
                            } else {
                                onShare(recipient).fold(
                                    onSuccess = {
                                        shared = true
                                        "Recipe shared with ${recipient.username}"
                                    },
                                    onFailure = { it.message ?: "Could not share recipe." }
                                )
                            }
                            loading = false
                        }
                    }
                },
                enabled = !loading && !shared,
                colors = ButtonDefaults.buttonColors(containerColor = AccountButter)
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
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    if (isPhone) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AccountNavy)
                .padding(start = 18.dp, end = 18.dp, top = 54.dp, bottom = 18.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(46.dp).background(AccountPanel, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = AccountCream)
                    }
                    Row(
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        ProfileAvatar(senderProfile?.profilePhotoUri ?: share.fromProfilePhotoUri, 38)
                        Column {
                            Text(
                                senderProfile?.displayName?.ifBlank { share.fromDisplayName }
                                    ?: share.fromDisplayName.ifBlank { share.fromUsername },
                                color = AccountCream,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Text("@${share.fromUsername}", color = AccountCream.copy(alpha = 0.68f), fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onAdd,
                        enabled = share.status != "added",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccountButter,
                            disabledContainerColor = Color(0xFFB7B2A8),
                            disabledContentColor = Color(0xFFEFECE3)
                        )
                    ) {
                        Text(if (share.status == "added") "Added" else "Add", color = if (share.status == "added") Color(0xFFEFECE3) else AccountNavy)
                    }
                }
                if (recipe.photoUris.isNotEmpty()) {
                    SharedPhotoGallery(recipe.photoUris)
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(26.dp),
                    color = AccountCream
                ) {
                    LazyColumn(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(recipe.title, color = AccountInk, fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 38.sp)
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
        return
    }
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
            Box(
                modifier = Modifier
                    .size(300.dp, 184.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6A5D4D), Color(0xFF221E18), Color.Black)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    tint = AccountButter.copy(alpha = 0.74f),
                    modifier = Modifier.size(52.dp)
                )
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
