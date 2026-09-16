# VaultShelf Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish VaultShelf's Compose/i18n/Fluent application shell while preserving Gander's existing file browser and document viewer.

**Architecture:** Add a Compose launcher activity rather than replacing `MainActivity`. `VaultShelfActivity` owns the new shell; the existing Gander activities remain isolated and callable. A local theme package centralizes Fluent-inspired visual tokens, while a resource-parity test makes Simplified Chinese coverage a CI invariant.

**Tech Stack:** Kotlin 2.1.0, Android Gradle Plugin 8.11.2, compileSdk 36, Jetpack Compose BOM 2026.06.00, Compose Material 3, AppCompat, Robolectric/JUnit/Truth.

**Spec:** `docs/superpowers/specs/2026-09-17-vaultshelf-foundation-design.md`

## Global Constraints

- Preserve `MainActivity` and `ViewerActivity` behavior.
- Preserve the zero-permission/offline model.
- Every new visible string uses `R.string`/`stringResource`.
- `values-zh-rCN/strings.xml` must cover every translatable fallback key.
- New shell follows Windows 11 / Fluent 2 visual direction.
- Do not add GPL/AGPL source.
- Do not add novel/vault/editing engines in this milestone.

---

### Task 1: Lock the localization contract

**Files:**
- Create: `app/src/test/java/com/arjun/gander/LocalizationContractTest.kt`
- Create: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Android XML string resources.
- Produces: CI invariant that every translatable fallback key has a Simplified Chinese translation.

- [ ] **Step 1: Write the failing localization parity test**

Create a JUnit test that reads both resource files from the repository, parses `<string>` elements, excludes entries with `translatable="false"`, and asserts that the Chinese key set contains every translatable fallback key.

- [ ] **Step 2: Verify the test fails before the Chinese resource exists**

Run: `./gradlew testDebugUnitTest --tests com.arjun.gander.LocalizationContractTest`

Expected: FAIL because `values-zh-rCN/strings.xml` does not exist or has missing keys.

- [ ] **Step 3: Add the full Simplified Chinese resource set**

Translate every translatable Gander key and add the VaultShelf shell keys. Keep formatting placeholders (`%1$s`, `%1$d`, `%%`) unchanged.

- [ ] **Step 4: Add matching English shell keys**

Add `vaultshelf_*` strings to `values/strings.xml`; no shell copy is embedded in Kotlin.

- [ ] **Step 5: Verify localization tests pass**

Run: `./gradlew testDebugUnitTest --tests com.arjun.gander.LocalizationContractTest`

Expected: PASS.

### Task 2: Enable Compose on the existing toolchain

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: Compose compiler/plugin and libraries available to the app module.

- [ ] **Step 1: Add the Kotlin Compose compiler plugin**

Add `org.jetbrains.kotlin.plugin.compose` version `2.1.0` at the root and apply it in the app module.

- [ ] **Step 2: Enable Compose build features**

Set `buildFeatures { compose = true }` without changing compileSdk, minSdk, targetSdk, namespace, or application ID.

- [ ] **Step 3: Add compatible stable Compose dependencies**

Use `androidx.compose:compose-bom:2026.06.00` with Material 3, foundation, UI, tooling-preview, and debug tooling. Do not add navigation-compose for the first shell.

- [ ] **Step 4: Compile the app module**

Run: `./gradlew compileDebugKotlin`.

Expected: PASS with the existing AGP 8.11.2 / compileSdk 36 project.

### Task 3: Add the VaultShelf Fluent design system

**Files:**
- Create: `app/src/main/java/com/arjun/gander/ui/theme/VaultShelfColor.kt`
- Create: `app/src/main/java/com/arjun/gander/ui/theme/VaultShelfTheme.kt`
- Create: `app/src/main/java/com/arjun/gander/ui/theme/VaultShelfType.kt`

**Interfaces:**
- Produces: `@Composable fun VaultShelfTheme(content: @Composable () -> Unit)` and shared Fluent surface tokens.

- [ ] **Step 1: Define light/dark semantic palettes**

Use restrained Windows-inspired blue accents, neutral layered backgrounds, dark equivalents, and high-contrast content colors.

- [ ] **Step 2: Define typography and shapes**

Use compact system typography and 12/16 dp rounded surface conventions.

- [ ] **Step 3: Implement `VaultShelfTheme`**

Wrap Material 3 solely as the component foundation; product colors and shapes come from VaultShelf tokens rather than dynamic Material You colors.

### Task 4: Add the Compose launcher shell

**Files:**
- Create: `app/src/main/java/com/arjun/gander/VaultShelfActivity.kt`
- Create: `app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- `VaultShelfActivity` is the `MAIN`/`LAUNCHER` activity.
- `MainActivity` remains exported but loses only the launcher intent filter.
- `VaultShelfShell(onOpenFiles: () -> Unit)` is the shell boundary; Files launches `MainActivity`.

- [ ] **Step 1: Add `VaultShelfActivity`**

Use an AppCompat/ComposeView boundary so the existing app theme remains valid. Apply edge-to-edge-safe system insets and render `VaultShelfTheme` + `VaultShelfShell`.

- [ ] **Step 2: Add five localized destinations**

Home, Library, Files, Vault, Settings use resource strings. Navigation state is local `rememberSaveable`; no navigation framework is required yet.

- [ ] **Step 3: Implement the Fluent home surface**

Add a product header and rounded bordered cards for documents, library, and vault. Files is functional; future destinations show localized foundation-state copy rather than dead controls.

- [ ] **Step 4: Move the launcher intent filter**

Declare `VaultShelfActivity` as launcher. Keep all existing `ViewerActivity` VIEW/SEND filters unchanged.

### Task 5: Protect the shell contract with tests

**Files:**
- Create: `app/src/test/java/com/arjun/gander/VaultShelfManifestTest.kt`

**Interfaces:**
- Protects the launcher split and external document handler.

- [ ] **Step 1: Add manifest contract assertions**

Parse `AndroidManifest.xml` as text/XML and assert that VaultShelfActivity is declared, MainActivity still exists, and ViewerActivity remains declared with VIEW/SEND actions.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew testDebugUnitTest --tests com.arjun.gander.LocalizationContractTest --tests com.arjun.gander.VaultShelfManifestTest`.

Expected: PASS.

### Task 6: Full verification and pull request

**Files:**
- No production files beyond Tasks 1-5.

- [ ] **Step 1: Run repository unit tests**

Run: `./gradlew testDebugUnitTest`.

- [ ] **Step 2: Run lint**

Run: `./gradlew lintDebug`.

- [ ] **Step 3: Build debug and release APKs**

Run: `./gradlew assembleDebug assembleRelease`.

- [ ] **Step 4: Review diff for scope**

Confirm no permission additions, no viewer renderer edits, no hard-coded new visible copy, and no GPL/AGPL source.

- [ ] **Step 5: Open a pull request against `main`**

GitHub Actions will run the repository's existing viewer tests, unit tests, lint, debug build and release build. Per project workflow, stop after CI is triggered and do not poll the run; continue only when the maintainer returns with the result.

---

CI trigger note: repository Actions were enabled after PR #1 was opened, so this documentation-only commit intentionally retriggers the pull-request workflow without changing runtime behavior.
