# VaultShelf Reader UX + Bookshelf Design

## Goal
Turn the current function-first library/reader milestone into a mature mobile reading experience without changing the successful document-viewer foundation.

## Scope

### Bookshelf
- Default library view becomes a 3-column book-cover grid on phones.
- EPUB uses embedded cover artwork when available.
- TXT or EPUB without artwork uses a deterministic generated text cover based on title + format; generation is local/offline.
- Generated covers use a small fixed palette selected from a stable title hash so a book keeps the same appearance.
- Show title and reading progress beneath each cover; show a thin progress indicator on/under the cover.
- Provide grid/list view toggle.
- Book overflow menu exposes Continue/Start, Rename, Info, Delete from library.
- Delete removes only the app-private imported copy and metadata, never the user's source file.
- Rename changes library metadata only.
- Sort by last opened by default so recently read books surface first.
- Home screen gains a compact Recent Reading shelf; remove oversized explanatory cards from the primary hierarchy.

### TXT reader
- Replace chapter-isolated reading with one continuous whole-book stream.
- In scroll mode, scrolling past a chapter end naturally continues into the next chapter.
- Chapter headings remain visible in the continuous stream and TOC jump remains available.
- Save progress as whole-book character offset and restore near the same paragraph/section.
- Reader chrome is hidden by default; center tap toggles top/bottom chrome.
- Visible chrome contains back/title/progress on top and TOC/appearance controls on bottom.
- No permanent Previous/Next Chapter bars.

### EPUB reader
- Keep Readium 3.1.2 as the content/navigation engine.
- Reader chrome hidden by default; center tap toggles chrome.
- Paged mode: left/right tap zones navigate backward/forward; center toggles chrome.
- Scroll mode: content remains vertically scrollable; chrome behavior stays the same.
- Existing font size, line height, margins, theme and paged/scroll preferences remain.
- Existing Locator persistence remains.

### Visual direction
- Keep Fluent 2 / Windows 11 design language but adapt to mobile reading patterns.
- Reduce borders and large explanatory blocks.
- Use compact headers, clear hierarchy, restrained accent color, rounded surfaces and more whitespace.
- Bottom navigation gains icons while retaining localized labels.
- Reading surfaces prioritize text and content over controls.

## Data model
Add optional mutable library title metadata without breaking existing imported entries. Cover state is derived rather than storing bitmaps in metadata for generated covers. EPUB embedded cover extraction may be cached in app-private storage; generated covers are rendered deterministically from metadata.

## Repository API
- Add `renameBook(id, title)`.
- Keep `deleteBook(id)` and expose it in UI.
- Keep TXT/EPUB progress APIs backward-compatible.

## Testing
- Existing metadata still loads with the new optional fields/defaults.
- Rename persists.
- Delete removes metadata and private file only.
- Generated cover palette selection is deterministic.
- Continuous TXT reading maps saved offsets correctly across chapter boundaries.
- Manifest/permission/i18n invariants remain green.
- Existing viewer tests remain untouched.

## Non-goals
- No MOBI/FB2 support in this milestone.
- No Vault encryption work.
- No annotations/highlights/TTS/full-text search yet.
- No network cover lookup.
- No AI-generated covers.
