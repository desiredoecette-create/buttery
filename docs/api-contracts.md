# API contracts

Buttery currently uses Firebase client SDK operations rather than a general REST API. “Request”
and “response” examples below describe logical repository operations. Firestore documents conform
to `shared/contracts/v1/`.

## Shared repository interfaces

Every platform should expose equivalent domain operations:

```text
AuthRepository.observeSession()
AuthRepository.signIn(provider)
AuthRepository.signOut()

RecipeRepository.observeRecipes(ownerId)
RecipeRepository.getRecipe(recipeId)
RecipeRepository.upsertRecipe(recipe)
RecipeRepository.deleteRecipe(recipeId)

AlbumRepository.observeAlbums(ownerId)
GroceryRepository.observeList(listId)
ShareRepository.sendRecipe(recipientId, recipeSnapshot, message)
ShareRepository.observeInbox(userId)
ShareRepository.updateStatus(shareId, status)
ProfileRepository.getProfile(userId)
ProfileRepository.updateProfile(profile)
```

These are semantic interfaces, not a mandate to share Kotlin/Swift source. Android uses Kotlin
flows/suspend functions; iOS uses `AsyncSequence`, `async` functions, or observable state.

## Create or update recipe

Request:

```json
{
  "id": "recipe_01J...",
  "ownerId": "firebase_uid",
  "title": "Weeknight Pasta",
  "notes": "",
  "prepTime": "10 min",
  "cookTime": "20 min",
  "totalTime": "30 min",
  "servings": "4",
  "ingredients": "340 g pasta\n2 tbsp butter",
  "instructions": "Boil pasta.\nFinish with butter.",
  "photoUrls": [],
  "videoUrl": null,
  "sourceUrl": null,
  "originalRawText": "",
  "albumId": null,
  "isFavorite": false,
  "createdAt": 1782840000000,
  "updatedAt": 1782840000000,
  "schemaVersion": 1
}
```

Response:

```json
{
  "id": "recipe_01J...",
  "updatedAt": 1782840000000,
  "syncState": "synced"
}
```

`syncState` is client/domain state and is not required in the Firestore document.

## Send recipe share

Request:

```json
{
  "shareId": "share_01J...",
  "sourceRecipeId": "recipe_01J...",
  "fromUserId": "sender_uid",
  "fromUsername": "alex",
  "toUserId": "recipient_uid",
  "toUsername": "sam",
  "recipeSnapshot": {
    "title": "Weeknight Pasta",
    "ingredients": "340 g pasta\n2 tbsp butter",
    "instructions": "Boil pasta.\nFinish with butter."
  },
  "message": "Try this one",
  "status": "pending",
  "createdAt": 1782840000000,
  "updatedAt": 1782840000000,
  "schemaVersion": 1
}
```

Response:

```json
{
  "shareId": "share_01J...",
  "status": "pending"
}
```

## Update share status

Request:

```json
{
  "status": "accepted",
  "updatedAt": 1782840060000
}
```

Only the recipient may make this update. Retrying the same terminal transition must not duplicate
an imported recipe; acceptance needs an idempotency key derived from the share ID.

## Grocery list

```json
{
  "id": "default",
  "ownerId": "firebase_uid",
  "mode": "TYPE",
  "typedText": "Butter\nPasta",
  "strokes": [],
  "createdAt": 1782840000000,
  "updatedAt": 1782840000000,
  "schemaVersion": 1
}
```

## Mapping requirements

- Cloud IDs are strings on both platforms; local database keys may differ.
- Firestore timestamps map to platform date/time types at the data boundary.
- Device-local file/content URIs never enter cloud contracts.
- Status and mode values use the exact serialized strings defined by schemas.
- DTO decoders tolerate unknown fields but reject absent required ownership/identity fields.
