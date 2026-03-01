# Project Rules (AiAura)

**For AI agents:** When working on this repo, read and follow this file. Use it for: where to put code (commonMain vs platform folders), what not to edit (generated/build/IDE), expect/actual usage, and which build commands to run before claiming success.

These rules keep Android, iOS, Desktop, Web (JS), and Web (Wasm) working together in this Kotlin Multiplatform project.

## 1) Edit Location Rules

1. Put shared UI and business logic in `composeApp/src/commonMain`.
2. Only use platform folders for platform-only APIs:
   - Android: `composeApp/src/androidMain`
   - iOS Kotlin bridge: `composeApp/src/iosMain`
   - Desktop JVM: `composeApp/src/jvmMain`
   - JS Web: `composeApp/src/jsMain` and `composeApp/src/webMain`
   - Wasm Web: `composeApp/src/wasmJsMain`
3. Keep iOS Swift entry code in `iosApp/iosApp` minimal. Prefer implementing behavior in shared Kotlin code.

## 2) Build Configuration Ownership

1. Keep target/dependency/source-set wiring in `composeApp/build.gradle.kts`.
2. Keep dependency and plugin versions centralized in `gradle/libs.versions.toml`.
3. Keep iOS app metadata/signing values in `iosApp/Configuration/Config.xcconfig`.
4. Only change app identifiers/version numbers in one place per platform, then verify build.

## 3) Do Not Edit Generated/Tooling Output

Do not manually edit:

1. `build/`
2. `composeApp/build/`
3. `.gradle/`
4. `.kotlin/`
5. IDE metadata under `.idea/` unless the change is intentionally IDE-specific.

## 4) Source-Set Safety Rules

1. `commonMain` must not import Android/iOS/JVM/browser-specific APIs.
2. Platform-specific implementations must satisfy shared `expect` contracts.
3. If adding platform behavior, prefer `expect/actual` pattern over `if`-style platform branching.

## 5) Required Build Checks Before Merge

Run these after meaningful changes:

1. Android: `./gradlew :composeApp:assembleDebug`
2. Desktop: `./gradlew :composeApp:run`
3. JS Web: `./gradlew :composeApp:jsBrowserDevelopmentRun`
4. Wasm Web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
5. iOS framework for Xcode: `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
6. iOS app run/build: open `iosApp` in Xcode and build/run there.

## 6) Change Checklist

For every feature or fix:

1. Confirm correct source-set placement (`commonMain` first).
2. Confirm dependencies were added in the right scope.
3. Confirm at least one build command for each impacted platform passes.
4. If UI changed, verify Android + iOS + one web target.

