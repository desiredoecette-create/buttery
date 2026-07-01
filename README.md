# Buttery

Buttery is a single-repository product for Android tablet, Android phone, and iPhone. The current
shipping client is the Kotlin/Jetpack Compose Android tablet app. Android phone and SwiftUI iPhone
clients will use the same Firebase project and versioned data contracts.

## Repository

- `android/` — buildable Android application and future Android module boundaries
- `ios/` — Xcode-ready workspace boundary for the future SwiftUI application
- `backend/` — Firebase rules, functions, migrations, seed data, and environment setup
- `shared/contracts/` — platform-neutral JSON Schemas; the source of truth for persisted data
- `docs/` — architecture, roadmap, navigation, backend schema, and feature specifications
- `design-assets/` — source branding and design files (not generated platform resources)
- `scripts/` — repository automation

## Android

```bash
cd android
./gradlew assembleDebug
```

Android application ID: `com.buttery.app`.

The current application remains one Gradle module while package boundaries are stabilized. See
[`docs/architecture.md`](docs/architecture.md) for the incremental modularization plan.
The tablet experience is the current MVP. Android phone support is planned as an adaptive layout
within the same Android application.

## Backend

Firebase is shared by every client. Rules and deployment configuration live in `backend/`.
Never create platform-specific collections or silently change a persisted field. Update the
versioned contracts and schema documentation first.

## iOS

Open the `ios/` directory in Xcode when bootstrapping the native project, then create the
`Buttery.xcodeproj` in that directory. The intended SwiftUI structure and dependency boundaries
are documented in `ios/README.md`.

## Roadmap

1. Preserve and stabilize the Android tablet MVP.
2. Add adaptive Android phone layouts to the existing Android application.
3. Build a native SwiftUI iPhone application against the shared contracts.
4. Expand the Firebase backend, rules, and migrations as shared features require.

Detailed milestones and sequencing are maintained in [`docs/roadmap.md`](docs/roadmap.md).

## Configuration

Local Android SDK paths, signing files, service-account keys, and Apple configuration files must
not be committed. Firebase setup is documented in `backend/FIREBASE_SETUP.md`.
