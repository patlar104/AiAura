---
name: aura-dependency-scope
description: Ensures dependencies and versions are added in the correct place and scope for AiAura KMP. Use when adding or updating dependencies, editing build.gradle.kts or libs.versions.toml.
---

# AiAura Dependency and Version Rules

## When to use

- Adding a new dependency to the project
- Updating library or plugin versions
- Editing `composeApp/build.gradle.kts` or `gradle/libs.versions.toml`

## Ownership

| What | Where |
|------|--------|
| Target/source-set wiring, dependency declarations | `composeApp/build.gradle.kts` |
| Plugin and dependency **versions** | `gradle/libs.versions.toml` |
| iOS app metadata/signing | `iosApp/Configuration/Config.xcconfig` |

Do **not** edit: `build/`, `composeApp/build/`, `.gradle/`, `.kotlin/` (generated).

## Scoping dependencies

- **commonMain** — Used by shared code (Kotlin stdlib, Compose, multiplatform libs). Most new code dependencies go here or in **commonTest** for tests.
- **androidMain** — Android-only (e.g. AndroidX, platform SDK).
- **iosMain** — iOS-only (e.g. Kotlin/Native APIs).
- **jvmMain** — Desktop JVM-only.
- **jsMain** / **webMain** / **wasmJsMain** — Browser/JS/Wasm-only.

When suggesting a dependency, state the **source set** and, if the version is not yet in the project, add or reference it in `libs.versions.toml`.

## After changing dependencies

Run at least one build for the affected target (e.g. `./gradlew :composeApp:assembleDebug` if Android scope changed).
