# Backend

All clients use one Firebase project per environment and the same collection names and contracts.

- `firestore-rules/`: Firestore and Storage authorization
- `cloud-functions/`: privileged workflows and event handlers
- `migrations/`: idempotent, versioned administrative migrations
- `seed-data/`: emulator-only fixtures; never production user data

Run Firebase CLI commands from this directory. Create explicit projects such as development,
staging, and production and select them with Firebase CLI aliases. Do not embed environment names
in collection paths.

Before deployment, validate rules and functions against the Emulator Suite. Backend contract
changes require updates to `../shared/contracts/`, `../docs/firestore-schema.md`, and both clients.
