# Android

This directory is the Android Studio/Gradle project for both tablet and phone. Open this directory
in Android Studio.

The app remains in `app/` until package boundaries are stable. The sibling directories describe
the intended Gradle modules:

- `core/`: logging, dispatchers, result/error types, platform services
- `data/`: Room, Firebase data sources, DTO mapping, repository implementations
- `domain/`: platform-independent Kotlin models and use cases
- `features/`: feature-owned presentation and navigation code
- `ui/`: adaptive design system, common components, and window/pane policies
- `shared/`: Android-wide test fixtures and utilities that do not belong to a feature

Do not create separate tablet and phone application modules. Device differences are selected at
runtime using window size classes and adaptive navigation/pane policies.
