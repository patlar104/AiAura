# AiAura Subagent Prompts

Use these prompts when launching a subagent (e.g. `mcp_task` with `generalPurpose` or `explore`) so the subagent respects the KMP project rules. Copy the relevant block into the task description.

---

## 1) KMP source-set explorer

Use when: Searching the codebase for where code lives or where to add new code.

```
You are working in the AiAura Kotlin Multiplatform project. Source sets:
- commonMain: shared UI and business logic (composeApp/src/commonMain)
- androidMain, iosMain, jvmMain, jsMain, webMain, wasmJsMain: platform-only code
- commonMain must NOT import platform-specific APIs.
When reporting file locations or suggesting where to add code, always state the correct source set and respect these boundaries.
```

---

## 2) Multi-platform build verifier

Use when: Verifying builds or debugging build failures.

```
AiAura targets: Android, iOS, Desktop (JVM), Web (JS), Web (Wasm). Commands:
- Android: ./gradlew :composeApp:assembleDebug
- Desktop: ./gradlew :composeApp:run
- JS Web: ./gradlew :composeApp:jsBrowserDevelopmentRun
- Wasm Web: ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
- iOS: ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode (macOS only)
On headless/cloud: use SKIKO_RENDER_API=SOFTWARE_FAST and DISPLAY=:1 for Desktop. Do not claim iOS build success on Linux.
Run only the builds that are relevant to the change and environment.
```

---

## 3) Expect/actual and platform API reviewer

Use when: Adding or reviewing platform-specific behavior or expect/actual code.

```
In AiAura (KMP), platform behavior must use expect/actual: define expect in commonMain, actual in each platform source set. commonMain must not contain platform-specific types or imports. When reviewing or suggesting code, ensure: (1) one expect declaration in commonMain, (2) one actual per platform that needs it, (3) no Android/iOS/JVM/browser imports in commonMain.
```

---

## 4) Gradle and dependency scope checker

Use when: Adding dependencies or editing build configuration.

```
AiAura build ownership: target and source-set wiring in composeApp/build.gradle.kts; dependency and plugin versions in gradle/libs.versions.toml. Do not edit build/, .gradle/, .kotlin/. When suggesting dependency changes, specify the correct source-set scope (e.g. commonMain vs androidMain) and whether to add the version to libs.versions.toml.
```

---

## 5) Pre-merge checklist runner

Use when: Confirming a feature or fix is ready before merge.

```
Before claiming work is complete for AiAura: (1) Confirm code is in the correct source set (commonMain first). (2) Confirm dependencies are in the right scope. (3) Run at least one successful build per impacted platform (Android, Desktop, JS Web, Wasm Web; iOS on macOS). (4) If UI changed, verify Android + iOS + one web target. Report which checks were run and their result.
```

---

## 6) Compose and UI placement checker

Use when: Adding or moving Compose UI or theming.

```
AiAura uses Compose Multiplatform. Shared UI and theming belong in composeApp/src/commonMain. Platform-specific UI only in androidMain, iosMain, jvmMain, jsMain, webMain, or wasmJsMain. When suggesting UI code, place it in commonMain unless it uses platform-only APIs; then use expect/actual with the API in commonMain and implementation in platform source sets.
```
