---
name: aura-expect-actual
description: Guides expect/actual pattern and platform API design in AiAura KMP. Use when adding storage, network, device APIs, or any behavior that differs per platform (Android, iOS, JVM, JS, Wasm).
---

# AiAura Expect/Actual Pattern

## When to use

- Adding file I/O, network, preferences, or device APIs
- Code needs to differ by platform (Android vs iOS vs Desktop vs Web)
- User asks how to share behavior across platforms or how to add platform-specific code

## Pattern

1. **commonMain:** Declare the contract with `expect` (function, class, or property). Use only types that exist in commonMain or are themselves expect/actual.

2. **Each platform:** Provide `actual` in the matching source set (`androidMain`, `iosMain`, `jvmMain`, `jsMain`/`webMain`, `wasmJsMain`). Only implement for platforms that need it; others can be stubs or omitted if the API is optional.

3. **No platform types in commonMain** — The `expect` signature must not reference `android.*`, `java.*`, `kotlinx.browser`, etc. Use shared types (e.g. `String`, `ByteArray`, custom common interfaces).

## Example (concept)

```kotlin
// commonMain
expect fun platformName(): String

// androidMain
actual fun platformName(): String = "Android"

// iosMain
actual fun platformName(): String = "iOS"
```

## Checklist

- [ ] `expect` lives in commonMain only.
- [ ] Each required platform has an `actual` (or documented omission).
- [ ] commonMain has no platform-specific imports.
- [ ] Prefer one expect/actual surface; keep platform details inside `actual` implementations.
