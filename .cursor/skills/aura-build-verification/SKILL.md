---
name: aura-build-verification
description: Runs and interprets AiAura multi-platform build and test commands before claiming success or merge. Use when verifying a change, before merge, or when build/tests fail on Android, Desktop, JS, Wasm, or iOS.
---

# AiAura Build Verification

## When to use

- Before claiming a feature or fix is complete
- User says "verify the build" or "run the checks"
- Debugging a failing build

## Commands (from AGENTS.md / project rules)

**Builds**

| Platform   | Command |
|-----------|---------|
| Android   | `./gradlew :composeApp:assembleDebug` |
| Desktop   | `./gradlew :composeApp:run` |
| JS Web    | `./gradlew :composeApp:jsBrowserDevelopmentRun` |
| Wasm Web  | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| iOS framework | `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` |
| iOS app   | Open `iosApp` in Xcode, build/run there |

**Tests** (run when the change touches code covered by tests)

| Target   | Command |
|----------|---------|
| JVM      | `./gradlew :composeApp:jvmTest` |
| JS       | `./gradlew :composeApp:jsTest` |
| Wasm     | `./gradlew :composeApp:wasmJsTest` |

## Process

1. **Scope** — Run at least one build per **impacted** platform (e.g. if only commonMain changed, JVM + one web target is often enough; if androidMain changed, run Android).
2. **Tests** — If the change touches logic that has tests, run the relevant test task (jvmTest, jsTest, wasmJsTest) and report pass/fail.
3. **UI changes** — Verify Android + iOS (if on macOS) + one web target.
4. **Environment:**
   - Headless/cloud: `DISPLAY=:1 SKIKO_RENDER_API=SOFTWARE_FAST` for Desktop.
   - iOS: Only on macOS; do not claim iOS build on Linux.

## Before claiming success

- Run the relevant build(s) and, when applicable, tests; report pass/fail.
- Do not say "build should pass" or "tests should pass" without having run them.
