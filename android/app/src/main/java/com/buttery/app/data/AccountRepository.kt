package com.buttery.app.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.buttery.app.domain.Recipe
import com.buttery.app.R
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class UserProfile(
    val userId: String,
    val username: String,
    val email: String,
    val displayName: String,
    val profilePhotoUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class SharedRecipeSnapshot(
    val title: String,
    val notes: String,
    val prepTime: String,
    val cookTime: String,
    val totalTime: String,
    val servings: String,
    val ingredients: String,
    val instructions: String,
    val photoUri: String?,
    val photoUris: List<String>,
    val videoUri: String?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val originalRawText: String
)

data class RecipeShare(
    val shareId: String,
    val sourceRecipeId: Long,
    val fromUserId: String,
    val fromUsername: String,
    val toUserId: String,
    val toUsername: String,
    val recipe: SharedRecipeSnapshot,
    val message: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

sealed interface AccountResult {
    data class Success(val profile: UserProfile) : AccountResult
    data class UsernameRequired(
        val userId: String,
        val email: String,
        val displayName: String
    ) : AccountResult
    data class Error(val message: String) : AccountResult
}

/**
 * Configuration-safe MVP account backend.
 *
 * This local implementation keeps the account UX testable until Firebase credentials are added.
 * It intentionally mirrors the Firestore document model. Password hashes are device-local demo
 * credentials, not a production authentication mechanism. Replace this class with the Firebase
 * implementation described in FIREBASE_SETUP.md before distributing the app.
 */
class AccountRepository(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow(loadCurrentUser())
    val currentUser: StateFlow<UserProfile?> = _currentUser
    private val _inbox = MutableStateFlow(loadInbox(_currentUser.value?.userId))
    val inbox: StateFlow<List<RecipeShare>> = _inbox
    private val _hasInboxNotification = MutableStateFlow(loadHasInboxNotification())
    val hasInboxNotification: StateFlow<Boolean> = _hasInboxNotification

    fun signUp(
        username: String,
        email: String,
        password: String,
        displayName: String
    ): AccountResult {
        val cleanUsername = username.trim()
        val usernameKey = cleanUsername.lowercase()
        val cleanEmail = email.trim().lowercase()
        if (!USERNAME.matches(cleanUsername)) {
            return AccountResult.Error("Username must be 3–24 letters, numbers, or underscores.")
        }
        if (!EMAIL.matches(cleanEmail)) return AccountResult.Error("Enter a valid email address.")
        if (password.length < 6) return AccountResult.Error("Password must be at least 6 characters.")
        val accounts = loadAccounts()
        if (accounts.any { it.profile.username.lowercase() == usernameKey }) {
            return AccountResult.Error("That username is already taken.")
        }
        if (accounts.any { it.profile.email == cleanEmail }) {
            return AccountResult.Error("An account already exists for that email.")
        }
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            userId = UUID.randomUUID().toString(),
            username = cleanUsername,
            email = cleanEmail,
            displayName = displayName.trim().ifBlank { cleanUsername },
            createdAt = now,
            updatedAt = now
        )
        saveAccounts(accounts + LocalAccount(profile, hash(password)))
        setSession(profile)
        return AccountResult.Success(profile)
    }

    fun signIn(email: String, password: String): AccountResult {
        val account = loadAccounts().firstOrNull {
            it.profile.email == email.trim().lowercase() && it.passwordHash == hash(password)
        } ?: return AccountResult.Error("Incorrect email or password.")
        setSession(account.profile)
        return AccountResult.Success(account.profile)
    }

    suspend fun signInWithGoogle(activity: Activity): AccountResult = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credential = CredentialManager.create(activity)
            .getCredential(activity, request)
            .credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google did not return an ID token." }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authResult = FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
            .await()
        openFederatedProfileOrRequestUsername(
            requireNotNull(authResult.user) { "Google account was unavailable." }
        )
    }.fold(
        onSuccess = { it },
        onFailure = {
            AccountResult.Error(
                when (it) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException ->
                        "Google Sign-In was canceled."
                    else -> it.localizedMessage ?: "Google Sign-In failed."
                }
            )
        }
    )

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        preferences.edit().remove(KEY_SESSION_USER_ID).apply()
        _currentUser.value = null
        _inbox.value = emptyList()
        _hasInboxNotification.value = false
    }

    private fun openFederatedProfileOrRequestUsername(user: FirebaseUser): AccountResult {
        val email = requireNotNull(user.email).lowercase()
        val accounts = loadAccounts()
        val existing = accounts.firstOrNull { it.profile.email == email }
        if (existing != null) {
            setSession(existing.profile)
            return AccountResult.Success(existing.profile)
        }
        return AccountResult.UsernameRequired(
            userId = user.uid,
            email = email,
            displayName = user.displayName?.trim().orEmpty()
        )
    }

    fun completeGoogleSignUp(
        userId: String,
        email: String,
        displayName: String,
        username: String
    ): AccountResult {
        val authenticatedUser = FirebaseAuth.getInstance().currentUser
            ?: return AccountResult.Error("Google Sign-In expired. Please try again.")
        if (authenticatedUser.uid != userId || authenticatedUser.email?.lowercase() != email) {
            return AccountResult.Error("Google account validation failed. Please sign in again.")
        }
        val cleanUsername = username.trim()
        if (!USERNAME.matches(cleanUsername)) {
            return AccountResult.Error("Username must be 3–24 letters, numbers, or underscores.")
        }
        val accounts = loadAccounts()
        if (accounts.any { it.profile.username.equals(cleanUsername, ignoreCase = true) }) {
            return AccountResult.Error("That username is already taken.")
        }
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            userId = userId,
            username = cleanUsername,
            email = email,
            displayName = displayName.ifBlank { cleanUsername },
            createdAt = now,
            updatedAt = now
        )
        saveAccounts(accounts + LocalAccount(profile, FEDERATED_GOOGLE))
        setSession(profile)
        return AccountResult.Success(profile)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase request failed.")
                )
            }
        }
    }

    fun updateProfile(displayName: String, photoUri: String?): AccountResult {
        val current = _currentUser.value ?: return AccountResult.Error("Sign in first.")
        val accounts = loadAccounts()
        val updated = current.copy(
            displayName = displayName.trim().ifBlank { current.username },
            profilePhotoUri = photoUri,
            updatedAt = System.currentTimeMillis()
        )
        saveAccounts(accounts.map {
            if (it.profile.userId == current.userId) it.copy(profile = updated) else it
        })
        setSession(updated)
        return AccountResult.Success(updated)
    }

    fun findUser(username: String): UserProfile? = loadAccounts()
        .firstOrNull { it.profile.username.equals(username.trim(), ignoreCase = true) }
        ?.profile

    fun shareRecipe(recipe: Recipe, recipient: UserProfile, message: String = ""): Result<Unit> {
        val sender = _currentUser.value ?: return Result.failure(IllegalStateException("Sign in first."))
        if (sender.userId == recipient.userId) {
            return Result.failure(IllegalArgumentException("Choose another user."))
        }
        if (loadShares().any {
                it.sourceRecipeId == recipe.id &&
                    it.fromUserId == sender.userId &&
                    it.toUserId == recipient.userId &&
                    it.status != STATUS_DISMISSED
            }
        ) {
            return Result.failure(IllegalStateException("This recipe was already shared with ${recipient.username}."))
        }
        val now = System.currentTimeMillis()
        val share = RecipeShare(
            shareId = UUID.randomUUID().toString(),
            sourceRecipeId = recipe.id,
            fromUserId = sender.userId,
            fromUsername = sender.username,
            toUserId = recipient.userId,
            toUsername = recipient.username,
            recipe = recipe.toSharedSnapshot(),
            message = message.trim(),
            status = STATUS_PENDING,
            createdAt = now,
            updatedAt = now
        )
        saveShares(loadShares() + share)
        refreshInbox()
        return Result.success(Unit)
    }

    fun markShare(shareId: String, status: String) {
        saveShares(loadShares().map {
            if (it.shareId == shareId) {
                it.copy(status = status, updatedAt = System.currentTimeMillis())
            } else {
                it
            }
        })
        refreshInbox()
    }

    fun refreshInbox() {
        _inbox.value = loadInbox(_currentUser.value?.userId)
        _hasInboxNotification.value = loadHasInboxNotification()
    }

    fun acknowledgeInboxNotification() {
        val userId = _currentUser.value?.userId ?: return
        preferences.edit()
            .putLong("$KEY_INBOX_SEEN_PREFIX$userId", System.currentTimeMillis())
            .apply()
        _hasInboxNotification.value = false
    }

    private fun setSession(profile: UserProfile) {
        preferences.edit().putString(KEY_SESSION_USER_ID, profile.userId).apply()
        _currentUser.value = profile
        refreshInbox()
    }

    private fun loadCurrentUser(): UserProfile? {
        val id = preferences.getString(KEY_SESSION_USER_ID, null) ?: return null
        return loadAccounts().firstOrNull { it.profile.userId == id }?.profile
    }

    private fun loadInbox(userId: String?): List<RecipeShare> {
        if (userId == null) return emptyList()
        return loadShares()
            .filter { it.toUserId == userId && it.status != STATUS_DISMISSED }
            .sortedByDescending { it.createdAt }
    }

    private fun loadHasInboxNotification(): Boolean {
        val userId = _currentUser.value?.userId ?: return false
        val lastSeen = preferences.getLong("$KEY_INBOX_SEEN_PREFIX$userId", 0L)
        return loadShares().any {
            it.toUserId == userId &&
                it.status != STATUS_DISMISSED &&
                it.createdAt > lastSeen
        }
    }

    private fun loadAccounts(): List<LocalAccount> = runCatching {
        val array = JSONArray(preferences.getString(KEY_ACCOUNTS, "[]"))
        buildList {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toAccount())
        }
    }.getOrDefault(emptyList())

    private fun saveAccounts(accounts: List<LocalAccount>) {
        preferences.edit().putString(
            KEY_ACCOUNTS,
            JSONArray().apply { accounts.forEach { put(it.toJson()) } }.toString()
        ).apply()
    }

    private fun loadShares(): List<RecipeShare> = runCatching {
        val array = JSONArray(preferences.getString(KEY_SHARES, "[]"))
        buildList {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toShare())
        }
    }.getOrDefault(emptyList())

    private fun saveShares(shares: List<RecipeShare>) {
        preferences.edit().putString(
            KEY_SHARES,
            JSONArray().apply { shares.forEach { put(it.toJson()) } }.toString()
        ).apply()
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private data class LocalAccount(val profile: UserProfile, val passwordHash: String)

    private fun LocalAccount.toJson() = profile.toJson().put("passwordHash", passwordHash)
    private fun JSONObject.toAccount() = LocalAccount(toProfile(), getString("passwordHash"))

    private fun UserProfile.toJson() = JSONObject()
        .put("userId", userId).put("username", username).put("email", email)
        .put("displayName", displayName).put("profilePhotoUri", profilePhotoUri)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun JSONObject.toProfile() = UserProfile(
        userId = getString("userId"),
        username = getString("username"),
        email = getString("email"),
        displayName = getString("displayName"),
        profilePhotoUri = optString("profilePhotoUri").takeIf { it.isNotBlank() && it != "null" },
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt")
    )

    private fun RecipeShare.toJson() = JSONObject()
        .put("shareId", shareId).put("sourceRecipeId", sourceRecipeId)
        .put("fromUserId", fromUserId)
        .put("fromUsername", fromUsername).put("toUserId", toUserId)
        .put("toUsername", toUsername).put("recipe", recipe.toJson())
        .put("message", message).put("status", status)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun JSONObject.toShare() = RecipeShare(
        shareId = getString("shareId"),
        sourceRecipeId = optLong("sourceRecipeId", -1L),
        fromUserId = getString("fromUserId"),
        fromUsername = getString("fromUsername"),
        toUserId = getString("toUserId"),
        toUsername = getString("toUsername"),
        recipe = getJSONObject("recipe").toSnapshot(),
        message = optString("message"),
        status = getString("status"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt")
    )

    private fun SharedRecipeSnapshot.toJson() = JSONObject()
        .put("title", title).put("notes", notes).put("prepTime", prepTime)
        .put("cookTime", cookTime).put("totalTime", totalTime).put("servings", servings)
        .put("ingredients", ingredients).put("instructions", instructions)
        .put("photoUri", photoUri)
        .put("photoUris", JSONArray().apply { photoUris.forEach(::put) })
        .put("videoUri", videoUri)
        .put("imageUrl", imageUrl).put("sourceUrl", sourceUrl)
        .put("originalRawText", originalRawText)

    private fun JSONObject.toSnapshot() = SharedRecipeSnapshot(
        title = getString("title"), notes = optString("notes"),
        prepTime = optString("prepTime"), cookTime = optString("cookTime"),
        totalTime = optString("totalTime"), servings = optString("servings"),
        ingredients = optString("ingredients"), instructions = optString("instructions"),
        photoUri = optString("photoUri").takeIf { it.isNotBlank() && it != "null" },
        photoUris = optJSONArray("photoUris")?.let { photos ->
            buildList {
                for (index in 0 until photos.length()) {
                    photos.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty().ifEmpty {
            listOfNotNull(optString("photoUri").takeIf { it.isNotBlank() && it != "null" })
        },
        videoUri = optString("videoUri").takeIf { it.isNotBlank() && it != "null" },
        imageUrl = optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
        sourceUrl = optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" },
        originalRawText = optString("originalRawText")
    )

    private fun Recipe.toSharedSnapshot() = SharedRecipeSnapshot(
        title, notes, prepTime, cookTime, totalTime, servings, ingredients, instructions,
        photoUri, photoUris, videoUri, imageUrl, sourceUrl, originalRawText
    )

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_VIEWED = "viewed"
        const val STATUS_ADDED = "added"
        const val STATUS_DISMISSED = "dismissed"
        private const val PREFERENCES_NAME = "buttery_accounts_mvp"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SHARES = "shares"
        private const val KEY_SESSION_USER_ID = "session_user_id"
        private const val KEY_INBOX_SEEN_PREFIX = "inbox_seen_"
        private const val FEDERATED_GOOGLE = "federated:google"
        private val USERNAME = Regex("^[A-Za-z0-9_]{3,24}$")
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
