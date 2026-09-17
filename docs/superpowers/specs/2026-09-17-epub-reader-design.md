# VaultShelf EPUB Reader Design

## Goal

Add first-class EPUB import and reading to VaultShelf without destabilizing the existing Gander viewer or TXT reader. EPUB files are copied into the same app-private library model as TXT files, then opened with Readium Kotlin Toolkit 3.1.2.

## Constraints

- Keep compileSdk 36, AGP 8.11.2, Kotlin 2.1.0, Gradle 8.13 and the current Compose generation.
- Preserve the zero-user-permission promise and the existing merged-manifest permission gate.
- Preserve the legacy Gander browser/viewer and current TXT reading behavior.
- All visible UI strings must exist in English and Simplified Chinese and satisfy the localization contract.
- Do not copy GPL code. Readium 3.1.2 is BSD-3-Clause and must be recorded in third-party notices.

## Dependency choice

Use only these Readium 3.1.2 modules from Maven Central:

- `org.readium.kotlin-toolkit:readium-shared:3.1.2`
- `org.readium.kotlin-toolkit:readium-streamer:3.1.2`
- `org.readium.kotlin-toolkit:readium-navigator:3.1.2`

Do not add OPDS or LCP in this milestone.

Readium 3.1.2 is chosen instead of the newer toolchain generation so the EPUB milestone does not force a project-wide SDK/AGP/Kotlin migration.

## Library model

`LibraryBook` remains independent of Readium classes. It gains format-neutral progress fields:

- TXT keeps `totalCharacters` and `readingOffset`.
- EPUB stores `readingLocatorJson: String?` containing the serialized Readium `Locator` and `publicationProgression: Float?` for shelf progress display.

Existing TXT metadata must decode unchanged. New JSON properties are optional and default to null/zero when absent.

`progressFraction` dispatches by format: TXT uses character offset; EPUB uses stored publication progression.

## Repository API

Extend the repository with explicit EPUB operations rather than a generic untyped import:

- `importEpub(uri)` copies the file to the app-private library and saves EPUB metadata.
- `bookFile(id)` exposes the private file to the reader layer only.
- `updateEpubProgress(id, locatorJson, progression)` persists resume state.

TXT APIs remain intact.

Import is transactional: copy to a temporary file, rename/copy into place, save metadata, and delete partial files on failure.

## EPUB opening

`EpubReaderActivity` owns Readium initialization for an imported EPUB:

1. Resolve the private EPUB file from the repository.
2. Create `DefaultHttpClient`, `AssetRetriever`, `DefaultPublicationParser`, and `PublicationOpener` using the Readium 3.1.2 API.
3. Retrieve the local file asset and open a `Publication`.
4. Verify the publication conforms to the EPUB profile.
5. Restore `LibraryBook.readingLocatorJson` with `Locator.fromJSON(JSONObject(...))` when valid.
6. Build `EpubNavigatorFactory` and install its fragment factory before fragment restoration.
7. Render `EpubNavigatorFragment` inside a dedicated fragment container.

Readium types do not leak into the persistent repository model.

## Reader UI

The EPUB reader uses a native fragment container for Readium plus a small Fluent-styled control surface owned by VaultShelf. Initial controls:

- back
- title and progress
- table of contents
- smaller/larger font
- light / sepia / dark theme
- paged / scrolling mode

Preferences are expressed as `EpubPreferences` and submitted to the navigator. Reader preferences are persisted locally and restored for the next EPUB.

Search, highlights, annotations and TTS are intentionally deferred.

## Progress and lifecycle

Listen for navigator location changes. On each meaningful location update, serialize the current `Locator` with `toJSON().toString()` and persist its `locations.totalProgression` when available. Save again when leaving the reader.

If the stored locator is malformed, ignore it and open at the beginning instead of failing the book.

If Android restores the Activity after process death before the publication is ready, use the Readium dummy fragment factory pattern and rebuild/finish safely rather than crashing fragment restoration.

## Shelf integration

The library file picker accepts both `text/plain` and `application/epub+zip`. Import dispatch uses MIME type and file extension fallback.

Book cards display the actual `TXT` or `EPUB` badge. Opening dispatches to `TxtReaderActivity` or `EpubReaderActivity` by `BookFormat`.

## Permissions and networking

This milestone supports local imported EPUB only. It does not expose remote URL import. Readium's demo applications request network permissions, but VaultShelf must not add user-visible/network permissions. The existing merged-manifest permission gate remains authoritative; if a transitive manifest contribution appears, remove it explicitly when local EPUB does not need it.

## Error handling

- Unsupported/corrupt EPUB: show a localized load failure state and allow returning to the shelf.
- Missing private file: same localized failure path.
- Import failure: preserve the existing shelf import-error surface.
- Invalid saved locator/preferences: fall back to safe defaults.

No partially imported library entry should remain after an import failure.

## Licensing

Add `THIRD_PARTY_NOTICES.md` with Readium Kotlin Toolkit 3.1.2, project URL, BSD-3-Clause attribution and license text/notice requirements. No Readium source code needs to be copied into VaultShelf; integration uses published artifacts and documented APIs.

## Verification

Add or extend tests for:

- backward-compatible decoding of old TXT metadata
- EPUB metadata round-trip and progress calculation
- EPUB import creates a private `.epub` copy
- format dispatch contract
- `EpubReaderActivity` is declared and non-exported
- EN/zh-CN localization parity

CI remains the final gate: unit tests, Lint, debug/release APK, permission check and viewer tests must all pass.