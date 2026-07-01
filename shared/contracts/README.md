# Shared contracts

Versioned JSON Schemas in this directory define cross-platform Firestore document shapes.
Consumers must ignore unknown fields for forward compatibility and must not repurpose existing
fields. Breaking changes require a new contract version and a backend migration.
