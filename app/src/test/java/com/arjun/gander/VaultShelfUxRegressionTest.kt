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
    fun filesBottomDestinationIsARealShellTabWithPersistedSafRoots() {
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()

        assertThat(shell).contains("VaultShelfDestination.FILES -> ExternalFilesScreen(")
        assertThat(shell).contains("contentResolver.persistedUriPermissions")
        assertThat(shell).contains("ActivityResultContracts.OpenDocumentTree()")
        assertThat(shell).contains("takePersistableUriPermission")
        assertThat(activity).contains("ExternalExplorerActivity")
        assertThat(activity).contains("SafVolume(applicationContext, treeUri)")
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
        assertThat(activity).contains("VaultVolumeActivity")
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
        assertThat(shell).doesNotContain("onOpenReaderSettings")
        assertThat(activity).doesNotContain("readerSettingsIntent")
        assertThat(shell).contains("onOpenAbout")
        assertThat(shell).doesNotContain("FoundationScreen")
        assertThat(activity).contains("DroidFsSettingsActivity::class.java")
        assertThat(activity).doesNotContain("VaultSettingsActivity")
        assertThat(main).contains("EXTRA_SHOW_ABOUT")
    }

    @Test
    fun localReaderKeepsTtsEngineDiscoveryWithoutNetworkPermission() {
        val manifest = File(repo, "legado-upstream/src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android.intent.action.TTS_SERVICE")
    }

    @Test
    fun vaultShelfSoftWhitePresetUsesAppPaletteWithoutShiftingExistingStyles() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        assertThat(bridge).contains("VaultShelf 柔和白")
        assertThat(bridge).contains("bgStr = \"#F3F3F3\"")
        assertThat(bridge).contains("textColor = \"#1B1B1B\"")
        assertThat(bridge).contains("textAccentColor = \"#005FB8\"")
        assertThat(bridge).contains("ReadBookConfig.configList.add(")
        assertThat(bridge).doesNotContain("ReadBookConfig.configList.add(1,")
    }

    @Test
    fun readerFloatingActionsKeepFourSlotsAndUseBookmarkInsteadOfReplaceRule() {
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(patch).contains("android:id=\"@+id/fabBookmark\"")
        assertThat(patch).contains("android:src=\"@drawable/ic_bookmark\"")
        assertThat(patch).contains("fabBookmark.setOnClickListener { callBack.addBookmark() }")
        assertThat(patch).contains("fun addBookmark()")
    }

    @Test
    fun droidFsManualVolumePathActionIsLocalized() {
        val chinese = File(
            repo,
            "app/src/main/res/values-zh-rCN/upstream_shared_strings.xml",
        ).readText()

        assertThat(chinese).contains(
            "<string name=\"enter_volume_path\">手动输入路径</string>",
        )
    }

    @Test
    fun bookshelfGridUsesStableLegadoStyleGeometry() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()

        assertThat(source).contains("minLines = 2")
        assertThat(source).contains(".align(Alignment.BottomCenter)")
        assertThat(source).contains("textAlign = TextAlign.Center")
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

        listOf(
            "TXT", "EPUB", "UMD", "PDF", "MOBI", "AZW3", "AZW", "MARKDOWN",
            "DOCX", "XLSX", "XLS", "XLSM", "XLSB", "CSV", "ODS", "PPTX",
        )
            .forEach { format ->
                assertThat(bookModel).contains("BookFormat.$format")
                assertThat(library).contains("BookFormat.$format")
            }

        listOf("TXT", "EPUB", "UMD", "MOBI", "AZW3", "AZW").forEach { format ->
            assertThat(router).contains("BookFormat.$format")
        }
        listOf("DOCX", "XLSX", "XLS", "XLSM", "XLSB", "CSV", "ODS", "PPTX")
            .forEach { format ->
                assertThat(router).contains("BookFormat.$format")
            }

        assertThat(library).contains("\"md\", \"markdown\"")
        assertThat(library).contains("\"mobi\"")
        assertThat(library).contains("\"azw3\"")
        assertThat(library).contains("\"azw\"")
        assertThat(library).contains("\"umd\"")
        assertThat(library).contains("\"pdf\"")
        assertThat(library).contains("\"docx\"")
        assertThat(library).contains("\"pptx\"")
        assertThat(library).contains("\"xlsx\"")
        assertThat(library).contains("\"xls\"")
        assertThat(library).contains("\"xlsm\"")
        assertThat(library).contains("\"xlsb\"")
        assertThat(library).contains("\"csv\"")
        assertThat(library).contains("\"ods\"")
    }

    @Test
    fun txtLibraryImportRegistersLegadoInBackgroundAfterStartupWarmup() {
        val repository = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LocalLibraryRepository.kt",
        ).readText()
        val router = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryReaderRouter.kt",
        ).readText()
        val startup = File(
            repo,
            "app/src/main/java/com/arjun/gander/GanderStartupProvider.kt",
        ).readText()
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()
        val txtImport = repository
            .substringAfter("override suspend fun importTxt")
            .substringBefore("override suspend fun importMarkdown")

        assertThat(startup).contains("LegadoReaderBridge.initialize(appContext)")
        assertThat(startup).contains("LegadoReaderBridge.warmUpLocalReader(appContext)")
        assertThat(bridge).contains("fun warmUpLocalReader(context: Context)")
        assertThat(bridge).contains("ensureLocalTxtTocRules()")
        assertThat(txtImport).contains("val book = importFile(uri, BookFormat.TXT")
        assertThat(txtImport).contains("scheduleLegadoAttachment(book)")
        assertThat(txtImport).doesNotContain("attachLegado(")
        assertThat(repository).contains("CoroutineScope(SupervisorJob() + Dispatchers.IO)")
        assertThat(router).contains("BookFormat.TXT")
        assertThat(router).contains("LegadoReaderBridge.ensureLocalBook")
    }

    @Test
    fun droidFsTemporaryProviderHonorsProjectionForVaultReaders() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(patch).contains("val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME")
        assertThat(patch).contains("OpenableColumns.SIZE -> file.size")
        assertThat(patch).contains("COLUMN_DOCUMENT_ID -> uri.lastPathSegment")
        assertThat(patch).contains("COLUMN_LAST_MODIFIED -> 0L")
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
    fun normalFilesAndLibraryShareOneReadingIdentity() {
        val positions = File(
            repo,
            "app/src/main/java/com/arjun/gander/Positions.kt",
        ).readText()
        val router = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryReaderRouter.kt",
        ).readText()
        val safVolume = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/SafVolume.kt",
        ).readText()
        val fileRouter = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfFileRouter.kt",
        ).readText()
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()

        assertThat(positions).contains("fun keyFor(file: File)")
        assertThat(router).contains("val readingKey = Positions.keyFor(bookFile)")
        assertThat(router).contains("BookReadingPositions.get(context, key)")
        assertThat(router).contains("LegadoReaderBridge.restoreReadingPosition")
        assertThat(router).contains("BookReadingPositions.save")
        assertThat(safVolume).contains("fun uriForPath(path: String): Uri?")
        assertThat(fileRouter).contains("volume is SafVolume")
        assertThat(fileRouter).contains("com.arjun.gander.FileDispatchActivity")
        assertThat(bridge).contains("chapterUpdatedAtEpochMillis")
    }

    @Test
    fun vaultFileAndLibraryPathsCollapseToOneReadingIdentity() {
        val progress = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfProgressStore.kt",
        ).readText()
        val router = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/VaultShelfFileRouter.kt",
        ).readText()
        val bridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()

        assertThat(progress).contains("canonicalPath(path)")
        assertThat(progress).contains("PathUtils.normalizePath")
        assertThat(progress).contains("fun previousFileKey")
        assertThat(router).contains("EXTRA_PREVIOUS_FILE_KEY")
        assertThat(bridge).contains("previousFileKey")
        assertThat(bridge).contains("BookReadingPositions.get(applicationContext, old)")
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
        assertThat(dispatcher).contains("delay(READER_RETURN_TRANSITION_MS)")
        assertThat(vaultBridge).doesNotContain("StartActivityForResult")
        assertThat(vaultBridge).contains("childActive")
        assertThat(vaultBridge).contains("bridgeResumed")
        assertThat(vaultBridge).contains("pendingChildIntent")
        assertThat(vaultBridge).contains("override fun onResume()")
        assertThat(vaultBridge).contains("legadoChildActive")
        assertThat(vaultBridge).contains("delay(READER_RETURN_TRANSITION_MS)")
        assertThat(vaultBridge).contains("override fun onPause()")
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
        assertThat(guard).contains("VaultScreenshotPolicy.apply(activity)")
    }



    @Test
    fun vaultScreenshotSettingIsLiveAcrossAllVaultSurfaces() {
        val policy = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultScreenshotPolicy.kt",
        ).readText()
        val mode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val bridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()
        val guard = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultSessionGuard.kt",
        ).readText()
        val viewer = File(
            repo,
            "app/src/main/java/com/arjun/gander/ViewerActivity.kt",
        ).readText()
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()

        assertThat(policy).contains("PREF_ALLOW_SCREENSHOTS")
        assertThat(policy).contains("window.clearFlags")
        assertThat(policy).contains("window.addFlags")
        assertThat(mode).contains("VaultScreenshotPolicy.apply(this)")
        assertThat(mode).contains("override fun onWindowFocusChanged(hasFocus: Boolean)")
        assertThat(bridge).contains("VaultScreenshotPolicy.apply(this)")
        assertThat(guard).contains("OnSharedPreferenceChangeListener")
        assertThat(guard).contains("VaultScreenshotPolicy.apply(activity)")
        assertThat(viewer).contains("VaultScreenshotPolicy.apply(this)")
        assertThat(patch).contains("registerOnSharedPreferenceChangeListener")
        assertThat(patch).contains("window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)")
        assertThat(patch).contains("loadUnsafeFeatures()")
        assertThat(patch).contains("explorerAdapter.loadThumbnails")
    }

    @Test
    fun newInstallEnablesFingerprintOnlyWhenStrongBiometricsAreUsable() {
        val startup = File(
            repo,
            "app/src/main/java/com/arjun/gander/GanderStartupProvider.kt",
        ).readText()

        assertThat(startup).contains("!preferences.contains(\"usf_fingerprint\")")
        assertThat(startup).contains("FingerprintProtector.canAuthenticate(appContext) == 0")
        assertThat(startup).contains("putBoolean(\"usf_fingerprint\", true)")
    }

    @Test
    fun vaultVolumeMenuCanAddAndRemoveFingerprintWithoutChangingPassword() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()

        assertThat(patch).contains("private val addFingerprintMenuId = View.generateViewId()")
        assertThat(patch).contains("R.string.add_fingerprint")
        assertThat(patch).contains("volumeOpener.requestSaveFingerprint(")
        assertThat(patch).contains("fun requestSaveFingerprint(")
        assertThat(patch).contains("savePasswordHash = true")
        assertThat(patch).contains("name=\"remove_fingerprint\"")
        assertThat(patch).contains("允许添加指纹解锁")
        assertThat(patch).contains("关闭不会移除已经保存的指纹解锁")
        assertThat(patch).contains("使用指纹验证当前密码")
    }

    @Test
    fun droidFsManagementScreensUseVaultShelfTheme() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()

        assertThat(patch).contains("class SettingsActivity : BaseActivity()")
        assertThat(patch).contains("class AddVolumeActivity: BaseActivity()")
        assertThat(patch).contains("class ChangePasswordActivity: BaseActivity()")
        assertThat(patch).contains("applyCustomTheme = false")
    }


    @Test
    fun simplifiedChineseResourcesHaveNoDuplicateStringNamesAcrossFiles() {
        val resourceDir = File(repo, "app/src/main/res/values-zh-rCN")
        val stringPattern = Regex("""<string\s+name="([^"]+)"""")
        val owners = mutableMapOf<String, MutableList<String>>()

        resourceDir.listFiles()
            .orEmpty()
            .filter { it.extension == "xml" }
            .forEach { file ->
                stringPattern.findAll(file.readText()).forEach { match ->
                    owners.getOrPut(match.groupValues[1]) { mutableListOf() }
                        .add(file.name)
                }
            }

        val duplicates = owners.filterValues { it.size > 1 }
        assertThat(duplicates).isEmpty()
    }


    @Test
    fun droidFsPatchDoesNotSplitOneSourceFileAcrossMultipleDiffBlocks() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readLines()

        val headers = patch.filter { it.startsWith("diff --git a/") }
        val duplicates = headers
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun plainAndVaultFilesReuseDroidFsExplorerCore() {
        val main = File(
            repo,
            "app/src/main/java/com/arjun/gander/MainActivity.kt",
        ).readText()
        val safVolume = File(
            repo,
            "droidfs-upstream/src/main/java/com/vaultshelf/droidfs/SafVolume.kt",
        ).readText()
        val externalExplorer = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()
        val vaultMode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()

        assertThat(safVolume).contains("class SafVolume")
        assertThat(safVolume).contains(": EncryptedVolume()")
        assertThat(main).contains("ExternalExplorerActivity")
        assertThat(main).contains("SafVolume(applicationContext, treeUri)")
        assertThat(externalExplorer).contains("class ExternalExplorerActivity : ExplorerActivity()")
        assertThat(externalExplorer).contains("action_import_to_vault")
        assertThat(vaultMode).contains("VaultExplorerActivity::class.java")
        assertThat(patch).contains("open class ExplorerActivity : BaseExplorerActivity()")
        assertThat(patch).contains("VaultImportTargetActivity")
        assertThat(main).doesNotContain("private fun renderVault()")
    }


    @Test
    fun droidFsExplorerUsesVaultShelfVisualShell() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()
        val activity = File(
            repo,
            "app/src/main/res/layout/activity_explorer.xml",
        ).readText()
        val listItem = File(
            repo,
            "app/src/main/res/layout/adapter_explorer_element_list.xml",
        ).readText()
        val gridItem = File(
            repo,
            "app/src/main/res/layout/adapter_explorer_element_grid.xml",
        ).readText()
        val infoBar = File(
            repo,
            "app/src/main/res/layout/explorer_info_bar.xml",
        ).readText()
        val theme = File(
            repo,
            "app/src/main/res/values/themes.xml",
        ).readText()

        assertThat(patch).contains("applyCustomTheme = false")
        assertThat(activity).contains("?attr/colorSurface")
        assertThat(activity).contains("@layout/explorer_info_bar")
        assertThat(listItem).contains("@drawable/vaultshelf_explorer_item_background")
        assertThat(gridItem).contains("@drawable/vaultshelf_explorer_item_background")
        assertThat(listItem).contains("ShapeableImageView")
        assertThat(gridItem).contains("ShapeableImageView")
        assertThat(infoBar).contains("@+id/layout_icon")
        assertThat(infoBar).contains("<ImageButton")
        assertThat(theme).contains("ShapeAppearance.Gander.ExplorerPreview")
        assertThat(theme).contains("name=\"menuIconColor\"")
    }

    @Test
    fun fileOperationsDoNotRequireNotificationPermission() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()

        assertThat(patch).contains("private var askForNotificationPermission = false")
        assertThat(patch).contains("Android 13+ does not require POST_NOTIFICATIONS")
        assertThat(patch).contains("private val usfSafWrite: Boolean")
        assertThat(patch).contains("get() = sharedPrefs.getBoolean(\"usf_saf_write\", false)")
    }

    @Test
    fun vaultSettingsDoNotExposeObsoleteDroidFsThemeControls() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()

        assertThat(patch).contains("VaultShelf removes obsolete DroidFS theme controls")
        assertThat(patch).contains("-    <PreferenceCategory android:title=\"@string/theme\">")
        assertThat(patch).contains("applyCustomTheme = false")
    }

    @Test
    fun fileAndLibraryTransfersStayIndependentAndAskBeforeDeletingSources() {
        val target = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultImportTargetActivity.kt",
        ).readText()
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()
        val vault = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()

        assertThat(target).contains("EXTRA_TARGET_LIBRARY")
        assertThat(target).contains("importDirectlyIntoVaultLibrary")
        assertThat(target).contains("promptVolumeSourceChoice")
        assertThat(target).contains("vault_transfer_delete_source")
        assertThat(target).doesNotContain("migrateLibraryIdentityAndPromptDelete")
        assertThat(external).contains("confirmDeleteSelected")
        assertThat(vault).contains("confirmDeleteSelected")
        assertThat(external).contains("vault_transfer_delete_file_and_library")
        assertThat(vault).contains("vault_transfer_delete_file_and_library")
    }
    @Test
    fun unlockedVaultUsesVaultShelfShellAndVaultExplorerKeepsBottomNavigation() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val mode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val explorer = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()
        val storage = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultStorage.kt",
        ).readText()

        assertThat(patch).contains("com.arjun.gander.vault.VaultModeActivity")
        assertThat(mode).contains("VaultModeShell")
        assertThat(mode).contains("VaultExplorerActivity::class.java")
        assertThat(explorer).contains("VaultBottomBar")
        assertThat(explorer).contains("selected = VaultBottomDestination.FILES")
        assertThat(explorer).doesNotContain("VaultShelfBottomBar")
        assertThat(storage).contains("METADATA_FILE = \"/.vaultshelf/library.json\"")
        assertThat(storage).contains("LIBRARY_FILES_DIRECTORY = \"/.vaultshelf/library-files\"")
        assertThat(storage).contains("fun addPath")
        assertThat(storage).contains("fun importExternalLibraryBook")
    }
    @Test
    fun externalLibraryCanTransferToFilesAndBothVaultDestinations() {
        val library = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val target = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultImportTargetActivity.kt",
        ).readText()

        assertThat(library).contains("onExportToFiles")
        assertThat(library).contains("onImportToVaultFiles")
        assertThat(library).contains("onImportToVaultLibrary")
        assertThat(activity).contains("ACTION_IMPORT_TO_VAULT")
        assertThat(activity).contains("ACTION_IMPORT_TO_VAULT_LIBRARY")
        assertThat(target).contains("EXTRA_SOURCE_LIBRARY_IDS")
        assertThat(target).contains("importExternalLibraryIntoCurrentDirectory")
        assertThat(target).contains("importDirectlyIntoVaultLibrary")
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
        assertThat(wrapperCmake).doesNotContain("add_library(avcodec")
        assertThat(wrapperCmake).doesNotContain("add_library(avformat")
        assertThat(wrapperCmake).doesNotContain("add_library(avutil")
        assertThat(wrapperCmake).doesNotContain("add_library(mux")
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
    fun standaloneAboutDoesNotLeaveTheFileBrowserBehindTheDialog() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/MainActivity.kt",
        ).readText()

        assertThat(source).contains("private var standaloneAbout = false")
        assertThat(source).contains("showAbout(finishOnDismiss = true)")
        assertThat(source).contains("if (!standaloneAbout) render()")
        assertThat(source).contains(
            "if (finishOnDismiss && !openingLicences && !isFinishing) finish()"
        )
    }

    @Test
    fun offlineLegadoReaderNeverRegistersNetworkCallbacks() {
        val patch = File(
            repo,
            "patches/legado-vaultshelf-runtime.patch",
        ).readText()

        assertThat(patch).contains("-import io.legado.app.receiver.NetworkChangedListener")
        assertThat(patch).contains("-import io.legado.app.utils.NetworkUtils")
        assertThat(patch).contains("-        networkChangedListener.register()")
        assertThat(patch).contains("-        networkChangedListener.unRegister()")
    }

    @Test
    fun readerSettingsWaitForLegadoBookInitialization() {
        val patch = File(
            repo,
            "patches/legado-vaultshelf-runtime.patch",
        ).readText()

        assertThat(patch).contains(
            "viewModel.initData(intent) { maybeShowVaultShelfReaderSettings() }"
        )
        assertThat(patch).contains("private fun maybeShowVaultShelfReaderSettings()")
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
    @Test
    fun fourStorageZonesExposeAllTwelveDirectedTransfers() {
        val externalFiles = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()
        val externalLibrary = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()
        val vaultFiles = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()
        val vaultLibrary = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()

        // EF -> EL, VF, VL
        assertThat(externalFiles).contains("importSelectedIntoExternalLibrary")
        assertThat(externalFiles).contains("targetLibrary = false")
        assertThat(externalFiles).contains("targetLibrary = true")

        // EL -> EF, VF, VL
        assertThat(externalLibrary).contains("vault_transfer_external_library_to_files")
        assertThat(externalLibrary).contains("onImportToVaultFiles")
        assertThat(externalLibrary).contains("onImportToVaultLibrary")

        // VF -> EF, EL, VL
        assertThat(vaultFiles).contains("action_export_external")
        assertThat(vaultFiles).contains("importSelectedIntoExternalLibrary")
        assertThat(vaultFiles).contains("importSelectedIntoVaultLibrary")

        // VL -> EF, EL, VF
        assertThat(vaultLibrary).contains("onExportExternalFiles")
        assertThat(vaultLibrary).contains("onExportExternalLibrary")
        assertThat(vaultLibrary).contains("onExportVaultFiles")
    }

    @Test
    fun vaultLibraryOwnsIndependentEncryptedCopies() {
        val storage = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultStorage.kt",
        ).readText()

        assertThat(storage).contains("LIBRARY_FILES_DIRECTORY = \"/.vaultshelf/library-files\"")
        assertThat(storage).contains("copyWithinVolume(path, privatePath)")
        assertThat(storage).contains("sourcePath = path")
        assertThat(storage).contains("migrateLegacyEntries")
        assertThat(storage).contains("deleteLinkedSource: Boolean = false")
        assertThat(storage).contains("MessageDigest.getInstance(\"SHA-256\")")
        assertThat(storage).contains("sha256(it.path) == expectedHash")
    }

    @Test
    fun deletingOnlyAFileDetachesItsSurvivingLibraryRelationship() {
        val repository = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LocalLibraryRepository.kt",
        ).readText()
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()
        val target = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultImportTargetActivity.kt",
        ).readText()
        val vault = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()

        assertThat(repository).contains("suspend fun detachOriginalSource")
        assertThat(external).contains("repository.detachOriginalSource(book.id)")
        assertThat(target).contains("repository.detachOriginalSource(book.id)")
        assertThat(vault).contains("vaultLibrary.removePath(element.fullPath)")
    }

    @Test
    fun phoneStatePermissionIsNotShippedForLocalTts() {
        val legadoManifest = File(
            repo,
            "legado-upstream/src/main/AndroidManifest.xml",
        ).readText()
        val build = File(repo, "app/build.gradle.kts").readText()
        val startup = File(
            repo,
            "app/src/main/java/com/arjun/gander/GanderStartupProvider.kt",
        ).readText()
        val patch = File(
            repo,
            "patches/legado-vaultshelf-runtime.patch",
        ).readText()

        assertThat(legadoManifest).doesNotContain("android.permission.READ_PHONE_STATE")
        assertThat(build).doesNotContain("\"android.permission.READ_PHONE_STATE\",")
        assertThat(startup).contains("putBoolean(\"pauseReadAloudWhilePhoneCalls\", false)")
        assertThat(patch).contains("-            android:key=\"pauseReadAloudWhilePhoneCalls\"")
        assertThat(legadoManifest).contains("android.permission.WAKE_LOCK")
        assertThat(legadoManifest).contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
    }

    @Test
    fun explorerShellHandlesInsetsAndSimplifiedChineseOverflowLabel() {
        val patch = File(
            repo,
            "patches/droidfs-vaultshelf-file-routing.patch",
        ).readText()
        val chinese = File(
            repo,
            "app/src/main/res/values-zh-rCN/upstream_shared_strings.xml",
        ).readText()
        val layout = File(
            repo,
            "app/src/main/res/layout/activity_explorer.xml",
        ).readText()

        assertThat(patch).contains("WindowInsetsCompat.Type.systemBars()")
        assertThat(patch).contains("WindowInsetsCompat.Type.displayCutout()")
        assertThat(patch).contains("encryptedVolume !is SafVolume")
        assertThat(patch).contains("removeAll { it.name == \".vaultshelf\" }")
        assertThat(chinese).contains("<string name=\"more_options\">更多选项</string>")
        assertThat(layout).contains("vaultshelf_explorer_bottom_nav")
    }


    @Test
    fun crossZoneDeletionRefreshesTheSourceZoneWhenItBecomesVisibleAgain() {
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val library = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()

        assertThat(activity).contains("libraryRevision by mutableIntStateOf(0)")
        assertThat(activity).contains("libraryRevision += 1")
        assertThat(activity).contains("externalRevision = libraryRevision")
        assertThat(shell).contains("externalRevision = externalRevision")
        assertThat(library).contains("LaunchedEffect(repository, externalRevision)")
        assertThat(library).doesNotContain("onFailure {\n                importFailed = true")
    }

    @Test
    fun plainExternalFilesDoNotInheritVaultScreenshotProtection() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(patch).contains("intent.getBooleanExtra(\"vaultshelf.plain_volume\", false)")
        assertThat(patch).contains("intent.getBooleanExtra(\"vaultshelf.plain_surface\", false)")
        assertThat(patch).contains("putExtra(\"vaultshelf.plain_surface\", true)")
    }

    @Test
    fun explorerTopInsetIsInstalledAfterDroidFsBasePostCreate() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val explorerPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        )

        val postCreate = explorerPatch.indexOf("override fun onPostCreate(savedInstanceState: Bundle?)")
        val superPostCreate = explorerPatch.indexOf("super.onPostCreate(savedInstanceState)", postCreate)
        val topInset = explorerPatch.indexOf("bars.top", superPostCreate)

        assertThat(postCreate).isAtLeast(0)
        assertThat(superPostCreate).isGreaterThan(postCreate)
        assertThat(topInset).isGreaterThan(superPostCreate)
        assertThat(explorerPatch).contains("ViewCompat.requestApplyInsets(root)")
    }

    @Test
    fun externalAndVaultNavigationUseSeparateComposeBottomBars() {
        val externalBar = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfBottomBar.kt",
        ).readText()
        val vaultBar = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultBottomBar.kt",
        ).readText()
        val mainShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()
        val vault = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()
        val layout = File(repo, "app/src/main/res/layout/activity_explorer.xml").readText()

        assertThat(externalBar).contains("fun VaultShelfBottomBar(")
        assertThat(externalBar).doesNotContain("VaultShelfVaultDestinations")
        assertThat(vaultBar).contains("fun VaultBottomBar(")
        assertThat(vaultBar).doesNotContain("SWITCH(")
        assertThat(vaultBar).contains("HOME(R.string.vaultshelf_nav_home")
        assertThat(vaultBar).contains("LIBRARY(R.string.vaultshelf_nav_library")
        assertThat(vaultBar).contains("FILES(R.string.vaultshelf_nav_files")
        assertThat(vaultBar).contains("SETTINGS(R.string.vaultshelf_nav_settings")
        listOf(externalBar, vaultBar).forEach { source ->
            assertThat(source).contains("Surface(")
            assertThat(source).contains("shadowElevation = 6.dp")
            assertThat(source).contains("padding(horizontal = 6.dp, vertical = 6.dp)")
            assertThat(source).doesNotContain("NavigationBar(")
            assertThat(source).doesNotContain("NavigationBarItem(")
            assertThat(source).doesNotContain("tonalElevation")
        }
        listOf(mainShell, external).forEach { source ->
            assertThat(source).contains("VaultShelfBottomBar(")
            assertThat(source).doesNotContain("VaultBottomBar(")
        }
        listOf(vaultShell, vault).forEach { source ->
            assertThat(source).contains("VaultBottomBar(")
            assertThat(source).doesNotContain("VaultShelfBottomBar(")
        }
        listOf(external, vault).forEach { source ->
            assertThat(source).contains("ComposeView")
        }
        assertThat(external).contains("selected = VaultShelfDestination.FILES")
        assertThat(vault).contains("selected = VaultBottomDestination.FILES")
        assertThat(vaultShell).contains("bottomDestination")
        assertThat(layout).contains("androidx.compose.ui.platform.ComposeView")
        assertThat(layout).doesNotContain("BottomNavigationView")
        assertThat(layout).doesNotContain("vaultshelf_explorer_nav_item_tint")
    }

    @Test
    fun droidFsExplorerSearchUsesCancelableRecursiveVolumeTraversal() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val explorerPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        )

        assertThat(explorerPatch).contains("searchMenuItemId")
        assertThat(explorerPatch).contains("R.drawable.icon_folder_search")
        assertThat(explorerPatch).contains("ArrayDeque<String>()")
        assertThat(explorerPatch).contains("encryptedVolume.readDir(directory)")
        assertThat(explorerPatch).contains("child.name.contains(query, ignoreCase = true)")
        assertThat(explorerPatch).contains("displaySearchResults(matches, query, rootPath, complete = false)")
        assertThat(explorerPatch).contains("searchJob?.cancel()")
        assertThat(explorerPatch).contains("activeSearchQuery.isNotEmpty() -> exitSearch()")
        assertThat(explorerPatch).contains("val visited = HashSet<String>()")
        assertThat(explorerPatch).contains("if (!visited.add(directory)) continue")
        assertThat(explorerPatch).contains("menu.findItem(R.id.rename).isVisible = !searchActive")
    }

    @Test
    fun sharedComposeExplorerBottomBarHasOneSystemBottomInsetOwner() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val layout = File(repo, "app/src/main/res/layout/activity_explorer.xml").readText()
        val explorerPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        )

        assertThat(layout).contains("androidx.compose.ui.platform.ComposeView")
        assertThat(layout).doesNotContain("BottomNavigationView")
        assertThat(explorerPatch).contains("bars.bottom")
        assertThat(explorerPatch).doesNotContain(
            "if (ime.bottom > 0) ime.bottom else bars.bottom",
        )
    }

    @Test
    fun vaultCanReturnDirectlyToExternalHomeFromHomeAndSettings() {
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()

        assertThat(shell).contains("vault_return_external_home")
        assertThat(shell.split("vault_return_external_home").size - 1).isAtLeast(2)
        assertThat(activity).contains("VaultShelfActivity.EXTRA_INITIAL_DESTINATION")
        assertThat(activity).contains("Intent.FLAG_ACTIVITY_CLEAR_TOP")
    }

    @Test
    fun transferSourceHandlingCoversAllTwelveRoutesAndDefaultsToPrompting() {
        val prefs = File(
            repo,
            "app/src/main/java/com/arjun/gander/transfer/TransferBehaviorPreferences.kt",
        ).readText()
        val settings = File(
            repo,
            "app/src/main/java/com/arjun/gander/transfer/TransferBehaviorSettingsActivity.kt",
        ).readText()

        listOf(
            "EXTERNAL_FILES_TO_EXTERNAL_LIBRARY",
            "EXTERNAL_FILES_TO_VAULT_FILES",
            "EXTERNAL_FILES_TO_VAULT_LIBRARY",
            "EXTERNAL_LIBRARY_TO_EXTERNAL_FILES",
            "EXTERNAL_LIBRARY_TO_VAULT_FILES",
            "EXTERNAL_LIBRARY_TO_VAULT_LIBRARY",
            "VAULT_FILES_TO_EXTERNAL_FILES",
            "VAULT_FILES_TO_EXTERNAL_LIBRARY",
            "VAULT_FILES_TO_VAULT_LIBRARY",
            "VAULT_LIBRARY_TO_EXTERNAL_FILES",
            "VAULT_LIBRARY_TO_EXTERNAL_LIBRARY",
            "VAULT_LIBRARY_TO_VAULT_FILES",
        ).forEach { route ->
            assertThat(prefs).contains(route)
            assertThat(settings).contains("TransferRoute.$route")
        }
        assertThat(prefs).contains("getBoolean(KEY_AUTOMATIC, false)")
        assertThat(prefs).contains("TransferSourceDecision.KEEP.name")
        assertThat(prefs).contains("if (isAutomatic(context)) decision(context, route) else null")
        assertThat(settings).contains("enabled = automatic")
    }

    @Test
    fun customVaultPlaintextRoutesRespectTheDroidFsExportSecuritySwitch() {
        val policy = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultSecurityPolicy.kt",
        ).readText()
        val vaultFiles = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()
        val vaultLibrary = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(policy).contains("getBoolean(\"usf_decrypt\", false)")
        assertThat(policy).contains("getBoolean(\"usf_share\", false)")
        assertThat(vaultFiles).contains("VaultSecurityPolicy.allowPlaintextExport")
        assertThat(vaultLibrary).contains("VaultSecurityPolicy.allowPlaintextExport")
        assertThat(patch).contains("usf_decrypt = sharedPrefs.getBoolean(\"usf_decrypt\", false)")
        assertThat(patch).contains("usf_share = sharedPrefs.getBoolean(\"usf_share\", false)")
    }

    @Test
    fun retainedDroidFsSettingsStillHaveRuntimeConsumers() {
        val rootPreferences = File(
            repo,
            "third_party/droidfs/app/src/main/res/xml/root_preferences.xml",
        ).readText()
        val unsafePreferences = File(
            repo,
            "third_party/droidfs/app/src/main/res/xml/unsafe_features_preferences.xml",
        ).readText()
        val baseExplorer = File(
            repo,
            "third_party/droidfs/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        ).readText()
        val explorer = File(
            repo,
            "third_party/droidfs/app/src/main/java/sushi/hardcore/droidfs/explorers/ExplorerActivity.kt",
        ).readText()
        val volumeApp = File(
            repo,
            "third_party/droidfs/app/src/main/java/sushi/hardcore/droidfs/VolumeManagerApp.kt",
        ).readText()
        val settings = File(
            repo,
            "third_party/droidfs/app/src/main/java/sushi/hardcore/droidfs/SettingsActivity.kt",
        ).readText()

        assertThat(rootPreferences).contains("key=\"sort_order\"")
        assertThat(baseExplorer).contains("Constants.SORT_ORDER_KEY")
        listOf("folders_first", "thumbnails", "map_folders").forEach {
            assertThat(rootPreferences).contains("key=\"$it\"")
            assertThat(baseExplorer).contains("\"$it\"")
        }
        listOf("usf_decrypt", "usf_share").forEach {
            assertThat(unsafePreferences).contains("android:key=\"$it\"")
            assertThat(explorer).contains("\"$it\"")
        }
        listOf("usf_background", "usf_keep_open", "lock_on_screen_lock").forEach {
            assertThat(unsafePreferences).contains("android:key=\"$it\"")
            assertThat(volumeApp).contains("\"$it\"")
        }
        listOf("usf_fingerprint", "usf_open", "usf_expose", "usf_saf_write", "export_method").forEach {
            assertThat(unsafePreferences).contains("android:key=\"$it\"")
        }
        assertThat(settings).contains("findPreference<SwitchPreferenceCompat>(\"usf_fingerprint\")")
        assertThat(settings).contains("findPreference<ListPreference>(\"export_method\")")
    }

    @Test
    fun vaultEntryAndVolumeChooserDoNotTrapNavigation() {
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val chooser = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultVolumeActivity.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(activity).contains("VaultVolumeActivity::class.java")
        assertThat(activity).contains("EXTRA_VAULT_SHELL_ENTRY")
        assertThat(activity).doesNotContain("VaultModeActivity.EXTRA_VOLUME_ID")
        assertThat(chooser).contains("VaultShelfBottomBar(")
        assertThat(chooser).doesNotContain("VaultBottomBar(")
        assertThat(chooser).contains("selected = VaultShelfDestination.VAULT")
        assertThat(chooser).contains("WindowInsetsCompat.Type.ime()")
        assertThat(chooser).contains("updateBottomNavigationVisibility()")
        assertThat(chooser).contains("override fun onWindowFocusChanged")
        assertThat(chooser).contains("bottom = bars.bottom")
        assertThat(chooser).doesNotContain("bottom = if (ime")
        assertThat(chooser).doesNotContain("showExitConfirmation")
        assertThat(chooser).doesNotContain("vault_exit_title")
        assertThat(chooser).doesNotContain("switchingMode")
        assertThat(vaultShell).contains("VaultModeDestination.HOME -> VaultBottomDestination.HOME")
        assertThat(vaultShell).contains("VaultModeDestination.LIBRARY -> VaultBottomDestination.LIBRARY")
        assertThat(vaultShell).contains("VaultModeDestination.SETTINGS -> VaultBottomDestination.SETTINGS")
        assertThat(patch).doesNotContain("resolveDefaultVolume")
        assertThat(patch).contains("explorerRouter.importTargetMode")
        assertThat(patch).doesNotContain("resolveDefaultVolume")
    }

    @Test
    fun volumeChooserAndExplorerExposePhysicalBackButtons() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val mainPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/MainActivity.kt",
        ).substringBefore("diff --git ")
        val explorerPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        )

        assertThat(mainPatch).contains("supportActionBar?.setDisplayHomeAsUpEnabled(true)")
        assertThat(mainPatch).contains("onBackPressedDispatcher.onBackPressed()")
        assertThat(explorerPatch).contains("supportActionBar?.setDisplayHomeAsUpEnabled(true)")
        assertThat(explorerPatch).contains("onBackPressedDispatcher.onBackPressed()")
    }

    @Test
    fun externalFolderAuthorizationRemovalRequiresConfirmation() {
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()

        assertThat(shell).contains("pendingRemoveRoot")
        assertThat(shell).contains("vaultshelf_files_remove_access_title")
        assertThat(shell).contains("releasePersistableUriPermission")
    }

    @Test
    fun transferDestinationFlowReturnsToItsSourceSurface() {
        val target = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultImportTargetActivity.kt",
        ).readText()
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()

        assertThat(target).contains("private fun finishToSource()")
        assertThat(target).contains("VaultShelfActivity.EXTRA_INITIAL_DESTINATION, \"LIBRARY\"")
        assertThat(target).contains("ExternalExplorerActivity::class.java")
        assertThat(target).contains("Intent.FLAG_ACTIVITY_CLEAR_TOP")
        assertThat(external).contains("override fun onResume()")
        assertThat(external).contains("refreshCurrentDirectory()")
    }

    @Test
    fun vaultEntryAlwaysUsesChooserAndDefaultVaultFeatureIsRemoved() {
        val preference = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultDefaultVolumePreference.kt",
        )
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val modeActivity = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(preference.exists()).isFalse()
        assertThat(activity).contains("VaultVolumeActivity::class.java")
        assertThat(patch).doesNotContain("resolveDefaultVolume")
        assertThat(patch).doesNotContain("setDefaultVolumeMenuId")
        assertThat(patch).doesNotContain("vaultshelf_default_volume_title")
        assertThat(patch).doesNotContain("vaultshelf_default_volume_label")
        assertThat(patch).contains("checkboxDefaultOpen.visibility = View.GONE")
        assertThat(vaultShell).contains("vault_switch_volume")
        assertThat(vaultShell).contains("onSwitchVault = onSwitchVault")
        assertThat(modeActivity).contains("showVaultSwitchDialog(volumeId)")
        val externalExit = modeActivity.substringAfter("onOpenExternalDestination = {")
            .substringBefore("modifier = Modifier.safeDrawingPadding()")
        assertThat(externalExit).doesNotContain("closeVolume")
    }

    @Test
    fun vaultModeUsesDroidFsBaseActivityBeforeConstructingVolumeOpener() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()

        assertThat(source).contains("class VaultModeActivity : BaseActivity()")
        assertThat(source).contains("applyCustomTheme = false")
        assertThat(source).contains("switchVolumeOpener = VolumeOpener(this)")
        assertThat(source).doesNotContain("class VaultModeActivity : AppCompatActivity()")
    }

    @Test
    fun vaultNavigationUsesFourTabsPagerAndSettingsSwitchDoesNotStackOldVaults() {
        val vaultBar = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultBottomBar.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val vaultMode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val vaultFiles = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()

        assertThat(vaultBar).doesNotContain("SWITCH(")
        assertThat(vaultShell).contains("HorizontalPager(")
        assertThat(vaultShell).contains("userScrollEnabled = false")
        assertThat(vaultShell).contains("pagerState.animateVaultShelfPageTo(page)")
        assertThat(vaultShell).contains("VaultBottomDestination.FILES -> onOpenFiles()")
        assertThat(vaultShell).contains("onSwitchVault = onSwitchVault")
        assertThat(vaultFiles).doesNotContain("openVaultSwitcher")
        assertThat(vaultMode).contains("showVaultSwitchDialog")
        assertThat(vaultMode).contains("setSingleChoiceItems")
        assertThat(vaultMode).contains("switchVolumeOpener.openVolume")
        assertThat(vaultMode).contains("finish()")
    }

    @Test
    fun topLevelNavigationUsesFixedBarsAndFlClashStylePagerSwitching() {
        val mainShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val motion = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfNavigationMotion.kt",
        ).readText()

        listOf(mainShell, vaultShell).forEach { source ->
            assertThat(source).contains("HorizontalPager(")
            assertThat(source).contains("userScrollEnabled = false")
            assertThat(source).contains("animateVaultShelfPageTo")
            assertThat(source).contains("pagerState.animateVaultShelfPageTo(requestedPage)")
            assertThat(source).doesNotContain("pagerState.scrollToPage(requestedPage)")
            assertThat(source).doesNotContain("AnimatedContent(")
            assertThat(source).doesNotContain("Crossfade(")
            assertThat(source).doesNotContain("scaleIn(")
        }
        assertThat(motion).contains("animateScrollToPage")
        assertThat(motion).contains("VAULTSHELF_PAGE_TRANSITION_MS = 300")
        assertThat(motion).contains("CubicBezierEasing(0f, 0f, 0.58f, 1f)")
        assertThat(motion).contains("overridePendingTransition(0, 0)")
        assertThat(motion).doesNotContain("AnimatedContent(")
        assertThat(motion).doesNotContain("fadeIn(")
        assertThat(motion).doesNotContain("fadeOut(")
    }

    @Test
    fun unlockedVaultBackBoundaryConfirmsFromShellAndExplorerRootOnly() {
        val mode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val explorer = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()
        val exit = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultExitCoordinator.kt",
        ).readText()
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val explorerPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        )

        assertThat(mode).contains("onBackPressedDispatcher.addCallback")
        assertThat(mode).contains("VaultExitCoordinator.confirmExit")
        assertThat(explorer).contains("override fun onRootBackPressed")
        assertThat(explorer).contains("VaultExitCoordinator.confirmExit")
        assertThat(explorerPatch).contains("protected open fun onRootBackPressed(): Boolean = false")
        assertThat(explorerPatch).contains("if (onRootBackPressed()) return@addCallback")
        assertThat(explorerPatch).contains("activeSearchQuery.isNotEmpty() -> exitSearch()")
        assertThat(exit).contains("vault_exit_title")
        assertThat(exit).contains("vault_exit_confirm")
        assertThat(exit).contains("Intent.FLAG_ACTIVITY_CLEAR_TOP")
        assertThat(exit).contains("EXTRA_PRESERVE_DESTINATION")
        assertThat(external).contains("EXTRA_PRESERVE_DESTINATION")
    }

    @Test
    fun chooserBottomBarHasStableInsetsAndHidesForUnlockPrompts() {
        val chooser = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultVolumeActivity.kt",
        ).readText()
        val externalBar = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfBottomBar.kt",
        ).readText()

        assertThat(chooser).contains("override fun onPostCreate")
        assertThat(chooser).contains("WindowInsetsCompat.Type.systemBars()")
        assertThat(chooser).contains("bottom = bars.bottom")
        assertThat(chooser).contains("windowFocused && !imeVisible")
        assertThat(chooser).contains("override fun onWindowFocusChanged")
        assertThat(externalBar).doesNotContain("windowInsets")
        assertThat(externalBar).doesNotContain("NavigationBar(")
        assertThat(externalBar).doesNotContain("tonalElevation")
    }

    @Test
    fun enteringAnyVaultGoesThroughChooserAndStartsAtVaultHome() {
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val vaultMode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()

        assertThat(external).contains("VaultVolumeActivity::class.java")
        assertThat(external).doesNotContain("VaultModeActivity.EXTRA_VOLUME_ID")
        assertThat(vaultMode).contains(
            "const val EXTRA_VOLUME_ID = \"vaultshelf.mode.volume_id\"",
        )
        assertThat(patch).contains(
            "explorerIntent.putExtra(\"vaultshelf.mode.volume_id\", volumeId)",
        )
        assertThat(patch).doesNotContain(
            "explorerIntent.putExtra(\"vaultshelf.mode.initial_destination\", \"FILES\")",
        )
    }

    @Test
    fun reusedShellActivitiesApplyRequestedDestinationImmediately() {
        val external = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val externalShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val vaultMode = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeActivity.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()

        assertThat(external).contains("override fun onNewIntent(intent: Intent)")
        assertThat(external).contains("applyNavigationIntent(intent)")
        assertThat(externalShell).contains("LaunchedEffect(initialDestinationName)")
        assertThat(vaultMode).contains("override fun onNewIntent(intent: Intent)")
        assertThat(vaultShell).contains("LaunchedEffect(initialDestinationName)")
    }

    @Test
    fun readerChromeOnlyReplacesBrownPrimaryAndUsesReadableForeground() {
        val preferences = File(
            repo,
            "app/src/main/java/com/arjun/gander/reader/ReaderChromePreferences.kt",
        ).readText()
        val settings = File(
            repo,
            "app/src/main/java/com/arjun/gander/reader/ReaderAppearanceSettingsActivity.kt",
        ).readText()
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(preferences).contains("DEFAULT_PRIMARY = \"#F3F3F3\"")
        assertThat(preferences).contains("vaultshelf_reader_chrome_primary")
        assertThat(settings).contains("reader_appearance_primary")
        assertThat(settings).doesNotContain("reader_appearance_accent")
        assertThat(patch).contains("applyVaultShelfReaderChrome")
        assertThat(patch).contains("ColorUtils.isColorLight(primaryColor)")
        assertThat(patch).doesNotContain("#005FB8")
        assertThat(patch).contains("accent, progress, button")
    }

    @Test
    fun vaultShelfSoftWhiteIsTheProductDefaultWithoutMutatingStockPresets() {
        val bridge = File(
            repo,
            "legado-upstream/src/main/java/com/vaultshelf/legado/LegadoReaderBridge.kt",
        ).readText()
        val patch = File(repo, "patches/legado-vaultshelf-runtime.patch").readText()

        assertThat(bridge).contains("VAULTSHELF_SOFT_WHITE_PRESET = \"VaultShelf 柔和白\"")
        assertThat(bridge).contains("configList.indexOfFirst")
        assertThat(bridge).contains("preferences.contains(PreferKey.readStyleSelect)")
        assertThat(bridge).contains("ReadBookConfig.readStyleSelect = index")
        assertThat(bridge).doesNotContain("soft_white_default")
        assertThat(bridge).doesNotContain("selectedName == \"微信读书\"")
        assertThat(patch).doesNotContain("defaultData/readConfig.json")
        assertThat(patch).doesNotContain("VaultShelf 柔和白")
    }

    @Test
    fun libraryOperationFailuresUseTransientTypedSnackbars() {
        val library = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()
        val strings = File(repo, "app/src/main/res/values-zh-rCN/strings.xml").readText()

        assertThat(library).contains("SnackbarHostState")
        assertThat(library).contains("SnackbarDuration.Long")
        assertThat(library).contains("vaultshelf_library_import_failed")
        assertThat(library).contains("vaultshelf_library_import_partial")
        assertThat(library).contains("vaultshelf_library_export_failed")
        assertThat(library).contains("vaultshelf_library_export_partial")
        assertThat(library).doesNotContain("var importFailed")
        assertThat(library).doesNotContain("if (importFailed)")
        assertThat(library).contains("dismissTransientMessage()")
        assertThat(strings).contains("部分文件导入失败")
        assertThat(strings).contains("部分书籍导出失败")
    }

    @Test
    fun searchImeHidesGlobalBottomNavigationAcrossLibrariesAndExplorers() {
        val externalShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val vaultShell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val externalExplorer = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/ExternalExplorerActivity.kt",
        ).readText()
        val vaultExplorer = File(
            repo,
            "app/src/main/java/com/arjun/gander/files/VaultExplorerActivity.kt",
        ).readText()

        assertThat(externalShell).contains("WindowInsets.ime.getBottom")
        assertThat(externalShell).contains("if (!imeVisible)")
        assertThat(vaultShell).contains("WindowInsets.ime.getBottom")
        assertThat(vaultShell).contains("if (bottomBarVisible && !imeVisible)")
        listOf(externalExplorer, vaultExplorer).forEach { source ->
            assertThat(source).contains("WindowInsetsCompat.Type.ime()")
            assertThat(source).contains("view.isVisible = !insets.isVisible")
        }
        val chooser = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultVolumeActivity.kt",
        ).readText()
        assertThat(chooser).contains("windowFocused && !imeVisible")
        assertThat(chooser).contains("bottom = bars.bottom")
    }

    @Test
    fun vaultLibraryReusesExternalLibraryDiscoveryControls() {
        val externalLibrary = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()
        val vaultLibrary = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val vaultStorage = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultStorage.kt",
        ).readText()

        assertThat(externalLibrary).contains("internal enum class ShelfViewMode")
        assertThat(externalLibrary).contains("internal enum class ShelfSort")
        assertThat(externalLibrary).contains("internal fun ShelfHeader")
        assertThat(externalLibrary).contains("filterAndSortShelfItems")
        assertThat(externalLibrary).contains("internal fun BookMenuButton")

        assertThat(vaultLibrary).contains("ShelfHeader(")
        assertThat(vaultLibrary).contains("filterAndSortShelfItems(")
        assertThat(vaultLibrary).contains("BookMenuButton(")
        assertThat(vaultLibrary).contains("ShelfSort.LAST_ACTIVITY")
        assertThat(vaultLibrary).contains("ShelfSort.TITLE")
        assertThat(vaultLibrary).contains("ShelfSort.ADDED")
        assertThat(vaultLibrary).contains("ShelfViewMode.GRID")
        assertThat(vaultLibrary).contains("ShelfViewMode.LIST")
        assertThat(vaultLibrary).doesNotContain("VaultLibraryHeader")
        assertThat(vaultLibrary).doesNotContain("VaultLibrarySort")
        assertThat(vaultLibrary).doesNotContain("VaultLibraryViewMode")
        assertThat(vaultLibrary).doesNotContain("filterAndSortVaultBooks")

        assertThat(vaultLibrary).contains("entryToRename")
        assertThat(vaultLibrary).contains("entryToInspect")
        assertThat(vaultLibrary).contains("libraryStore.rename(entry.id")
        assertThat(vaultStorage).contains("fun rename(id: String, title: String)")
    }

    @Test
    fun fileExplorerPromotesSearchAndCloseAndKeepsImeOffBottomInset() {
        val patch = File(repo, "patches/droidfs-vaultshelf-file-routing.patch").readText()
        val explorerPatch = patch.substringAfter(
            "diff --git a/app/src/main/java/sushi/hardcore/droidfs/explorers/BaseExplorerActivity.kt",
        )

        assertThat(explorerPatch).contains("searchMenuItemId")
        assertThat(explorerPatch).contains("MenuItem.SHOW_AS_ACTION_ALWAYS")
        assertThat(explorerPatch).contains("menu.findItem(R.id.close).apply")
        assertThat(explorerPatch).contains("bars.bottom")
        assertThat(explorerPatch).doesNotContain(
            "if (ime.bottom > 0) ime.bottom else bars.bottom",
        )
    }

    @Test
    fun vaultHomeOrdersRecentThenQuickAccessThenExternalReturn() {
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultModeShell.kt",
        ).readText()
        val home = shell.substringAfter("private fun VaultHomeScreen(")
            .substringBefore("private fun VaultLibraryScreen(")

        val recent = home.indexOf("vault_home_recent")
        val quick = home.indexOf("vaultshelf_quick_access")
        val library = home.indexOf("vaultshelf_library_card", quick)
        val files = home.indexOf("vaultshelf_open_documents", quick)
        val external = home.indexOf("vault_return_external_home", quick)

        assertThat(recent).isAtLeast(0)
        assertThat(quick).isGreaterThan(recent)
        assertThat(library).isGreaterThan(quick)
        assertThat(files).isGreaterThan(library)
        assertThat(external).isGreaterThan(files)
        assertThat(home).contains("QuickActionTile(")
        assertThat(home).contains("verticalScroll(rememberScrollState())")
    }

    @Test
    fun externalFolderLaunchKeepsMatchedPlatformBackTransition() {
        val activity = File(
            repo,
            "app/src/main/java/com/arjun/gander/VaultShelfActivity.kt",
        ).readText()
        val openFolder = activity.substringAfter("private fun openExternalFolder(")
            .substringBefore("private fun ")

        assertThat(openFolder).contains("ExternalExplorerActivity::class.java")
        assertThat(openFolder).doesNotContain("applyVaultShelfPeerTransition()")
        assertThat(openFolder).doesNotContain("overridePendingTransition(0, 0)")
    }

    @Test
    fun transientLegadoReadersKeepTheirParentAliveThroughReturnAnimation() {
        val dispatcher = File(
            repo,
            "app/src/main/java/com/arjun/gander/FileDispatchActivity.kt",
        ).readText()
        val vaultBridge = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultContentActivity.kt",
        ).readText()

        assertThat(dispatcher).contains("READER_RETURN_TRANSITION_MS = 300L")
        assertThat(dispatcher).contains("delay(READER_RETURN_TRANSITION_MS)")
        assertThat(dispatcher).contains("transientReaderActive = false")

        assertThat(vaultBridge).contains("legadoChildActive")
        assertThat(vaultBridge).contains("READER_RETURN_TRANSITION_MS = 300L")
        assertThat(vaultBridge).contains("delay(READER_RETURN_TRANSITION_MS)")
        assertThat(vaultBridge).contains("if (legadoChildActive)")
        assertThat(vaultBridge).contains("openWithGander")
    }

    @Test
    fun homeQuickAccessOrdersLibraryThenFilesThenVault() {
        val shell = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()
        val quick = shell.substringAfter("vaultshelf_quick_access")

        val library = quick.indexOf("vaultshelf_library_card")
        val files = quick.indexOf("vaultshelf_open_documents")
        val vault = quick.indexOf("vaultshelf_vault_card")
        assertThat(library).isAtLeast(0)
        assertThat(files).isGreaterThan(library)
        assertThat(vault).isGreaterThan(files)
    }

    @Test
    fun bothLibrariesAcceptEveryDocumentFormatSupportedByBundledDocumentViewers() {
        val format = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryBook.kt",
        ).readText()
        val local = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LocalLibraryRepository.kt",
        ).readText()
        val vault = File(
            repo,
            "app/src/main/java/com/arjun/gander/vault/VaultStorage.kt",
        ).readText()
        val viewer = File(
            repo,
            "app/src/main/java/com/arjun/gander/FileKind.kt",
        ).readText()

        listOf("DOCX", "XLSX", "XLS", "XLSM", "XLSB", "CSV", "ODS", "PPTX")
            .forEach { name ->
                assertThat(format).contains(name)
                assertThat(local).contains("BookFormat.$name")
            }
        listOf("docx", "xlsx", "xls", "xlsm", "xlsb", "csv", "ods", "pptx")
            .forEach { extension ->
                assertThat(format).contains("\"$extension\"")
            }
        assertThat(local).contains("override suspend fun importDocument")
        assertThat(vault).contains("BookFormat.fromFileName")
        assertThat(viewer).contains("DOCX")
        assertThat(viewer).contains("XLSX")
        assertThat(viewer).contains("PPTX")
    }

}
