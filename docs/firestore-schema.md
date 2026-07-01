# Firestore schema

This document describes the intended shared cloud model. The JSON Schemas under
`shared/contracts/v1/` are machine-readable companions. Current Android local models do not yet
fully implement this cloud model.

## Conventions

- Document IDs and references are strings.
- `createdAt` and `updatedAt` are Firestore timestamps in production. JSON examples use epoch
  milliseconds only for portability.
- Every document has `schemaVersion`.
- User-owned documents contain `ownerId`.
- Clients ignore unknown fields and validate required fields before mapping to domain models.

## `users/{uid}`

Private profile for the authenticated user: `userId`, `username`, `email`, `displayName`,
`profilePhotoUrl`, timestamps, and `schemaVersion`.

Public username discovery should not expose private profile fields. Use
`usernames/{usernameLowercase}` with only `userId` and minimal display metadata if required.

## `recipes/{recipeId}`

Owned recipe with stable string `id`, `ownerId`, title/notes/timing/serving fields, ingredient and
instruction content, `photoUrls`, optional `videoUrl` and `sourceUrl`, optional `albumId`,
`isFavorite`, timestamps, and `schemaVersion`.

Storage media lives under `users/{ownerId}/recipes/{recipeId}/...`. Firestore stores download URLs
or storage paths according to one documented policy; do not store device-local URIs in cloud data.

## `albums/{albumId}`

Owned album with `id`, `ownerId`, `name`, optional `customCoverImageUrl`, timestamps, and
`schemaVersion`. Recipe membership is represented by `recipes.albumId` for the current one-album
model. A future many-to-many model requires a migration and new contract.

## `groceryLists/{listId}`

Owned list with `id`, `ownerId`, `mode`, `typedText`, drawing `strokes`, timestamps, and
`schemaVersion`. Drawing points need normalized coordinates so Android and iOS render consistently.
If item-level collaboration is introduced, replace the text blob with versioned structured items.

## `recipeShares/{shareId}`

Immutable addressing plus mutable recipient state: `shareId`, `sourceRecipeId`, sender/recipient
IDs and usernames, `recipeSnapshot`, `message`, `status`, timestamps, and `schemaVersion`.
The snapshot lets a recipient preview a share without read permission on the sender's recipe.
Allowed states are `pending`, `viewed`, `accepted`, and `dismissed`.

## Inbox items

The MVP inbox is a query over `recipeShares` where `toUserId` is the signed-in user. Do not create
a second inbox collection until non-share notification types require it. If introduced later,
`users/{uid}/inbox/{itemId}` should reference a typed event and use an idempotent Cloud Function.

## Security model

- Users can read/write their own private profile and owned content.
- Signed-in users may resolve usernames through the minimal username index.
- Senders may create valid shares addressed to another UID.
- Sender and recipient may read a share; only the recipient may transition allowed status fields.
- Storage writes are owner-scoped, size-limited, and content-type validated.

Current rules cover users, usernames, recipes, albums, and recipe shares. Grocery list rules must
be added and emulator-tested before grocery cloud sync is enabled.

## Indexes and migration

Expected compound queries include recipient shares ordered by `createdAt`, owner recipes ordered
by `updatedAt`, owner favorites, and owner recipes filtered by `albumId`. Commit generated
Firestore indexes once queries are implemented. Migrations must be idempotent and stored under
`backend/migrations/`.
