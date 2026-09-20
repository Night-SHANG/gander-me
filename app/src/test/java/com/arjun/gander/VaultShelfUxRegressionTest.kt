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
        assertThat(shell).doesNotContain("onOpenReaderSettings")
        assertThat(activity).doesNotContain("readerSettingsIntent")
        assertThat(shell).contains("onOpenAbout")
        assertThat(shell).doesNotContain("FoundationScreen")
        assertThat(activity).contains("DroidFsSettingsActivity")
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
        assertThat(vaultBridge).doesNotContain("StartActivityForResult")
        assertThat(vaultBridge).contains("childActive")
        assertThat(vaultBridge).contains("bridgeResumed")
        assertThat(vaultBridge).contains("pendingChildIntent")
        assertThat(vaultBridge).contains("override fun onResume()")
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
        assertThat(explorer).contains("vaultshelf_explorer_bottom_nav")
        assertThat(explorer).contains("EXTRA_RETURN_TO_FILES")
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


}
