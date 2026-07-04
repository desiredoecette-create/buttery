# iOS

The native SwiftUI iPhone client lives in `Buttery.xcodeproj`. Open that project in Xcode and
select the `Buttery` scheme. The current minimum deployment target is iOS 17.

The first implementation slice includes the app composition root, animated intro, adaptive
portrait dashboard, shared design tokens, contract-shaped recipe models, app icon, and Android
artwork reused as iOS resources.

Firebase is linked with Swift Package Manager (`FirebaseCore`, `FirebaseAuth`,
`FirebaseFirestore`, `FirebaseStorage`, and `GoogleSignIn`). The environment configuration remains
local and gitignored.

Register the iOS bundle ID `com.buttery.app` inside the **same Firebase project as Android** and
download its `GoogleService-Info.plist`. Place it at:

`ios/Buttery/GoogleService-Info.plist`

The Xcode project already includes this file in the Buttery target and configures its Google
callback URL scheme, so no manual Xcode setup is required. The app shows a configuration warning
rather than crashing while this file is absent.

To verify both platforms point to the same backend, compare `project_info.project_id` in
`android/app/google-services.json` with `PROJECT_ID` in the iOS plist. Both config files are
gitignored and must never be committed.

Auth profile creation uses the existing collections `users` and `usernames`. Recipe, album,
sharing, and grocery sync will use `recipes`, `albums`, `recipeShares`, and `groceryLists`
respectively; auth integration does not create alternate collection names.

Security follow-up: emulator-test rules guaranteeing that users can only access their own profile
and owned recipes/albums, username lookup is readable to authenticated users, and username
reservation cannot overwrite an existing document. Recipe-sharing rules remain a separate feature.

Suggested ownership:

- `Buttery/`: app entry point, composition root, configuration, top-level navigation
- `Shared/`: design system, networking/Firebase adapters, contract DTOs, utilities
- `Resources/`: asset catalogs, localization, fonts, privacy manifests
- `Features/`: Swift packages or groups for recipes, albums, grocery, sharing, inbox, and profile

The iOS UI is native SwiftUI. It shares contracts and backend behavior, not Android UI or
platform persistence implementation.
