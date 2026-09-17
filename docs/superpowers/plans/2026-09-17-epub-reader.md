# EPUB Reader Implementation Plan

## 1. Lock the storage contract with tests

Modify/add tests under `app/src/test/java/com/arjun/gander/` before implementation:

- old TXT JSON metadata still decodes with new nullable EPUB fields absent
- EPUB progress uses stored publication progression
- EPUB metadata survives repository save/load
- imported EPUB is copied to the private library directory
- manifest declares `EpubReaderActivity` with `exported=false`

Run through CI after the complete milestone; local full Gradle execution is not assumed available in this connector-only workflow.

## 2. Generalize the library model without breaking TXT

Modify:

- `app/src/main/java/com/arjun/gander/library/LibraryBook.kt`
- `app/src/main/java/com/arjun/gander/library/LibraryRepository.kt`
- `app/src/main/java/com/arjun/gander/library/LocalLibraryRepository.kt`

Add `readingLocatorJson` and `publicationProgression`, EPUB import, private file access and EPUB progress update. Keep JSON decode backwards-compatible.

## 3. Add Readium dependencies and licensing

Modify `app/build.gradle.kts` with Readium 3.1.2 shared/streamer/navigator only.

Add `THIRD_PARTY_NOTICES.md` for Readium BSD-3-Clause.

Do not add OPDS/LCP or remote URL import.

## 4. Add EPUB publication session layer

Create a focused EPUB package under `app/src/main/java/com/arjun/gander/epub/`.

Responsibilities:

- open an app-private EPUB using Readium 3.1.2 `AssetRetriever` / `PublicationOpener`
- validate EPUB profile
- restore a serialized Locator safely
- expose publication, navigator factory and initial locator to the Activity/Fragment layer
- close publication/session resources with the reader lifecycle

Keep persistent storage independent of Readium types.

## 5. Add EPUB reader Activity/Fragment

Create:

- `app/src/main/java/com/arjun/gander/EpubReaderActivity.kt`
- `app/src/main/java/com/arjun/gander/epub/EpubReaderFragment.kt`
- minimal layout resources for a Readium `FragmentContainerView` plus VaultShelf controls as needed

Implement:

- open / error / back
- resume locator
- TOC navigation
- progress persistence from navigator location changes
- font size
- light/sepia/dark theme
- paged/scroll toggle
- safe fragment restoration using Readium dummy factory behavior

## 6. Integrate the shelf

Modify `LibraryScreen.kt`:

- picker accepts TXT and EPUB
- dispatch import by MIME/extension
- display format-specific badge
- dispatch reader Activity by `BookFormat`

Keep existing TXT behavior unchanged.

## 7. Localization and manifest

Modify EN and zh-CN `strings.xml` with every new visible label/error/control.

Modify `AndroidManifest.xml` to declare `EpubReaderActivity` private/non-exported. If Readium contributes permissions, explicitly remove only those unnecessary for local EPUB while preserving the zero-permission build invariant.

## 8. Static review and CI

Before PR:

- inspect all changed files for GPL/copied-source contamination
- verify no Readium type leaked into stored model API
- verify no hard-coded visible strings
- verify format dispatch and failure cleanup
- verify zero-permission design

Open PR, confirm exactly one GitHub Actions run is registered, then stop polling. If CI later fails, inspect all errors in the failing step and fix them as a group before retriggering.