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

        assertThat(source).contains("VaultShelfDestination.FILES -> onOpenFiles()")
        assertThat(source).contains("onOpenFiles()")
    }

    @Test
    fun transientLegadoSessionsUseDatabaseMarkerInsteadOfPlainUriRegistry() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        assertThat(bridge).contains("TRANSIENT_ORIGIN")
        assertThat(bridge).contains("preview.copy(origin = TRANSIENT_ORIGIN)")
        assertThat(bridge).contains("appDb.bookDao.all")
        assertThat(bridge).contains("it.origin == TRANSIENT_ORIGIN")
        assertThat(bridge).doesNotContain("putStringSet(TRANSIENT_URLS")
        assertThat(bridge).doesNotContain("fun rememberTransientUrl")
    }

    @Test
    fun transientLegadoHistorySnapshotIsEncryptedAtRest() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        assertThat(bridge).contains("AndroidKeyStore")
        assertThat(bridge).contains("AES/GCM/NoPadding")
        assertThat(bridge).contains("GCMParameterSpec")
        assertThat(bridge).contains("cipher.updateAAD(bookUrl.toByteArray")
        assertThat(bridge).contains("\"\$digest.bin\"")
        assertThat(bridge).doesNotContain("output.write(GSON.toJson(snapshot).toByteArray")
    }

    @Test
    fun transientVaultBookDeletesMemoAndRuleBigData() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(bridge).contains("bookMemoDao.delete(bookUrl)")
        assertThat(bridge).contains("RuleBigDataHelp.clearBook(bookUrl)")
        assertThat(patch).contains("delete from book_memos where bookUrl = :bookUrl")
        assertThat(patch).contains("fun clearBook(bookUrl: String)")
        assertThat(patch).contains("deleteRootDir = true")
    }

    @Test
    fun transientVaultEpubDeletesItsFullPlaintextContentUriCache() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(bridge).contains("BookHelp.clearEpubContentUriCache(book)")
        assertThat(patch).contains("clearEpubContentUriCache")
        assertThat(patch).contains(".temporary_provider")
        assertThat(patch).contains("MD5Utils.md5Encode16(book.bookUrl)")
    }

    @Test
    fun transientVaultBooksCannotExportOrEditTheirTemporaryIdentity() {
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(patch).contains(".temporary_provider")
        assertThat(patch).contains("menu_share_it")
        assertThat(patch).contains("menu_copy_book_url")
        assertThat(patch).contains("menu_upload")
    }

    @Test
    fun localBookEntryWaitsForTxtTocRulesWithoutPrematureVersionMarking() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        assertThat(bridge).contains("private fun ensureLocalTxtTocRules()")
        assertThat(bridge).contains("runBlocking(Dispatchers.IO)")
        assertThat(bridge).contains("appDb.txtTocRuleDao.count")
        assertThat(bridge).contains("DefaultData.importDefaultTocRules()")
        assertThat(bridge).contains(".putInt(TXT_TOC_RULE_VERSION_KEY, TXT_TOC_RULE_VERSION)")
        assertThat(bridge).doesNotContain("LocalConfig.needUpTxtTocRule")
    }

    @Test
    fun transientStartupCleanupCompletesBeforeAReplacementSessionIsImported() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()
        val dispatcher = File(
            repo,
            "app/src/main/java/com/arjun/gander/FileDispatchActivity.kt",
        ).readText()

        val transientSession = bridge
            .substringAfter("fun createTransientBookSession(")
            .substringBefore("fun restoreTransientReadingPosition")
        val persistentSession = bridge
            .substringAfter("fun ensurePersistentUriBook(")
            .substringBefore("fun createTransientBookSession(")

        val cleanupBarrier = transientSession.indexOf("ensureTransientStartupCleanup(context)")
        val importPreview = transientSession.indexOf("val preview = LocalBook.previewImportFile(uri)")
        assertThat(cleanupBarrier).isAtLeast(0)
        assertThat(importPreview).isGreaterThan(cleanupBarrier)
        assertThat(persistentSession).doesNotContain("ensureTransientStartupCleanup(context)")
        assertThat(dispatcher).doesNotContain("ensurePersistentUriBook")
        assertThat(dispatcher).contains("createTransientBookSession")
    }

    @Test
    fun legadoBridgeInitializesSplittiesBeforeReadingAppConfig() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        val inject = bridge.indexOf("appContext.injectAsAppCtx()")
        val firstAppConfig = bridge.indexOf("AppConfig.themeMode")
        assertThat(inject).isAtLeast(0)
        assertThat(firstAppConfig).isGreaterThan(inject)
    }

    @Test
    fun localLegadoManifestKeepsReaderReachableActivitiesOnly() {
        val manifest = File(repo, "legado-upstream/src/main/AndroidManifest.xml").readText()

        listOf(
            "ReadBookActivity",
            "BookInfoActivity",
            "BookInfoEditActivity",
            "ReplaceRuleActivity",
            "ReplaceEditActivity",
            "CodeEditActivity",
            "HighlightRuleActivity",
            "TxtTocRuleActivity",
            "OpenUrlConfirmActivity",
        ).forEach { activity ->
            assertThat(manifest).contains(activity)
        }

        assertThat(manifest).doesNotContain("SourceLoginActivity")
        assertThat(manifest).doesNotContain("WebViewActivity")
        assertThat(manifest).doesNotContain("BookSourceEditActivity")
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
        assertThat(upstreamBuild).contains("../third_party/legado/modules/book/src/main/resources")
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
        assertThat(shell).contains("onOpenReaderSettings")
        assertThat(shell).contains("onClick = onOpenReaderSettings")
        assertThat(activity).contains("LegadoReaderBridge.readerSettingsIntent")
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
        assertThat(legadoBridge).contains("TransientMetadataSnapshot")
        assertThat(legadoBridge).contains("readRecordDao.getRecords")
        assertThat(legadoBridge).contains("restoreTransientMetadataSnapshot")
        assertThat(positions).contains("noBackupFilesDir")
        assertThat(positions).doesNotContain("DISPLAY_NAME")
        assertThat(droidFsProgress).contains("MessageDigest.getInstance(\"SHA-256\")")
        assertThat(droidFsProgress).contains("VolumeManagerApp")
    }

    @Test
    fun vaultCleanupOutlivesBridgeActivityDestruction() {
        val bridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()

        assertThat(bridge).contains("CoroutineScope(SupervisorJob() + Dispatchers.IO)")
        assertThat(bridge).contains("startCleanup(finishWhenDone = false)")
        assertThat(bridge).doesNotContain("Thread {")
        assertThat(bridge).doesNotContain("lifecycleScope.launch {\n            if (bookUrl != null)")
    }

    @Test
    fun singleTaskLegadoReadersDoNotUseActivityResultForCleanup() {
        val dispatcher = File(
            repo,
            "app/src/main/java/com/arjun/gander/FileDispatchActivity.kt",
        ).readText()
        val vaultBridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()

        assertThat(dispatcher).doesNotContain("StartActivityForResult")
        assertThat(dispatcher).contains("transientReaderActive")
        assertThat(dispatcher).contains("override fun onResume()")
        assertThat(vaultBridge).doesNotContain("StartActivityForResult")
        assertThat(vaultBridge).contains("childActive")
        assertThat(vaultBridge).contains("override fun onResume()")
    }

    @Test
    fun vaultLockStopsTransientReadAloudAndHidesItsLockscreenNotification() {
        val guard = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultSessionGuard.kt",
        ).readText()
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(guard).contains("ReadAloud.stop(context)")
        assertThat(guard).contains(".temporary_provider")
        assertThat(patch).contains("NotificationCompat.VISIBILITY_SECRET")
        assertThat(patch).contains(".temporary_provider")
    }

    @Test
    fun vaultLockGuardCoversNestedLegadoActivitiesInTheSameTask() {
        val guard = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultSessionGuard.kt",
        ).readText()

        assertThat(guard).contains("val taskIds: MutableSet<Int>")
        assertThat(guard).contains("LEGADO_PACKAGE_PREFIX")
        assertThat(guard).contains("activity.javaClass.name.startsWith")
        assertThat(guard).contains("current.activities.entries.toList().forEach")
        assertThat(guard).contains("activity.finish()")
        assertThat(guard).contains("WindowManager.LayoutParams.FLAG_SECURE")
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
        assertThat(dispatcher).contains("Intent.EXTRA_TEXT")
        assertThat(dispatcher).contains("setClass(this, ViewerActivity::class.java)")
        assertThat(mediaRouter).contains("AudioPlayer::class.java")
        assertThat(mediaRouter).contains("VideoPlayer::class.java")
        assertThat(mediaRouter).contains("EXTRA_PROGRESS_KEY")
        assertThat(dispatcher).contains("Positions.keyFor")
        assertThat(dispatcher).contains("transientReaderActive = true")
        assertThat(main).contains("FileDispatchActivity::class.java")
    }

    @Test
    fun droidFsUnreferencedBackupRulesAreNamespacedAwayFromLegado() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val manifest = File(repo, "droidfs-upstream/src/main/AndroidManifest.xml").readText()

        assertThat(manifest).doesNotContain("@xml/backup_rules")
        assertThat(patch).contains("rename to app/src/main/res/xml/droidfs_backup_rules.xml")
    }

    @Test
    fun temporaryVaultProviderUsesPerUriGrantsInsteadOfGlobalExport() {
        val manifest = File(
            repo,
            "droidfs-upstream/src/main/AndroidManifest.xml",
        ).readText()
        val provider = manifest.substringAfter("TemporaryFileProvider").substringBefore("</application>")

        assertThat(provider).contains("android:exported=\"false\"")
        assertThat(provider).contains("android:grantUriPermissions=\"true\"")
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
    fun unusedDroidFsCameraStackIsNotShipped() {
        val wrapperManifest = File(repo, "droidfs-upstream/src/main/AndroidManifest.xml").readText()
        val wrapperBuild = File(repo, "droidfs-upstream/build.gradle.kts").readText()
        val wrapperCmake = File(repo, "droidfs-upstream/CMakeLists.txt").readText()
        val workflow = File(repo, ".github/workflows/build.yml").readText()
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(wrapperManifest).doesNotContain("android.permission.CAMERA")
        assertThat(wrapperManifest).doesNotContain("android.permission.RECORD_AUDIO")
        assertThat(wrapperManifest).doesNotContain("CameraActivity")
        assertThat(wrapperBuild).doesNotContain("androidx.camera:camera-")
        assertThat(wrapperCmake).doesNotContain("libavcodec")
        assertThat(wrapperCmake).doesNotContain("libavformat")
        assertThat(workflow).doesNotContain("app/ffmpeg/ffmpeg")
        assertThat(workflow).doesNotContain("app/ffmpeg && ./build.sh")
        assertThat(patch).contains("CameraActivity.kt.vaultshelf-disabled")
    }

    @Test
    fun vaultSettingsFillKnownSimplifiedChineseTranslationGaps() {
        val zh = File(
            repo,
            "app/src/main/res/values-zh-rCN/upstream_shared_strings.xml",
        ).readText()

        listOf(
            "title_activity_settings",
            "propose_wipe_imported_files",
            "usf_background",
            "lock_on_screen_lock",
            "usf_keep_open",
            "export_method",
        ).forEach { key ->
            assertThat(zh).contains("name=\"$key\"")
        }
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
