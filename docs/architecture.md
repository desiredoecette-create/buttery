# Architecture

## Repository structure

```text
android/              Android Studio project for tablet and phone
  app/                Current buildable application/composition root
  core/               Future shared infrastructure modules
  data/               Future persistence, Firebase, sync, and repository implementations
  domain/             Future pure Kotlin models, interfaces, and use cases
  features/           Future feature presentation modules
  ui/                 Future adaptive design system and layout policies
  shared/             Cross-feature Android test/support code
ios/                  Future Xcode/SwiftUI project and feature boundaries
backend/              Firebase rules, functions, migrations, and emulator seed data
shared/contracts/     Versioned, platform-neutral persisted-data schemas
docs/                 Product and engineering documentation
design-assets/        Shared source artwork and brand assets
scripts/              CI and developer automation
```

## Client layers

Both clients follow the same dependency direction:

```text
App/composition → Features/presentation → Domain ← Data/platform adapters
                          ↓
                    Shared UI system
```

- **Domain** owns business models, repository interfaces, validation, and use cases. It has no
  Compose, SwiftUI, Firebase, Room, or Firestore dependencies.
- **Data** implements domain repositories using local persistence, Firebase, media storage, and
  sync. Firestore DTOs map explicitly to domain models.
- **Features** own presentation state, screens, and feature navigation entry points.
- **UI** owns tokens, reusable components, accessibility, and adaptive layout policies.
- **App** composes dependencies, selects environment configuration, and owns top-level navigation.

## Android modularization

Keep the existing `android/app` build operational while extracting seams in this order:

1. Move pure models and repository interfaces to `domain`.
2. Put Room/Firebase implementations and mappers in `data`.
3. Extract the design system and adaptive policies to `ui`.
4. Extract features one at a time, beginning with recipes and authentication.
5. Add narrowly scoped `core` modules only when multiple modules genuinely need them.

Recommended eventual Gradle modules include `:domain:model`, `:domain:usecase`, `:data:local`,
`:data:firebase`, `:core:common`, `:core:testing`, `:ui:designsystem`, and feature modules such as
`:features:recipes`. Avoid a single oversized “shared” module.

## Android adaptive strategy

Use one activity and one application ID. Compute a stable layout policy from Material 3
`WindowSizeClass`, posture, and available bounds:

| Width policy | Navigation | Content |
| --- | --- | --- |
| Compact | Bottom bar | Single pane, one-column lists/cards |
| Medium | Navigation rail where useful | Wider cards or list/detail when space permits |
| Expanded | Rail/dashboard navigation | Multi-pane dashboard and larger grids |

Shared feature state and actions must not know whether a screen is compact or expanded. A feature
provides compact and expanded arrangements over the same state. Pane count, column count, spacing,
and navigation chrome are adaptive—not separate tablet/phone features.

Tablet-specific behavior remains limited to mounted-kitchen defaults, landscape optimization,
ambient slideshow presentation, expanded dashboard composition, and optional multi-pane cooking.
Immersive mode and keep-awake behavior should be capability/policy driven and activated only for
cooking or mounted experiences, not assumed for every large screen.

## Cross-platform reuse

Kotlin and Swift UI code are intentionally native. Reuse occurs through:

- One Firebase backend and authorization model
- Versioned JSON contracts and matching timestamp/status semantics
- Shared product specifications, navigation semantics, and acceptance criteria
- Shared source assets with platform-specific generated renditions
- Common emulator fixtures and contract compatibility tests

Kotlin Multiplatform can be evaluated later for validation or parsing, but should not precede
stable contracts and repository boundaries. Firebase and local persistence behavior must remain
explicit per platform.

## Compatibility rules

- Clients ignore unknown document fields.
- Existing fields are never repurposed.
- Additive optional changes stay within a contract version; breaking changes create a new version.
- Every persisted document includes `schemaVersion`.
- Server timestamps are authoritative for cloud ordering; local times are provisional.
- IDs in cloud contracts are strings. Android Room `Long` IDs remain local implementation details
  and require a separate stable cloud ID before sync.
