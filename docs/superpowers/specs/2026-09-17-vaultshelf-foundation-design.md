# VaultShelf Foundation Design

## Goal

Turn the existing Gander fork into the foundation for VaultShelf without discarding Gander's mature document viewer.

## Product direction

VaultShelf is an Android document, private library, and novel reading application. The first foundation milestone establishes the application shell only. Novel engines, encrypted vault storage, and document editing are intentionally deferred to later milestones.

## Non-negotiable constraints

- Keep the existing `MainActivity` file browser and `ViewerActivity` document rendering path working.
- New user-facing UI uses Jetpack Compose.
- All new user-facing text must come from Android string resources. No hard-coded UI copy.
- `values/strings.xml` remains the fallback locale and `values-zh-rCN/strings.xml` must provide complete Simplified Chinese coverage for every translatable string.
- UI direction is Windows 11 / Fluent 2 inspired: layered neutral surfaces, restrained blue accent, 12-16 dp rounded corners, thin borders, low-elevation cards, compact typography, and clear navigation.
- Preserve the project's zero-permission/offline document-viewing model.
- Do not introduce GPL/AGPL source code.
- Do not integrate Readium, a novel reader, or encrypted vault storage in this milestone.

## Architecture

### Activity split

`VaultShelfActivity` becomes the launcher and owns the new Compose application shell. The existing `MainActivity` remains the legacy/mature file browser and is launched from the Files destination. `ViewerActivity` remains the external `VIEW`/`SEND` handler and document renderer.

This deliberately avoids rewriting the heavily tested Gander file browser while allowing new VaultShelf modules to be built in Compose.

### Navigation shell

The initial shell exposes five destinations:

- Home
- Library
- Files
- Vault
- Settings

Only Files is fully functional in this milestone and opens the existing Gander file browser. Home contains quick entry cards and communicates the product structure. Library/Vault/Settings are stable navigation destinations with localized foundation-state copy so later milestones can replace their content without changing the shell contract.

### Compose boundary

Compose is enabled with the Kotlin Compose compiler plugin matching the repository's Kotlin version. The project pins the Compose BOM to `2026.06.00`, which stays on the Compose 1.11 generation and avoids forcing the existing `compileSdk 36` / AGP 8.11 project onto the Compose 1.12 API-37/AGP-9 toolchain during this foundation change.

### Design system

A small local design system under `ui/theme` owns:

- light/dark Fluent-inspired color schemes;
- typography;
- corner radii and shared surface treatment;
- `VaultShelfTheme`.

Feature screens do not define their own product colors.

### Internationalization contract

Every translatable key in `values/strings.xml` must exist in `values-zh-rCN/strings.xml`. A unit test parses both XML files and fails CI when a key is missing. `translatable="false"` values such as URLs are excluded from parity checks.

The first milestone also translates the pre-existing Gander strings so opening the legacy file browser or viewer from VaultShelf does not drop back to English on a Simplified Chinese device.

## First milestone deliverables

1. Compose enabled without changing application ID or package namespace.
2. New `VaultShelfActivity` launcher.
3. Fluent-inspired light and dark Compose theme.
4. Home/navigation shell with five destinations.
5. Files destination launches existing `MainActivity`.
6. Complete Simplified Chinese translation for all translatable app strings.
7. Localization parity unit test.
8. Existing `MainActivity` and `ViewerActivity` behavior preserved.

## Deferred work

- Readium / EPUB integration.
- TXT novel pagination and reading controls.
- Library database and metadata model.
- Encrypted vault, biometric/PIN unlock, SQLCipher/Keystore work.
- Markdown/TXT editor integration.
- Application/package rename and migration strategy.
- Backup/export format.
