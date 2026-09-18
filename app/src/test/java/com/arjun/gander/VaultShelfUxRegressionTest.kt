package com.arjun.gander

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class VaultShelfUxRegressionTest {

    private val repo = File("..")

    @Test
    fun bookshelfKeepsLegadoStyleTwoThroughSixColumnChoices() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()

        assertThat(source).contains("DEFAULT_GRID_COLUMNS = 3")
        assertThat(source).contains("(2..6).forEach")
        assertThat(source).contains("GridCells.Fixed(gridColumns)")
        assertThat(source).doesNotContain("GridCells.Adaptive")
    }

    @Test
    fun filesBottomDestinationOpensTheExistingBrowserDirectly() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()

        assertThat(source).contains("destination == VaultShelfDestination.FILES")
        assertThat(source).contains("onOpenFiles()")
    }

    @Test
    fun originalLegadoReaderIsPinnedAndUsedDirectly() {
        val gitmodules = File(repo, ".gitmodules").readText()
        val appBuild = File(repo, "app/build.gradle.kts").readText()
        val upstreamBuild = File(repo, "legado-upstream/build.gradle.kts").readText()
        val router = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryReaderRouter.kt",
        ).readText()
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        assertThat(gitmodules).contains("third_party/legado")
        assertThat(gitmodules).contains("https://github.com/LegadoTeam/legado.git")
        assertThat(appBuild).contains("implementation(project(\":legado-upstream\"))")
        assertThat(appBuild).doesNotContain("implementation(project(\":legado-reader\"))")
        assertThat(upstreamBuild).contains("../third_party/legado/app/src/main/java")
        assertThat(upstreamBuild).contains("../third_party/legado/app/src/main/res")
        assertThat(bridge).contains("ReadBookActivity")
        assertThat(bridge).contains("LocalBook.previewImportFile")
        assertThat(router).contains("LegadoReaderBridge.readerIntent")
        assertThat(router).contains("syncLegadoReaderProgress")
    }

    @Test
    fun vaultUsesPinnedDroidFsHiddenVolumeSubsystem() {
        val gitmodules = File(repo, ".gitmodules").readText()
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val droidFsBuild = File(repo, "droidfs-upstream/build.gradle.kts").readText()

        assertThat(gitmodules).contains("third_party/droidfs")
        assertThat(gitmodules).contains("https://github.com/hardcore-sushi/DroidFS.git")
        assertThat(activity).contains("DroidFsMainActivity")
        assertThat(shell).contains("VaultShelfDestination.VAULT -> onOpenVault()")
        assertThat(droidFsBuild).contains("../third_party/droidfs/app/src/main/java")
        assertThat(droidFsBuild).contains("\"CRYFS_DISABLED\", \"true\"")
        assertThat(droidFsBuild).contains("\"GOCRYPTFS_DISABLED\", \"false\"")
    }

    @Test
    fun settingsRoutesToMatureSubsystems() {
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val main = File(
            repo,
            "app/src/main/java/com/arjun/gander/MainActivity.kt",
        ).readText()

        assertThat(shell).contains("SettingsScreen(")
        assertThat(shell).contains("onOpenVaultSettings")
        assertThat(shell).contains("onOpenAbout")
        assertThat(shell).doesNotContain("FoundationScreen")
        assertThat(activity).contains("DroidFsSettingsActivity")
        assertThat(main).contains("EXTRA_SHOW_ABOUT")
    }

    @Test
    fun projectLicenceMatchesDroidFsIntegration() {
        val licence = File(repo, "LICENSE").readText()
        val notices = File(repo, "THIRD_PARTY_NOTICES.md").readText()
        val inApp = File(repo, "app/src/main/assets/licences.md").readText()

        assertThat(licence).contains("GNU AFFERO GENERAL PUBLIC LICENSE")
        assertThat(notices).contains("DroidFS")
        assertThat(notices).contains("Legado")
        assertThat(inApp).contains("AGPL-3.0")
    }

    @Test
    fun libraryKeepsLegadoLocalFormatsPlusMarkdown() {
        val bookModel = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryBook.kt",
        ).readText()
        val library = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()
        val router = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryReaderRouter.kt",
        ).readText()

        listOf("TXT", "EPUB", "UMD", "PDF", "MOBI", "AZW3", "AZW", "MARKDOWN")
            .forEach { format ->
                assertThat(bookModel).contains("BookFormat.$format")
                assertThat(library).contains("BookFormat.$format")
            }

        listOf("TXT", "EPUB", "UMD", "MOBI", "AZW3", "AZW").forEach { format ->
            assertThat(router).contains("BookFormat.$format")
        }

        assertThat(library).contains("\"md\", \"markdown\"")
        assertThat(library).contains("\"mobi\"")
        assertThat(library).contains("\"azw3\"")
        assertThat(library).contains("\"azw\"")
        assertThat(library).contains("\"umd\"")
        assertThat(library).contains("\"pdf\"")
    }

    @Test
    fun viewerProgressStaysConnectedToShelf() {
        val viewer = File(
            repo,
            "app/src/main/java/com/arjun/gander/ViewerActivity.kt",
        ).readText()
        val markdown = File(
            repo,
            "app/src/main/assets/viewer/md.html",
        ).readText()
        val library = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()

        assertThat(viewer).contains("ScrollPositions.fraction")
        assertThat(viewer).contains("ScrollPositions.save")
        assertThat(viewer).contains("Positions.page(this, it)")
        assertThat(viewer).contains("EXTRA_LIBRARY_PROGRESS")
        assertThat(markdown).contains("resumeScroll")
        assertThat(library).contains("updateViewerProgress")
        assertThat(shell).contains("updateViewerProgress")
    }

    @Test
    fun vaultReadersKeepProgressWithoutPersistingPlaintext() {
        val vaultBridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()
        val positions = File(
            repo,
            "app/src/main/java/com/arjun/gander/BookReadingPositions.kt",
        ).readText()
        val legadoBridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()
        val droidFsProgress = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfProgressStore.kt",
        ).readText()

        assertThat(vaultBridge).contains("BookReadingPositions.get")
        assertThat(vaultBridge).contains("BookReadingPositions.save")
        assertThat(vaultBridge).contains("cleanupTransientBookSession")
        assertThat(legadoBridge).contains("restoreTransientReadingPosition")
        assertThat(legadoBridge).contains("transientReadingPosition")
        assertThat(positions).contains("noBackupFilesDir")
        assertThat(positions).doesNotContain("DISPLAY_NAME")
        assertThat(droidFsProgress).contains("MessageDigest.getInstance(\"SHA-256\")")
        assertThat(droidFsProgress).contains("VolumeManagerApp")
    }

    @Test
    fun vaultFormatsUseTheMatureViewerForEachDomain() {
        val router = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfFileRouter.kt",
        ).readText()
        val bridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()

        assertThat(router).contains("\"txt\", \"epub\", \"umd\", \"mobi\", \"azw3\", \"azw\"")
        assertThat(router).contains("\"jpg\", \"jpeg\", \"png\", \"webp\"")
        assertThat(router).contains("\"gif\", \"svg\", \"avif\", \"ico\"")
        assertThat(router).contains("\"pdf\"")
        assertThat(router).contains("\"md\", \"markdown\"")
        assertThat(bridge).contains("\"txt\", \"epub\", \"umd\", \"mobi\", \"azw3\", \"azw\" -> openWithLegado")
        assertThat(bridge).contains("\"jpg\", \"jpeg\", \"png\", \"webp\"")
        assertThat(bridge).contains("\"pdf\", \"docx\"")
        assertThat(bridge).contains("openWithGander")
    }

    @Test
    fun normalFilesReuseLegadoAndDroidFsInsteadOfDuplicatingViewers() {
        val dispatcher = File(
            repo,
            "app/src/main/java/com/arjun/gander/FileDispatchActivity.kt",
        ).readText()
        val mediaRouter = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfExternalMediaRouter.kt",
        ).readText()
        val main = File(
            repo,
            "app/src/main/java/com/arjun/gander/MainActivity.kt",
        ).readText()

        assertThat(dispatcher).contains("EBOOK_EXTENSIONS")
        assertThat(dispatcher).contains("LegadoReaderBridge.createTransientBookSession")
        assertThat(dispatcher).contains("VaultShelfExternalMediaRouter.supports")
        assertThat(mediaRouter).contains("AudioPlayer::class.java")
        assertThat(mediaRouter).contains("VideoPlayer::class.java")
        assertThat(main).contains("FileDispatchActivity::class.java")
    }

    @Test
    fun droidFsPatchKeepsHiddenVaultDefaultAndMigrationAvailable() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()
        val manifest = File(
            repo,
            "droidfs-upstream/src/main/AndroidManifest.xml",
        ).readText()

        assertThat(patch).contains("binding.switchHiddenVolume.isChecked = true")
        assertThat(patch).contains("VaultShelfExternalMediaRouter")
        assertThat(patch).contains("VaultShelfProgressStore")
        assertThat(manifest).contains("android.permission.MANAGE_EXTERNAL_STORAGE")
        assertThat(manifest).contains("android.permission.WRITE_EXTERNAL_STORAGE")
    }

    @Test
    fun networkPermissionRemainsStripped() {
        val manifest = File(repo, "app/src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android.permission.INTERNET")
        assertThat(manifest).contains("tools:node=\"remove\"")
    }

    @Test
    fun portableVaultBackupKeepsCiphertextAndPerVolumeOpaqueMetadata() {
        val manager = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultBackupManager.kt",
        ).readText()
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultBackupActivity.kt",
        ).readText()
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val progress = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfProgressStore.kt",
        ).readText()

        assertThat(manager).contains("EncryptedVolume.getVolumeType")
        assertThat(manager).contains("volume.uuid")
        assertThat(manager).contains("encryptedHash = null")
        assertThat(manager).contains("iv = null")
        assertThat(manager).contains("BookReadingPositions.exportOpaque(appContext, prefix)")
        assertThat(manager).contains("VaultShelfProgressStore.exportOpaque(appContext, volume.uuid)")
        assertThat(manager).doesNotContain("openFileReadMode")
        assertThat(activity).contains("CreateDocument(\"application/zip\")")
        assertThat(activity).contains("OpenDocument()")
        assertThat(shell).contains("onOpenVaultBackup")
        assertThat(progress).contains("fun volumePrefix")
    }

    @Test
    fun adaptedReaderActivitiesAndPanelsAreGone() {
        listOf(
            "app/src/main/java/com/arjun/gander/TxtReaderActivity.kt",
            "app/src/main/java/com/arjun/gander/EpubReaderActivity.kt",
            "app/src/main/java/com/arjun/gander/UmdReaderActivity.kt",
            "app/src/main/java/com/arjun/gander/MobiReaderActivity.kt",
            "app/src/main/java/com/arjun/gander/ui/reader/ReaderPanels.kt",
        ).forEach { relative ->
            assertThat(File(repo, relative).exists()).isFalse()
        }

        val manifest = File(repo, "app/src/main/AndroidManifest.xml").readText()
        assertThat(manifest).doesNotContain(".TxtReaderActivity")
        assertThat(manifest).doesNotContain(".EpubReaderActivity")
        assertThat(manifest).doesNotContain(".UmdReaderActivity")
        assertThat(manifest).doesNotContain(".MobiReaderActivity")
    }
}
