package com.buttery.app.data

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.buttery.app.R
import com.buttery.app.domain.Recipe
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL
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
    val sourceRecipeId: String,
    val fromUserId: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val fromProfilePhotoUri: String?,
    val toUserId: String,
    val toUsername: String,
    val recipe: SharedRecipeSnapshot,
    val message: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class SubscriberNotification(
    val subscriptionId: String,
    val subscriberUserId: String,
    val subscriberUsername: String,
    val subscriberDisplayName: String,
    val subscriberProfilePhotoUri: String?,
    val createdAt: Long
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
 * Firebase-backed account, profile, username lookup, sharing, and inbox repository.
 *
 * Normal Android recipes remain in Room for this chunk. Shares use the same Firestore
 * collections as iOS: users, usernames, and recipeShares.
 */
class AccountRepository(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var inboxListener: ListenerRegistration? = null
    private var subscriberInboxListener: ListenerRegistration? = null
    private val inboxPreferences = appContext.getSharedPreferences(
        "buttery_inbox_state",
        Context.MODE_PRIVATE
    )

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser

    private val _inbox = MutableStateFlow<List<RecipeShare>>(emptyList())
    val inbox: StateFlow<List<RecipeShare>> = _inbox

    private val _subscriberNotifications = MutableStateFlow<List<SubscriberNotification>>(emptyList())
    val subscriberNotifications: StateFlow<List<SubscriberNotification>> = _subscriberNotifications

    private val _hasInboxNotification = MutableStateFlow(false)
    val hasInboxNotification: StateFlow<Boolean> = _hasInboxNotification

    init {
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            scope.launch {
                val user = firebaseAuth.currentUser
                if (user == null) {
                    setSignedOut()
                } else {
                    loadProfile(user)?.let(::setSession)
                        ?: run {
                            _currentUser.value = null
                            listenForInbox(null)
                        }
                }
            }
        }.also(auth::addAuthStateListener)
    }

    suspend fun signUp(
        username: String,
        email: String,
        password: String,
        displayName: String
    ): AccountResult = runCatching {
        val cleanUsername = validateUsername(username)
        val cleanEmail = validateEmail(email)
        if (password.length < 6) throw IllegalArgumentException("Password must be at least 6 characters.")

        val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = requireNotNull(result.user) { "Account was created, but the session was unavailable." }
        try {
            reserveUsernameAndCreateProfile(
                user = user,
                username = cleanUsername,
                displayName = displayName.trim().ifBlank { cleanUsername },
                provider = "password"
            )
            requireNotNull(loadProfile(user)).also(::setSession)
        } catch (error: Throwable) {
            runCatching { user.delete().await() }
            throw error
        }
    }.fold(
        onSuccess = AccountResult::Success,
        onFailure = { AccountResult.Error(friendlyMessage(it)) }
    )

    suspend fun signIn(email: String, password: String): AccountResult = runCatching {
        val cleanEmail = validateEmail(email)
        if (password.isBlank()) throw IllegalArgumentException("Password is required.")
        val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
        val user = requireNotNull(result.user) { "Signed in, but the user session was unavailable." }
        val profile = loadProfile(user)
            ?: throw IllegalStateException("This account is missing its Buttery profile. Please sign in again or contact support.")
        setSession(profile)
        profile
    }.fold(
        onSuccess = AccountResult::Success,
        onFailure = { AccountResult.Error(friendlyMessage(it)) }
    )

    suspend fun signInWithGoogle(activity: Activity): AccountResult = runCatching {
        val serverClientId = resolveGeneratedString("default_web_client_id")
            ?: throw IllegalStateException(
                "Google Sign-In needs an Android google-services.json that includes a Web OAuth client."
            )
        val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
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
        val authResult: AuthResult = auth
            .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
            .await()
        openFederatedProfileOrRequestUsername(
            requireNotNull(authResult.user) { "Google account was unavailable." }
        )
    }.fold(
        onSuccess = { it },
        onFailure = {
            Log.e("ButteryGoogleSignIn", "Google sign-in failed", it)
            AccountResult.Error(
                when (it) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException ->
                        it.message
                            ?.takeUnless { message ->
                                message.equals("activity is cancelled by the user.", ignoreCase = true)
                            }
                            ?.let { message -> "Google Sign-In couldn't finish: $message" }
                            ?: "Google Sign-In was canceled."
                    is androidx.credentials.exceptions.NoCredentialException ->
                        "No Google account is available. Add a Google account to this phone, then try again."
                    else -> friendlyMessage(it)
                }
            )
        }
    )

    fun signOut() {
        auth.signOut()
        setSignedOut()
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Your session expired. Sign in again first.")
        val profile = _currentUser.value ?: throw IllegalStateException("Your profile could not be loaded.")
        val lastSignInAt = user.metadata?.lastSignInTimestamp ?: 0L
        if (lastSignInAt == 0L || System.currentTimeMillis() - lastSignInAt > RECENT_LOGIN_WINDOW_MS) {
            throw IllegalStateException(
                "For your security, sign out and sign back in before deleting your account."
            )
        }

        val sentShares = documentsWhere(SHARES, "fromUserId", user.uid)
        val receivedShares = documentsWhere(SHARES, "toUserId", user.uid)
        val ownedRecipes = documentsWhere(RECIPES, "ownerId", user.uid)
        val createdLikes = documentsWhere(RECIPE_LIKES, "userId", user.uid)
        val receivedLikes = documentsWhere(RECIPE_LIKES, "ownerId", user.uid)
        val sentSubscriptions = documentsWhere(SUBSCRIPTIONS, "subscriberUserId", user.uid)
        val receivedSubscriptions = documentsWhere(SUBSCRIPTIONS, "creatorUserId", user.uid)

        val ownedMediaUrls = buildSet {
            profile.profilePhotoUri?.let(::add)
            sentShares.forEach { document ->
                val snapshot = document.get("recipeSnapshot") as? Map<*, *> ?: return@forEach
                (snapshot["photoUrls"] as? List<*>)?.mapNotNullTo(this) { it as? String }
                (snapshot["videoUrl"] as? String)?.takeIf { it.isNotBlank() }?.let(::add)
            }
            ownedRecipes.forEach { document ->
                (document.get("photoUrls") as? List<*>)?.mapNotNullTo(this) { it as? String }
                (document.get("videoUrls") as? List<*>)?.mapNotNullTo(this) { it as? String }
                document.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }?.let(::add)
                document.getString("videoUrl")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        ownedMediaUrls.forEach { deleteOwnedStorageObjectIfPresent(it, user.uid) }

        val references = buildList<DocumentReference> {
            addAll((sentShares + receivedShares).map { it.reference })
            addAll(ownedRecipes.map { it.reference })
            addAll((createdLikes + receivedLikes).map { it.reference })
            addAll((sentSubscriptions + receivedSubscriptions).map { it.reference })
            add(database.collection(PUBLIC_PROFILES).document(user.uid))
            add(database.collection(USERNAMES).document(profile.username.lowercase()))
            add(database.collection(USERS).document(user.uid))
        }.associateBy { it.path }.values
        references.chunked(FIRESTORE_DELETE_BATCH_SIZE).forEach { chunk ->
            val batch = database.batch()
            chunk.forEach(batch::delete)
            batch.commit().await()
        }

        user.delete().await()
        setSignedOut()
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(IllegalStateException(friendlyMessage(it), it)) }
    )

    private suspend fun openFederatedProfileOrRequestUsername(user: FirebaseUser): AccountResult {
        val existing = loadProfile(user)
        if (existing != null) {
            setSession(existing)
            return AccountResult.Success(existing)
        }
        return AccountResult.UsernameRequired(
            userId = user.uid,
            email = user.email.orEmpty().lowercase(),
            displayName = user.displayName?.trim().orEmpty()
        )
    }

    suspend fun completeGoogleSignUp(
        userId: String,
        email: String,
        displayName: String,
        username: String
    ): AccountResult = runCatching {
        val authenticatedUser = auth.currentUser
            ?: throw IllegalStateException("Google Sign-In expired. Please try again.")
        if (authenticatedUser.uid != userId || authenticatedUser.email.orEmpty().lowercase() != email.lowercase()) {
            throw IllegalStateException("Google account validation failed. Please sign in again.")
        }
        val cleanUsername = validateUsername(username)
        reserveUsernameAndCreateProfile(
            user = authenticatedUser,
            username = cleanUsername,
            displayName = displayName.trim().ifBlank { cleanUsername },
            provider = authenticatedUser.providerData.firstOrNull()?.providerId ?: "google.com"
        )
        requireNotNull(loadProfile(authenticatedUser)).also(::setSession)
    }.fold(
        onSuccess = AccountResult::Success,
        onFailure = { AccountResult.Error(friendlyMessage(it)) }
    )

    suspend fun updateProfile(displayName: String, photoUri: String?): AccountResult = runCatching {
        val current = _currentUser.value ?: throw IllegalStateException("Sign in first.")
        val cleanName = displayName.trim().ifBlank { current.username }
        val uploadedPhoto = uploadProfilePhotoIfNeeded(current.userId, photoUri)
        val updated = current.copy(
            displayName = cleanName,
            profilePhotoUri = uploadedPhoto,
            updatedAt = System.currentTimeMillis()
        )
        database.collection(USERS).document(current.userId).update(
            mapOf(
                "displayName" to cleanName,
                "profilePhotoUrl" to (uploadedPhoto ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        database.collection("publicProfiles").document(current.userId).set(
            mapOf(
                "uid" to current.userId,
                "username" to current.username,
                "displayName" to cleanName,
                "profilePhotoUrl" to (uploadedPhoto ?: ""),
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to 1
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
        database.collection("recipes")
            .whereEqualTo("ownerId", current.userId)
            .get()
            .await()
            .documents
            .forEach { document ->
                document.reference.update(
                    mapOf(
                        "ownerDisplayName" to cleanName,
                        "ownerProfilePhotoUrl" to (uploadedPhoto ?: ""),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        database.collection(SHARES)
            .whereEqualTo("fromUserId", current.userId)
            .get()
            .await()
            .documents
            .forEach { document ->
                document.reference.update(
                    mapOf(
                        "fromDisplayName" to cleanName,
                        "fromProfilePhotoUrl" to (uploadedPhoto ?: ""),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        setSession(updated)
        updated
    }.fold(
        onSuccess = AccountResult::Success,
        onFailure = { AccountResult.Error(friendlyMessage(it)) }
    )

    suspend fun findUser(username: String): UserProfile? {
        val lower = username.trim().lowercase()
        if (lower.isBlank()) return null
        val usernameDocument = database.collection(USERNAMES).document(lower).get().await()
        val userId = usernameDocument.getString("userId")
            ?: usernameDocument.getString("uid")
            ?: return null
        val displayUsername = usernameDocument.getString("username")
            ?: usernameDocument.getString("displayUsername")
            ?: lower
        return UserProfile(
            userId = userId,
            username = displayUsername,
            email = "",
            displayName = displayUsername,
            profilePhotoUri = null,
            createdAt = 0L,
            updatedAt = 0L
        )
    }

    suspend fun matchingUsernames(prefix: String): List<String> {
        val cleanPrefix = prefix.trim().lowercase()
        if (cleanPrefix.isBlank()) return emptyList()
        return database.collection(USERNAMES)
            .orderBy(FieldPath.documentId())
            .startAt(cleanPrefix)
            .endAt(cleanPrefix + "\uf8ff")
            .limit(8)
            .get()
            .await()
            .documents
            .map { it.id }
    }

    suspend fun shareRecipe(recipe: Recipe, recipient: UserProfile, message: String = ""): Result<Unit> =
        runCatching {
            val sender = _currentUser.value ?: throw IllegalStateException("Sign in first.")
            if (sender.userId == recipient.userId) throw IllegalArgumentException("Choose another user.")

            val duplicate = database.collection(SHARES)
                .whereEqualTo("fromUserId", sender.userId)
                .get()
                .await()
                .documents
                .any {
                    it.getString("toUserId") == recipient.userId &&
                        it.getString("sourceRecipeId") == recipe.id.toString() &&
                        it.getString("status") != STATUS_DISMISSED
                }
            if (duplicate) {
                throw IllegalStateException("This recipe was already shared with ${recipient.username}.")
            }

            val shareRef = database.collection(SHARES).document()
            val initialPhotoUrls = recipe.photoUris
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
            val initialVideoUrl = recipe.videoUri
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val snapshot = mapOf(
                "title" to recipe.title,
                "notes" to recipe.notes,
                "prepTime" to recipe.prepTime,
                "cookTime" to recipe.cookTime,
                "totalTime" to recipe.totalTime,
                "servings" to recipe.servings,
                "ingredients" to recipe.ingredients,
                "instructions" to recipe.instructions,
                "photoUrls" to initialPhotoUrls,
                "videoUrl" to (initialVideoUrl ?: ""),
                "imageUrl" to (recipe.imageUrl ?: ""),
                "sourceUrl" to (recipe.sourceUrl ?: ""),
                "originalRawText" to recipe.originalRawText
            )
            shareRef.set(
                mapOf(
                    "shareId" to shareRef.id,
                    "sourceRecipeId" to recipe.id.toString(),
                    "fromUserId" to sender.userId,
                    "fromUsername" to sender.username,
                    "fromDisplayName" to sender.displayName,
                    "fromProfilePhotoUrl" to (sender.profilePhotoUri ?: ""),
                    "toUserId" to recipient.userId,
                    "toUsername" to recipient.username.lowercase(),
                    "recipeSnapshot" to snapshot,
                    "message" to message.trim(),
                    "status" to STATUS_PENDING,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1
                )
            ).await()
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val media = uploadShareMedia(recipe, sender.userId, shareRef.id)
                    val updates = mutableMapOf<String, Any>()
                    if (media.photoUrls.isNotEmpty()) {
                        updates["recipeSnapshot.photoUrls"] = media.photoUrls
                    }
                    media.videoUrl?.let { updates["recipeSnapshot.videoUrl"] = it }
                    if (updates.isNotEmpty()) {
                        updates["updatedAt"] = FieldValue.serverTimestamp()
                        shareRef.update(updates).await()
                    }
                }
            }
        }

    suspend fun shareCommunityRecipe(recipe: CommunityRecipe, recipient: UserProfile, message: String = ""): Result<Unit> =
        runCatching {
            val sender = _currentUser.value ?: throw IllegalStateException("Sign in first.")
            if (sender.userId == recipient.userId) throw IllegalArgumentException("Choose another user.")
            val shareRef = database.collection(SHARES).document()
            val snapshot = mapOf(
                "title" to recipe.title,
                "notes" to recipe.notes,
                "prepTime" to recipe.prepTime,
                "cookTime" to recipe.cookTime,
                "totalTime" to recipe.totalTime,
                "servings" to recipe.servings,
                "ingredients" to recipe.ingredients,
                "instructions" to recipe.instructions,
                "photoUrls" to recipe.photoUrls,
                "videoUrl" to (recipe.videoUrls.firstOrNull() ?: ""),
                "imageUrl" to (recipe.thumbnailUrl ?: ""),
                "sourceUrl" to (recipe.sourceUrl ?: ""),
                "originalRawText" to ""
            )
            shareRef.set(
                mapOf(
                    "shareId" to shareRef.id,
                    "sourceRecipeId" to recipe.id,
                    "fromUserId" to sender.userId,
                    "fromUsername" to sender.username,
                    "fromDisplayName" to sender.displayName,
                    "fromProfilePhotoUrl" to (sender.profilePhotoUri ?: ""),
                    "toUserId" to recipient.userId,
                    "toUsername" to recipient.username.lowercase(),
                    "recipeSnapshot" to snapshot,
                    "message" to message.trim(),
                    "status" to STATUS_PENDING,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1
                )
            ).await()
        }

    suspend fun markShare(shareId: String, status: String) {
        database.collection(SHARES).document(shareId).update(
            mapOf(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun refreshInbox() {
        listenForInbox(_currentUser.value?.userId)
    }

    fun acknowledgeInboxNotification() {
        val userId = _currentUser.value?.userId ?: return
        inboxPreferences.edit()
            .putLong(lastInboxViewedKey(userId), System.currentTimeMillis())
            .apply()
        recomputeInboxNotification()
    }

    private suspend fun reserveUsernameAndCreateProfile(
        user: FirebaseUser,
        username: String,
        displayName: String,
        provider: String
    ) {
        val lower = username.lowercase()
        val usernameRef = database.collection(USERNAMES).document(lower)
        val userRef = database.collection(USERS).document(user.uid)
        database.runTransaction { transaction ->
            val existing = transaction.get(usernameRef)
            if (existing.exists()) throw IllegalStateException("That username is already taken.")
            val now = FieldValue.serverTimestamp()
            transaction.set(usernameRef, mapOf("uid" to user.uid, "userId" to user.uid))
            transaction.set(
                userRef,
                mapOf(
                    "uid" to user.uid,
                    "userId" to user.uid,
                    "username" to username,
                    "usernameLowercase" to lower,
                    "email" to user.email.orEmpty(),
                    "displayName" to displayName,
                    "profilePhotoUrl" to (user.photoUrl?.toString() ?: ""),
                    "provider" to provider,
                    "termsVersion" to TERMS_VERSION,
                    "termsAcceptedAt" to now,
                    "createdAt" to now,
                    "updatedAt" to now,
                    "schemaVersion" to 1
                )
            )
            null
        }.await()
    }

    private suspend fun loadProfile(user: FirebaseUser): UserProfile? =
        database.collection(USERS).document(user.uid).get().await().toProfile(user)

    private fun setSession(profile: UserProfile) {
        _currentUser.value = profile
        listenForInbox(profile.userId)
    }

    private fun setSignedOut() {
        _currentUser.value = null
        _inbox.value = emptyList()
        _subscriberNotifications.value = emptyList()
        _hasInboxNotification.value = false
        listenForInbox(null)
    }

    private fun listenForInbox(userId: String?) {
        inboxListener?.remove()
        inboxListener = null
        subscriberInboxListener?.remove()
        subscriberInboxListener = null
        if (userId == null) return
        inboxListener = database.collection(SHARES)
            .whereEqualTo("toUserId", userId)
            .addSnapshotListener { snapshot, _ ->
                val items = snapshot?.documents
                    ?.mapNotNull { it.toShare() }
                    ?.filter { it.status != STATUS_DISMISSED }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()
                _inbox.value = items
                recomputeInboxNotification()
            }
        subscriberInboxListener = database.collection(SUBSCRIPTIONS)
            .whereEqualTo("creatorUserId", userId)
            .addSnapshotListener { snapshot, _ ->
                _subscriberNotifications.value = snapshot?.documents
                    ?.mapNotNull { it.toSubscriberNotification() }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()
                recomputeInboxNotification()
            }
    }

    private fun recomputeInboxNotification() {
        val userId = _currentUser.value?.userId ?: run {
            _hasInboxNotification.value = false
            return
        }
        val lastViewedAt = inboxPreferences.getLong(lastInboxViewedKey(userId), 0L)
        _hasInboxNotification.value =
            _inbox.value.any { it.createdAt > lastViewedAt } ||
                _subscriberNotifications.value.any { it.createdAt > lastViewedAt }
    }

    private fun lastInboxViewedKey(userId: String) = "last_inbox_viewed_at_$userId"

    private suspend fun uploadProfilePhotoIfNeeded(ownerId: String, photoUri: String?): String? {
        if (photoUri.isNullOrBlank()) return null
        if (photoUri.startsWith("http://") || photoUri.startsWith("https://")) return photoUri
        val data = readMediaBytes(photoUri, 5 * 1_024 * 1_024)
        val reference = storage.reference
            .child("users/$ownerId/profile/avatar_${System.currentTimeMillis()}.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        reference.putBytes(data, metadata).await()
        return reference.downloadUrl.await().toString()
    }

    private suspend fun uploadShareMedia(
        recipe: Recipe,
        ownerId: String,
        shareId: String
    ): UploadedShareMedia {
        val folder = storage.reference.child("users/$ownerId/recipes/${recipe.id}/shares/$shareId")
        val photos = recipe.photoUris
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .toMutableList()
        recipe.photoUris.distinct().forEachIndexed { index, uri ->
            if (uri.startsWith("http://") || uri.startsWith("https://")) return@forEachIndexed
            val data = runCatching { readMediaBytes(uri, 15 * 1_024 * 1_024) }.getOrNull()
                ?: return@forEachIndexed
            val item = folder.child("photo_$index.jpg")
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            item.putBytes(data, metadata).await()
            photos += item.downloadUrl.await().toString()
        }

        val videoUrl = recipe.videoUri?.let { uri ->
            if (uri.startsWith("http://") || uri.startsWith("https://")) return@let uri
            val data = runCatching { readMediaBytes(uri, 50 * 1_024 * 1_024) }.getOrNull()
                ?: return@let null
            val item = folder.child("video.mp4")
            val metadata = StorageMetadata.Builder().setContentType("video/mp4").build()
            item.putBytes(data, metadata).await()
            item.downloadUrl.await().toString()
        }
        return UploadedShareMedia(photos, videoUrl)
    }

    private suspend fun readMediaBytes(uriText: String, maximumSize: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriText)
            val bytes = when (uri.scheme?.lowercase()) {
                "http", "https" -> URL(uriText).openStream().use { it.readBytes() }
                else -> appContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open selected media." }.readBytes()
                }
            }
            require(bytes.size <= maximumSize) { "That media file is too large." }
            bytes
        }

    private suspend fun documentsWhere(
        collection: String,
        field: String,
        value: String
    ): List<DocumentSnapshot> = database.collection(collection)
        .whereEqualTo(field, value)
        .get()
        .await()
        .documents

    private suspend fun deleteOwnedStorageObjectIfPresent(url: String, ownerId: String) {
        val reference = runCatching { storage.getReferenceFromUrl(url) }.getOrNull() ?: return
        val normalizedPath = reference.path.trimStart('/')
        if (!normalizedPath.startsWith("users/$ownerId/")) return
        try {
            reference.delete().await()
        } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
    }

    private fun DocumentSnapshot.toProfile(user: FirebaseUser? = null): UserProfile? {
        if (!exists()) return null
        val username = getString("username")?.takeIf { it.isNotBlank() } ?: return null
        return UserProfile(
            userId = getString("uid") ?: getString("userId") ?: id,
            username = username,
            email = getString("email") ?: user?.email.orEmpty(),
            displayName = getString("displayName") ?: user?.displayName ?: username,
            profilePhotoUri = getString("profilePhotoUrl")?.takeIf { it.isNotBlank() },
            createdAt = getTimestamp("createdAt").toMillisOrNow(),
            updatedAt = getTimestamp("updatedAt").toMillisOrNow()
        )
    }

    private fun DocumentSnapshot.toShare(): RecipeShare? {
        val snapshot = get("recipeSnapshot") as? Map<*, *> ?: return null
        val recipeTitle = (snapshot["title"] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val photos = (snapshot["photoUrls"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val imageUrl = (snapshot["imageUrl"] as? String)?.takeIf { it.isNotBlank() }
        return RecipeShare(
            shareId = getString("shareId") ?: id,
            sourceRecipeId = getString("sourceRecipeId") ?: "",
            fromUserId = getString("fromUserId") ?: return null,
            fromUsername = getString("fromUsername") ?: "",
            fromDisplayName = getString("fromDisplayName") ?: getString("fromUsername") ?: "",
            fromProfilePhotoUri = getString("fromProfilePhotoUrl")?.takeIf { it.isNotBlank() },
            toUserId = getString("toUserId") ?: return null,
            toUsername = getString("toUsername") ?: "",
            recipe = SharedRecipeSnapshot(
                title = recipeTitle,
                notes = snapshot["notes"] as? String ?: "",
                prepTime = snapshot["prepTime"] as? String ?: "",
                cookTime = snapshot["cookTime"] as? String ?: "",
                totalTime = snapshot["totalTime"] as? String ?: "",
                servings = snapshot["servings"] as? String ?: "",
                ingredients = snapshot["ingredients"] as? String ?: "",
                instructions = snapshot["instructions"] as? String ?: "",
                photoUri = photos.firstOrNull() ?: imageUrl,
                photoUris = photos.ifEmpty { listOfNotNull(imageUrl) },
                videoUri = snapshot["videoUrl"] as? String,
                imageUrl = imageUrl,
                sourceUrl = snapshot["sourceUrl"] as? String,
                originalRawText = snapshot["originalRawText"] as? String ?: ""
            ),
            message = getString("message") ?: "",
            status = getString("status") ?: STATUS_PENDING,
            createdAt = getTimestamp("createdAt").toMillisOrNow(),
            updatedAt = getTimestamp("updatedAt").toMillisOrNow()
        )
    }

    private fun DocumentSnapshot.toSubscriberNotification(): SubscriberNotification? {
        val subscriberUserId = getString("subscriberUserId") ?: return null
        return SubscriberNotification(
            subscriptionId = getString("subscriptionId") ?: id,
            subscriberUserId = subscriberUserId,
            subscriberUsername = getString("subscriberUsername") ?: "buttery_user",
            subscriberDisplayName = getString("subscriberDisplayName")
                ?: getString("subscriberUsername")
                ?: "Buttery user",
            subscriberProfilePhotoUri = getString("subscriberProfilePhotoUrl")
                ?.takeIf { it.isNotBlank() },
            createdAt = getTimestamp("createdAt").toMillisOrNow()
        )
    }

    private fun Timestamp?.toMillisOrNow(): Long = this?.toDate()?.time ?: System.currentTimeMillis()

    private fun validateEmail(email: String): String {
        val clean = email.trim().lowercase()
        require(EMAIL.matches(clean)) { "Enter a valid email address." }
        return clean
    }

    private fun validateUsername(username: String): String {
        val clean = username.trim()
        require(USERNAME.matches(clean)) { "Username must be 3–24 letters, numbers, or underscores." }
        return clean
    }

    private fun friendlyMessage(error: Throwable): String {
        if (error.message?.contains("username", ignoreCase = true) == true &&
            error.message?.contains("taken", ignoreCase = true) == true
        ) {
            return "That username is already taken."
        }
        return when ((error as? FirebaseAuthException)?.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
            "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists for that email."
            "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                "Incorrect email or password."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network connection failed. Please try again."
            else -> error.localizedMessage ?: "Something went wrong. Please try again."
        }
    }

    private data class UploadedShareMedia(val photoUrls: List<String>, val videoUrl: String?)

    private fun resolveGeneratedString(name: String): String? {
        val id = appContext.resources.getIdentifier(name, "string", appContext.packageName)
        if (id == 0) return null
        return appContext.getString(id).takeIf { it.isNotBlank() }
    }

    private suspend fun <T> Task<T>.awaitLegacy(): T = suspendCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase request failed.")
            )
        }
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_VIEWED = "viewed"
        const val STATUS_ADDED = "added"
        const val STATUS_DISMISSED = "dismissed"
        private const val USERS = "users"
        private const val USERNAMES = "usernames"
        private const val SHARES = "recipeShares"
        private const val SUBSCRIPTIONS = "subscriptions"
        private const val PUBLIC_PROFILES = "publicProfiles"
        private const val RECIPES = "recipes"
        private const val RECIPE_LIKES = "recipeLikes"
        private const val TERMS_VERSION = "2026-07-13"
        private const val RECENT_LOGIN_WINDOW_MS = 5 * 60 * 1000L
        private const val FIRESTORE_DELETE_BATCH_SIZE = 450
        private val USERNAME = Regex("^[A-Za-z0-9_]{3,24}$")
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
