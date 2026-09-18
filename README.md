# VaultShelf

VaultShelf is an offline Android document viewer and private reading library built on the mature Gander file-viewer foundation and adapted reading technology from Legado / 阅读 3.0.

The project keeps two jobs separate:

- **Documents:** PDF, Word, Excel, PowerPoint, Markdown, images, audio/video and other files continue to use Gander's proven local viewer path.
- **Books:** TXT and EPUB can be imported into an app-private library and opened in a dedicated novel-reading experience.

No document or book content is uploaded by VaultShelf. The Android app does not request the `INTERNET` permission.

## Current reading features

- App-private TXT and EPUB imports.
- Bookshelf with reading progress and recent-reading state.
- TXT chapter detection and resume position.
- EPUB spine/TOC parsing and embedded image rendering.
- One shared TXT/EPUB reader surface based on the adapted Legado `ReadView` model.
- Cover, slide, simulation/curl, continuous-scroll and no-animation page modes.
- Center-tap reading controls with automatic hiding.
- Font size, line spacing, page margins and light/sepia/dark reading themes.
- Chinese-oriented paragraph indentation, layout and full justification.
- Edge-to-edge Android layout with safe handling of display cutouts and gesture insets.
- Complete Simplified Chinese UI alongside English through the project's i18n resources.

## Document formats

The Gander-derived viewer currently covers:

| Category | Formats / renderer |
| --- | --- |
| PDF | pdf.js, offline in a sandboxed WebView |
| Word | `.docx` through docx-preview |
| Spreadsheets | `.xlsx`, `.xls`, `.xlsm`, `.xlsb`, `.csv`, `.ods` through SheetJS |
| Slides | `.pptx` through PPTXjs |
| Images | common raster formats plus GIF/SVG/AVIF/ICO paths |
| Video / audio | Media3 |
| Markdown | marked + DOMPurify |
| Text / code | local text viewer |

Legacy binary `.doc` and `.ppt` are not currently supported.

## Architecture

`VaultShelfActivity` is the Compose application shell. The mature Gander `MainActivity` and `ViewerActivity` remain responsible for general file browsing and document rendering.

Novel reading is isolated in `:legado-reader`. TXT and EPUB are converted into the same `TextChapter` / `TextPage` model and rendered by the same `ReadView`, so page-turning, typography, themes and interaction behavior do not diverge by book format.

EPUB parsing uses Legado's standalone `modules:book` parser pinned to upstream commit:

`62003ce732a7e30602754d28996da7f98b9ea296`

## Build

Requirements:

- JDK 21 for the current CI/test toolchain.
- Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release builds can also be assembled with:

```sh
./gradlew assembleRelease
```

The project includes CI checks for Android unit tests, lint, viewer tests, debug/release APK builds and the merged-manifest permission invariant.

## Privacy

VaultShelf uses Android's Storage Access Framework and app-private storage. It does not request broad storage access and does not declare the Android `INTERNET` permission. Imported library books are copied into VaultShelf's private storage; deleting a library entry deletes that private copy, not the user's original source file.

The ordinary library is currently app-private but **not yet an encrypted vault**. Encryption/PIN/biometric protection is a separate planned Vault module and should not be confused with the current library storage.

## Licence and attribution

The combined VaultShelf application is distributed under **GNU GPL v3.0** because the novel-reading core incorporates and adapts GPL-3.0 code from Legado / 阅读 3.0. See [`LICENSE`](LICENSE).

VaultShelf started from the MIT-licensed Gander project by Arjun Maniyani. Gander-originated source retains its original copyright and MIT notice; the original MIT text is preserved in [`LICENSES/GANDER-MIT.txt`](LICENSES/GANDER-MIT.txt).

Legado source: https://github.com/LegadoTeam/legado

Gander source: https://github.com/mokshablr/gander

Additional attribution and bundled viewer-library licences are documented in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and are also shipped inside the APK.
