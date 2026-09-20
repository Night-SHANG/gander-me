# Third-party notices

VaultShelf is an open-source Android application that combines mature open-source subsystems. The combined application is distributed under GNU Affero General Public License v3.0 (AGPL-3.0) because it directly incorporates the AGPL-3.0 DroidFS subsystem.

This file preserves upstream attribution. Upstream projects retain their own copyright and licence notices.

## DroidFS

Encrypted-vault storage, gocryptfs integration, hidden volumes, biometric password-hash protection, auto-lock lifecycle, encrypted-file Explorer and associated vault UI are provided by the original DroidFS source.

Source: https://github.com/hardcore-sushi/DroidFS

Pinned source commit:

`296713c7627b43db4b8508fb8c6c65acb257aefe`

Licence: GNU Affero General Public License v3.0.

VaultShelf compiles the pinned source through a thin Gradle adapter and uses DroidFS' own supported gocryptfs-only build switch. The encryption implementation itself is not reimplemented by VaultShelf.

## Legado / 阅读 3.0

Local ebook import models, reader UI, pagination, page-turning, reading configuration, TOC, search and other supported reader behavior are provided by the original Legado source.

Source: https://github.com/LegadoTeam/legado

Pinned source commit:

`62003ce732a7e30602754d28996da7f98b9ea296`

Licence: GNU General Public License v3.0.

VaultShelf compiles the pinned source through a thin Gradle adapter and uses a bridge to register app-private local books and synchronize reading progress.

## Gander

VaultShelf started from the Gander Android file viewer.

Source: https://github.com/mokshablr/gander

Original licence: MIT License.

Copyright (c) 2026 Arjun Maniyani.

The original Gander MIT licence text is preserved in `LICENSES/GANDER-MIT.txt`. Gander-originated source retains its original copyright and MIT notice.

## Other bundled components

Document-rendering JavaScript libraries and their notices are listed in the in-app licence asset. Pinned Git submodules also retain their upstream licence files and notices in their source trees.
