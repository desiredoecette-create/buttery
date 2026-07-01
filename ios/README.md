# iOS

This directory is the future native SwiftUI client boundary. In Xcode:

1. Open `ios/` and create an iOS App project named `Buttery` at this directory.
2. Keep `Buttery.xcodeproj` and the app target under `Buttery/`.
3. Add Firebase with Swift Package Manager: Auth, FirebaseFirestore, and FirebaseStorage.
4. Add `GoogleService-Info.plist` to the app target locally; do not commit production secrets.
5. Generate or hand-maintain `Codable` DTOs against `../shared/contracts/v1/`.

Suggested ownership:

- `Buttery/`: app entry point, composition root, configuration, top-level navigation
- `Shared/`: design system, networking/Firebase adapters, contract DTOs, utilities
- `Resources/`: asset catalogs, localization, fonts, privacy manifests
- `Features/`: Swift packages or groups for recipes, albums, grocery, sharing, inbox, and profile

The iOS UI is native SwiftUI. It shares contracts and backend behavior, not Android UI or
platform persistence implementation.
