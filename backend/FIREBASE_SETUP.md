# Buttery Firebase activation

The app currently runs its account and sharing MVP through `AccountRepository`, a persistent
on-device fallback. This makes the full UI flow testable without credentials, but it is not a
production authentication or cross-device cloud backend.

## Required project setup

1. Create/register Android app `com.buttery.app` in Firebase.
2. Put the downloaded `google-services.json` in `android/app/`.
3. Enable Authentication providers:
   - Email/password
   - Google
   - Facebook (requires Facebook app ID/secret and Firebase's OAuth redirect URI)
4. Add the tablet/debug SHA-1 and SHA-256 certificates for Google Sign-In.
5. Create Firestore and Storage, then run Firebase CLI deployment from `backend/`.
6. Add the Google Services Gradle plugin, Firebase Auth, Firestore, and Storage dependencies.
7. Implement `FirebaseAccountRepository` behind the same operations exposed by
  `AccountRepository`; switch the Android composition root in `RecipeTabletApp`.

## Required backend behavior

- Reserve `usernames/{usernameLowercase}` and create/update `users/{uid}` in one Firestore
  transaction. Never rely on a client query alone for uniqueness.
- On profile image selection, upload to `users/{uid}/profile/avatar`, then write its download URL
  to `users/{uid}.profilePhotoUrl`.
- On each successful local Room recipe/album create or update, upsert its Firestore document with
  `ownerId = FirebaseAuth.currentUser.uid`. Keep Room writes first so cloud failures never delete
  or block existing local data.
- A full two-way migration is intentionally out of MVP scope. Add an idempotent background upload
  job before enabling cross-device restoration of pre-existing local recipes.
- Share creation must copy recipe fields into `recipeSnapshot`; the recipient preview must not
  require permission to read the sender's recipe document.
- Listen/query for `recipeShares` where `toUserId == uid` and status is `pending` or `viewed`.

## Firestore document shapes

- `users/{uid}`: `userId`, `username`, `email`, `displayName`, `profilePhotoUrl`,
  `createdAt`, `updatedAt`
- `usernames/{usernameLowercase}`: `userId`
- `recipes/{recipeId}`: `ownerId`, recipe fields, `albumId`, media URLs, favorite state,
  timestamps
- `albums/{albumId}`: `ownerId`, `name`, `customCoverImageUrl`, timestamps
- `recipeShares/{shareId}`: sender/recipient IDs and usernames, `recipeId`,
  `recipeSnapshot`, `message`, `status`, timestamps

## Facebook status

The Facebook button is intentionally scaffolded only. Activation needs a Facebook developer app,
package/key-hash configuration, its app ID/token in Android resources, the Facebook Login SDK,
and `FacebookAuthProvider` credential exchange with Firebase Auth.

## Production requirements

Remove the local password fallback before distribution. It uses a device-local SHA-256 hash only
to exercise the UI and is not a substitute for Firebase Authentication. Validate rules with the
Firebase Emulator Suite, especially username races and share status updates.

Use separate Firebase projects for development, staging, and production. Android and iOS builds
for the same environment must point to the same Firebase project. Keep downloaded client
configuration files out of shared contracts; they identify an environment but do not define the
data model.
