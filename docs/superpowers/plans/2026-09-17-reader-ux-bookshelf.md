# Reader UX + Bookshelf Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a mature bookshelf and immersive TXT/EPUB reading experience while preserving the existing Gander viewer and offline/zero-dangerous-permission posture.

**Architecture:** Keep the current app-private library repository and Readium EPUB engine. Add small focused helpers for cover derivation and continuous TXT reading state, then rebuild the Compose bookshelf and reader chrome around those stable data APIs.

**Tech Stack:** Kotlin, Jetpack Compose, AppCompat, Readium Kotlin Toolkit 3.1.2, SharedPreferences JSON metadata, Robolectric/JUnit, existing GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-17-reader-ux-bookshelf-design.md`

## Global Constraints
- Keep complete Simplified Chinese and English localization; no user-visible hardcoded strings.
- Keep existing external ViewerActivity behavior unchanged.
- No network cover lookup and no new dangerous/user-granted Android permissions.
- Readium stays pinned at 3.1.2.
- Delete only app-private imported copies; never modify source documents selected by the user.

---

### Task 1: Library metadata management and deterministic covers

**Files:**
- Modify: `app/src/main/java/com/arjun/gander/library/LibraryBook.kt`
- Modify: `app/src/main/java/com/arjun/gander/library/LibraryRepository.kt`
- Modify: `app/src/main/java/com/arjun/gander/library/LocalLibraryRepository.kt`
- Create: `app/src/main/java/com/arjun/gander/library/BookCoverStyle.kt`
- Test: `app/src/test/java/com/arjun/gander/library/LibraryManagementTest.kt`

**Interfaces:**
- Produces: `suspend fun renameBook(id: String, title: String): LibraryBook?`
- Produces: deterministic `BookCoverStyle.from(book)` with palette index/format label/title.

- [ ] Write tests proving old metadata loads, rename persists, delete removes only app-private copy, and cover style is deterministic.
- [ ] Run focused tests and confirm RED.
- [ ] Implement metadata migration-safe rename and cover derivation.
- [ ] Run focused tests and confirm GREEN.

### Task 2: Bookshelf UI and home recent-reading shelf

**Files:**
- Modify: `app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: repository `renameBook`, `deleteBook`, `listBooks` and `BookCoverStyle`.
- Produces: grid/list toggle, three-column generated-cover bookshelf, overflow actions, compact recent-reading shelf.

- [ ] Add UI state tests/contract tests where practical for localized keys and management actions.
- [ ] Replace large-card library with grid/list bookshelf and accessible generated covers.
- [ ] Add rename/delete confirmation flows and refresh state after mutations.
- [ ] Rework Home into compact recent-reading + quick actions; keep Files/Vault/Settings routes.
- [ ] Run unit/localization tests.

### Task 3: Continuous immersive TXT reader

**Files:**
- Create: `app/src/main/java/com/arjun/gander/library/TxtReadingFlow.kt`
- Modify: `app/src/main/java/com/arjun/gander/TxtReaderActivity.kt`
- Test: `app/src/test/java/com/arjun/gander/library/TxtReadingFlowTest.kt`

**Interfaces:**
- Produces: flattened reading items containing chapter headings and paragraphs with stable whole-book offsets.
- Consumes: existing `TxtChapterParser` and whole-book `readingOffset` persistence.

- [ ] Write RED tests for chapter-boundary flattening and offset restore across chapters.
- [ ] Implement `TxtReadingFlow`.
- [ ] Rewrite TXT UI to one continuous LazyColumn spanning the whole book.
- [ ] Default reader chrome hidden; center tap toggles chrome; remove permanent chapter bars.
- [ ] Keep TOC and font-size controls inside temporary chrome.
- [ ] Run TXT parser/flow/progress tests.

### Task 4: Immersive EPUB chrome and tap navigation

**Files:**
- Modify: `app/src/main/java/com/arjun/gander/EpubReaderActivity.kt`
- Modify: `app/src/main/res/layout/activity_epub_reader.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: existing `EpubNavigatorFragment`, preferences and Locator persistence.
- Produces: overlay chrome hidden by default; center toggle; edge page navigation in paged mode.

- [ ] Preserve current Readium session/open/progress code.
- [ ] Convert controls to overlay containers over the navigator instead of permanent stacked rows.
- [ ] Add content/tap-zone handling: left previous, center toggle chrome, right next in paged mode; scroll mode center toggle only.
- [ ] Keep appearance controls and TOC in bottom chrome.
- [ ] Verify controls auto-hide after inactivity and do not block scrolling.

### Task 5: Visual polish, localization, regression contracts

**Files:**
- Modify: `app/src/main/java/com/arjun/gander/ui/theme/*` only as needed
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify/Add tests under `app/src/test/java/com/arjun/gander/`

**Interfaces:**
- Produces: consistent Fluent mobile hierarchy, navigation icons, complete bilingual strings.

- [ ] Reduce unnecessary borders/shadows and normalize spacing/shape hierarchy.
- [ ] Add simple vector icons for bottom navigation/actions if missing; keep them local and license-safe.
- [ ] Run localization contract and manifest/permission tests.
- [ ] Static review for hardcoded strings, destructive actions, large-text clipping and accessibility labels.

### Task 6: Verification and CI handoff

**Files:** none unless verification exposes defects.

- [ ] Review branch diff against the approved spec; remove accidental scope expansion.
- [ ] Run/trigger full unit tests, lint, debug/release APK and Viewer tests via the existing workflow.
- [ ] Create PR from `feature/reader-ux-bookshelf` to `main`.
- [ ] Confirm GitHub Actions run is registered once, then stop polling per project workflow.
