# VaultShelf

VaultShelf is an offline Android document viewer, local reading library and encrypted private vault. It deliberately reuses mature open-source subsystems instead of reimplementing them.

## What VaultShelf uses

- **General documents:** Gander's mature local browser and viewer.
- **Local ebooks:** the original Legado / 阅读 3.0 reader source, pinned as a Git submodule.
- **Encrypted vault:** the original DroidFS hidden-volume stack backed by gocryptfs, pinned as a Git submodule.
- **VaultShelf code:** the application shell, bookshelf index, routing and the thin synchronization layers required to connect those mature subsystems.

The Android app does not request the `INTERNET` permission. Document, book and vault content stays on-device unless the user explicitly exports or shares something through Android. DroidFS' original external-storage permissions remain declared so its explicit encrypted-volume backup/migration workflow can work; normal Files, bookshelf and hidden-vault use do not require granting broad storage access.

## Reading library

The bookshelf accepts:

- TXT
- EPUB
- UMD
- PDF
- MOBI
- AZW3
- AZW
- Markdown

TXT, EPUB, UMD, MOBI, AZW3 and AZW are registered with Legado's original local-book model and opened in its original reading UI. Typography, page turning, themes, TOC, search, bookmarks/highlights and other supported reading behavior remain Legado behavior rather than VaultShelf reimplementations.

PDF and Markdown continue to use Gander's mature viewer. Their reading position is synchronized back to the bookshelf.

Pinned Legado commit:

`62003ce732a7e30602754d28996da7f98b9ea296`

## Private vault

VaultShelf routes the Vault entry directly into DroidFS' original vault UI and lifecycle.

The intended VaultShelf vault is DroidFS' **hidden volume** mode:

- encrypted volume lives under the app-private files directory;
- ordinary Android file managers and media scanners do not have normal access to that directory;
- gocryptfs encrypts the stored file contents and names;
- password and biometric unlock use DroidFS' existing implementation;
- DroidFS' original lifecycle performs locking and sensitive temporary-file cleanup;
- encrypted files can still be viewed and managed after the vault is unlocked using DroidFS' original Explorer and viewers;
- a hidden encrypted volume can be copied/exported as an encrypted volume and added again on another device.

Pinned DroidFS commit:

`296713c7627b43db4b8508fb8c6c65acb257aefe`

The project currently builds the DroidFS integration in gocryptfs-only mode; CryFS is disabled through DroidFS' own supported build switch.

## General document viewer

The Gander-derived viewer covers PDF, Word, spreadsheets, slides, images, video/audio, Markdown and text/code paths. Gander-originated code retains its original MIT notice.

## Project structure

- `app` — VaultShelf shell, bookshelf and Gander viewer.
- `legado-upstream` — Gradle adapter that compiles the pinned original Legado reader source.
- `droidfs-upstream` — Gradle adapter that compiles the pinned original DroidFS source.
- `third_party/legado` — pinned upstream Legado Git submodule.
- `third_party/droidfs` — pinned upstream DroidFS Git submodule.

## Build

The development branch intentionally does not run CI for every intermediate commit. A full build/test/lint pass is performed after the current integration work is complete.

Current toolchain target:

- JDK 21
- Android SDK 36

The repository uses submodules, so a local checkout must initialize them before building.

## Privacy

VaultShelf is designed around local processing.

- No Android `INTERNET` permission.
- Gander uses the Storage Access Framework for ordinary files.
- Broad storage access is only part of DroidFS' original external encrypted-volume backup/migration path and is not needed for normal hidden-vault use.
- Books imported to the bookshelf are copied into app-private storage.
- DroidFS hidden vaults live under app-private storage and remain encrypted at rest.
- Export/share only occurs through an explicit user action.

## Licence and attribution

The combined VaultShelf application is distributed under **GNU Affero General Public License v3.0 (AGPL-3.0)** because it incorporates the AGPL-3.0 DroidFS subsystem. See [`LICENSE`](LICENSE).

Upstream components retain their notices and licence obligations:

- DroidFS: https://github.com/hardcore-sushi/DroidFS — AGPL-3.0
- Legado / 阅读 3.0: https://github.com/LegadoTeam/legado — GPL-3.0
- Gander: https://github.com/mokshablr/gander — MIT

Gander-originated source retains its MIT copyright and notice in [`LICENSES/GANDER-MIT.txt`](LICENSES/GANDER-MIT.txt). Additional notices are kept in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and the in-app open-source licence asset.
