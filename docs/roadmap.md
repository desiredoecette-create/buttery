# Buttery roadmap

## Current MVP

The Android tablet application is the primary experience. It includes recipe creation/import,
albums, favorites, grocery lists, sharing/inbox and profile scaffolding, continue-recipe state,
cooking mode, and ambient slideshow behavior. Room/local persistence is established. Google
Firebase authentication is partially integrated, while portions of accounts and sharing still
use an on-device MVP fallback and are not production-ready cross-device services.

## Stabilize the product foundation

1. Replace local password/account behavior with Firebase Authentication.
2. Implement Firestore repository adapters and deterministic offline/sync behavior.
3. Finalize version 1 contracts and test Security Rules in the Emulator Suite.
4. Add crash reporting, analytics consent, accessibility checks, and CI.
5. Add repository tests and end-to-end tests around recipe ownership and sharing.

## Next product features

- Cross-device recipe, album, favorite, grocery, and profile sync
- Reliable share delivery, inbox state, notifications, and share acceptance
- Background media upload with retry and cleanup
- Search, filtering, duplicate detection, and improved recipe import
- Household/collaborative ownership only after single-owner permissions are stable

## Android phone roadmap

1. Add Material 3 adaptive dependencies and a tested `WindowSizeClass` policy.
2. Extract navigation, grid, and pane decisions from feature content.
3. Introduce compact bottom navigation and portrait-first single-pane destinations.
4. Make recipe/cooking screens responsive and validate text scaling and foldables.
5. Add compact-width screenshots, UI tests, and Play Store device testing.

The phone is a layout/navigation variant of `com.buttery.app`, not another application.

## iPhone roadmap

1. Create `ios/Buttery.xcodeproj` with SwiftUI and a minimum supported iOS version.
2. Add Firebase through Swift Package Manager and environment-specific plist configuration.
3. Implement contract DTOs, authentication, repositories, and an offline/cache policy.
4. Deliver recipes and profile/authentication first, then albums/favorites and grocery.
5. Add sharing/inbox, cooking mode, media upload, and push notifications.
6. Add contract compatibility and Firebase Emulator integration tests to CI.
