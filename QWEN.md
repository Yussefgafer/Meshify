# Meshify — Agent Guide
Offline-first P2P messaging for Android. No servers, no internet needed,for a personal use.

## Some info
- Currently app version v1.1.3
- New MD3E Expressive Design System at `core:ui/designsystem/` running alongside the old `core:ui/components/` system
- `feature:real-device-testing` is `debugImplementation` only (not in release APK)
- DeveloperScreen + RealDeviceTesting nav are guarded by `BuildConfig.DEBUG`

# Qwen instructions
- Always don't over engenering any code. 
- Don't use a normal cercle use Squirrel inset.
- This app is not a demo, mocking, MVP this is a real world app don't make mocking or demo data, be careful of what you type in the code.
- Don't commit with your self to commit use the`kt commit gard` SubAgent.
- Before committing any change update QWEN.md and README.md and CHANGELOG.md (if necessary) Make sure all updated before commit.
- Watchout to delete any file becouse some files is not tracked in git likt QWEN.md.
- After any big change or UI change, update **@CHANGELOG.md** by adding entries under the **last existing version** (DO NOT create a new version). Use this format:

```
[Feat]:For New feature
[Fix]:For Bug fix
[Docs]:For Documentation modification only
[Style]:For Code formatting without changing logic
[Refactor]:For Refactoring without changing behavior
[Perf]:For Performance improvement
[Test]:For Add or modify tests
[Chore]:For General maintenance (dependencies, build config, etc.)
[CI]:For CI/CD-specific modifications
etc
```

## Commands

```sh
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
./gradlew :core:domain:test --tests "*PayloadTest*"
./gradlew :core:domain:test
./gradlew lint
```

لا تستخدم اي تاجز مثل --no-daemon او -Dorg.gradle.jvmargs=-Xmx*m او --max-workers=* الا اذا اخبرك المستخدم بذالك.

<!-- codebase-memory-mcp:start -->
# Codebase Knowledge Graph (codebase-memory-mcp)

This project uses codebase-memory-mcp to maintain a knowledge graph of the codebase.
ALWAYS prefer MCP graph tools over grep/glob/file-search for code discovery.

## Priority Order
1. `search_graph` — find functions, classes, routes, variables by pattern
2. `trace_path` — trace who calls a function or what it calls
3. `get_code_snippet` — read specific function/class source code
4. `query_graph` — run Cypher queries for complex patterns
5. `get_architecture` — high-level project summary

## When to fall back to grep/glob
- Searching for string literals, error messages, config values
- Searching non-code files (Dockerfiles, shell scripts, configs)
- When MCP tools return insufficient results

## Examples
- Find a handler: `search_graph(name_pattern=".*OrderHandler.*")`
- Who calls it: `trace_path(function_name="OrderHandler", direction="inbound")`
- Read source: `get_code_snippet(qualified_name="pkg/orders.OrderHandler")`
<!-- codebase-memory-mcp:end -->

## Architecture

Strict module layering:

```
:app → :feature:* → :core:domain  ← zero Android deps
                → :core:common
                → :core:data      ← Room, DataStore, repos
                → :core:network   ← mDNS, TCP, BLE
                → :core:ui        ← M3 components, theme, navigation
```

- **Never** depend across feature modules (`:feature:chat` cannot import `:feature:home`)
- `:core:domain` is **pure Kotlin/JVM** — no Android imports allowed
- Source lives under `src/main/java/` (not `src/main/kotlin/`)

## Project quirks

- **QWEN.md is gitignored** — `QWEN.md`, TODO.md also ignored. Agent instruction files are untracked by design.
- Two domain package prefixes exist: `com.p2p.meshify.core.domain.*` and `com.p2p.meshify.domain.*`. The `domain.*` (without `core`) is the newer convention.
- Compiler opt-ins required: `ExperimentalMaterial3Api`, `ExperimentalMaterial3ExpressiveApi` (configured in `app/build.gradle.kts`)
- `abiFilters = arm64-v8a` only — no x86/32-bit builds
- Room schema dir: `$projectDir/schemas` (per module; `app/` and `core/data/` both export)
- `lint.abortOnError = false`, `lint.checkReleaseBuilds = false`
- `org.gradle.configuration-cache = false` in gradle.properties
- Navigation via `MeshifyNavHost` in `:core:ui`, routes defined in `Screen` sealed class
- BLE transport is optional (controlled by settings); default is LAN TCP + mDNS

## Localization

- Arabic (`values-ar/strings.xml`) and English (`values/strings.xml`) with RTL layout support
- Language selection persisted in DataStore (`appLanguage` flow)
- Language change triggers Activity recreation (`activity.recreate()`)

## Key dependencies

| Category | Library | Version source |
|---|---|---|
| UI | Compose BOM 2026.06.01 + M3 1.5.0-alpha24 | `libs.versions.toml` |
| Database | Room 2.8.4 + KSP | `libs.versions.toml` |
| DI | Hilt 2.60.1 + ksp | `libs.versions.toml` |
| Images | Coil 3.5.0 (OkHttp network) | `libs.versions.toml` |
| Navigation | Jetpack Navigation 2.9.8 | `libs.versions.toml` |
| Serialization | kotlinx-serialization 1.11.0 | `libs.versions.toml` |

All versions in `gradle/libs.versions.toml` — never hardcode versions in `build.gradle.kts`.

