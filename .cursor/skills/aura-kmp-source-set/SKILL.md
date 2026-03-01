---
name: aura-kmp-source-set
description: Enforces AiAura Kotlin Multiplatform source-set placement and boundaries. Use when adding or moving code, deciding where a new file or class belongs, or when reviewing changes that touch composeApp/src.
---

# AiAura KMP Source-Set Placement

## When to use

- Adding new Kotlin/Compose code under `composeApp/src`
- Moving code between modules or source sets
- User asks "where should this go?" or "commonMain vs platform"

## Rules

1. **Default: `commonMain`** — Shared UI and business logic go in `composeApp/src/commonMain`. Prefer this unless the code needs a platform-only API.

2. **Platform folders only for platform APIs:**
   - `androidMain` — Android
   - `iosMain` — iOS Kotlin bridge
   - `jvmMain` — Desktop JVM
   - `jsMain` / `webMain` — JS Web
   - `wasmJsMain` — Wasm Web

3. **commonMain must not import** `android.*`, `ios.*`, `java.*` (platform), `kotlinx.browser`, or other platform-specific packages. If the code needs such APIs, use **expect/actual**: declare the API in commonMain with `expect`, implement in each platform source set with `actual`.

4. **Before suggesting a path**, state the source set and why (e.g. "commonMain — no platform APIs" or "androidMain — uses Context").

## Quick check

- New screen or ViewModel with no device/OS APIs → `commonMain`
- File storage, network, or system API → expect in commonMain + actual per platform
- Android-only UI or system service → `androidMain` (and consider exposing via expect/actual if shared behavior is needed)
