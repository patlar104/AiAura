# AGENTS.md

## Cursor Cloud specific instructions

This is a **Kotlin Multiplatform (KMP) + Compose Multiplatform** project called `aiaura` targeting Android, iOS, Desktop (JVM), Web (JS), and Web (Wasm).

### Environment prerequisites

- **JDK 21** must be available; set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
- **Android SDK** at `/opt/android-sdk` with platform 36 and build-tools 36.0.0. A `local.properties` with `sdk.dir=/opt/android-sdk` must exist in the repo root (gitignored).
- `ANDROID_HOME=/opt/android-sdk` must be exported.

### Build, test, lint, and run commands

See `README.md` for full commands. Quick reference:

| Task | Command |
|---|---|
| Build JVM | `./gradlew :composeApp:compileKotlinJvm` |
| Build Android APK | `./gradlew :composeApp:assembleDebug` |
| Run Desktop (JVM) | `DISPLAY=:1 SKIKO_RENDER_API=SOFTWARE_FAST ./gradlew :composeApp:run` |
| Run Web (Wasm) | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Run Web (JS) | `./gradlew :composeApp:jsBrowserDevelopmentRun` |
| JVM tests | `./gradlew :composeApp:jvmTest` |
| JS tests | `./gradlew :composeApp:jsTest` |
| Wasm tests | `./gradlew :composeApp:wasmJsTest` |
| Android lint | `./gradlew :composeApp:lint` |

### Cloud VM gotchas

- **Desktop (JVM) rendering:** The cloud VM has no GPU. You must set `SKIKO_RENDER_API=SOFTWARE_FAST` when running `./gradlew :composeApp:run` so Skiko uses software rendering instead of trying (and failing) to create an OpenGL context.
- **DISPLAY:** The virtual display is at `:1`. Always export `DISPLAY=:1` before running the Desktop app.
- **Wasm browser target:** The Wasm target may encounter a `kotlin.js.JsException: Cannot read properties of undefined (reading 'optParameter')` error in the browser. This is a known Kotlin/Wasm toolchain issue in this environment. The JS target (`jsBrowserDevelopmentRun`) or Desktop JVM target are more reliable for manual testing.
- **iOS target:** Cannot build on Linux — requires macOS + Xcode.
- **Gradle daemon:** The first Gradle invocation downloads Gradle 8.14.3 and all dependencies, which takes several minutes. Subsequent builds use the configuration cache and are much faster.
- All Gradle commands should include `JAVA_HOME` and `ANDROID_HOME` env vars (already in `~/.bashrc` after setup).
