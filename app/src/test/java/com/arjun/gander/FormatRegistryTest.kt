package com.arjun.gander

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The format list exists in four places, and they drift.
 *
 * [FileKind] routes a file once it is open. The manifest's two intent filters
 * decide whether Gander is offered in the share sheet at all. The welcome grid
 * names the kinds, and a string in strings.xml describes that grid to a screen
 * reader. Adding a format touches all four, and forgetting one is silent: the
 * app either never appears in the chooser, or appears and then says it cannot
 * open the thing it just claimed.
 *
 * This is the test that fails until they agree.
 */
@RunWith(AndroidJUnit4::class)
class FormatRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager

    private companion object {
        val MANIFEST = File("src/main/AndroidManifest.xml").readText()

        /** Every mimeType the manifest claims, in declaration order with duplicates. */
        fun claimedMimes(): List<String> =
            Regex("""android:mimeType="([^"]+)"""").findAll(MANIFEST)
                .map { it.groupValues[1] }
                .toList()

        /** The mimeTypes inside one intent-filter naming [action]. */
        fun mimesFor(action: String): Set<String> {
            val filters = Regex("""<intent-filter>(.*?)</intent-filter>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(MANIFEST)
                .map { it.groupValues[1] }
            val filter = filters.first { it.contains("android.intent.action.$action") }
            return Regex("""android:mimeType="([^"]+)"""").findAll(filter)
                .map { it.groupValues[1] }
                .toSet()
        }
    }

    private fun resolves(action: String, mime: String): Boolean {
        val intent = Intent(action).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            if (action == Intent.ACTION_VIEW) {
                setDataAndType(Uri.parse("content://test.fixtures/a"), mime)
            } else {
                type = mime
            }
        }
        return packageManager.queryIntentActivities(intent, 0).any {
            it.activityInfo.name == FileDispatchActivity::class.java.name
        }
    }

    // ---------------------------------------------------------------
    // The manifest against itself
    // ---------------------------------------------------------------

    /**
     * VIEW is "Open with"; SEND is the share sheet. A format offered by one
     * and not the other works from the file manager and is invisible from
     * Gmail, or the reverse, and nothing says so.
     */
    @Test
    fun bothIntentFiltersClaimTheSameFormats() {
        assertThat(mimesFor("VIEW")).isEqualTo(mimesFor("SEND"))
    }

    @Test
    fun noMimeTypeIsClaimedTwiceInTheSameFilter() {
        val all = claimedMimes()
        assertThat(all).hasSize(mimesFor("VIEW").size + mimesFor("SEND").size)
    }

    @Test
    fun theUnifiedDispatcherIsTheActivityThatAnswers() {
        mimesFor("VIEW").forEach { mime ->
            assertThat("$mime resolves to the dispatcher: ${resolves(Intent.ACTION_VIEW, mime)}")
                .isEqualTo("$mime resolves to the viewer: true")
        }
    }

    @Test
    fun theShareSheetOffersTheDispatcherForEveryClaimedFormat() {
        mimesFor("SEND").forEach { mime ->
            assertThat("$mime resolves to the viewer: ${resolves(Intent.ACTION_SEND, mime)}")
                .isEqualTo("$mime resolves to the viewer: true")
        }
    }

    // ---------------------------------------------------------------
    // The manifest against FileKind
    // ---------------------------------------------------------------

    /**
     * A share with no filename, which is what a mail client or a chat app
     * routinely sends, arrives with a MIME type and nothing else. If the
     * manifest claims a type that [FileKind.detect] cannot place, Gander
     * offers itself in the chooser and then shows the unsupported card.
     */
    @Test
    fun everyFormatTheManifestClaimsCanBeRoutedByMimeAlone() {
        val wildcards = mimesFor("VIEW").filter { it.endsWith("/*") }
        val exact = mimesFor("VIEW").filterNot { it.endsWith("/*") }

        // Wildcards are checked with a representative type each
        val representative = mapOf(
            "image/*" to "image/png",
            "video/*" to "video/mp4",
            "audio/*" to "audio/mpeg",
            "text/*" to "text/plain",
        )
        wildcards.forEach { pattern ->
            val mime = representative.getValue(pattern)
            assertThat("$pattern via $mime: ${FileKind.detect("", mime)}")
                .isNotEqualTo("$pattern via $mime: ${FileKind.UNSUPPORTED}")
        }
        exact.forEach { mime ->
            assertThat("$mime routes to: ${FileKind.detect("", mime)}")
                .isNotEqualTo("$mime routes to: ${FileKind.UNSUPPORTED}")
        }
    }

    /**
     * And the other way: a format Gander can render but does not claim never
     * gets offered, so the reader has to go and find the file themselves.
     */
    @Test
    fun everyFormatWithARendererIsClaimedByTheManifest() {
        val claimed = mimesFor("VIEW")
        val shouldBeOffered = mapOf(
            "application/pdf" to FileKind.PDF,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                to FileKind.DOCX,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                to FileKind.XLSX,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                to FileKind.PPTX,
            "application/vnd.ms-excel" to FileKind.XLSX,
        )
        shouldBeOffered.forEach { (mime, kind) ->
            assertThat("$mime claimed: ${mime in claimed}").isEqualTo("$mime claimed: true")
            assertThat(FileKind.detect("", mime)).isEqualTo(kind)
        }
    }

    // ---------------------------------------------------------------
    // The welcome grid against its spoken description
    // ---------------------------------------------------------------

    /**
     * The grid is nine tiles and the string is prose, so nothing but this
     * keeps them describing the same app. The KDoc on WELCOME_BADGES warns
     * about it; this is the warning made to fail.
     */
    @Test
    fun theSpokenDescriptionNamesEveryKindTheGridShows() {
        val spoken = context.getString(R.string.welcome_formats_spoken).lowercase()
        val expected = mapOf(
            "PDF" to listOf("pdf"),
            "DOC" to listOf("word", "document"),
            "XLS" to listOf("excel", "spreadsheet"),
            "PPT" to listOf("powerpoint", "slide", "presentation"),
            "IMG" to listOf("photo", "image", "picture"),
            "VID" to listOf("video"),
            "AUD" to listOf("audio", "music", "sound"),
            "MD" to listOf("markdown"),
            "TXT" to listOf("text", "code"),
        )
        WELCOME_BADGES.forEach { (label, _) ->
            val words = expected.getValue(label)
            val mentioned = words.any { it in spoken }
            assertThat("$label mentioned in welcome_formats_spoken: $mentioned")
                .isEqualTo("$label mentioned in welcome_formats_spoken: true")
        }
    }

    // ---------------------------------------------------------------

    /**
     * Every renderer the registry names has to be in the APK. This is the same
     * check FileKindTest makes against the source tree, made again against the
     * built assets, because that is what actually ships.
     */
    @Test
    fun everyViewerPageIsPackagedIntoTheAssets() {
        FileKind.entries.filter { it.page.isNotEmpty() }.forEach { kind ->
            val opened = runCatching {
                context.assets.open("viewer/${kind.page}").use { it.read() }
            }.isSuccess
            assertThat("${kind.page} is in the assets: $opened")
                .isEqualTo("${kind.page} is in the assets: true")
        }
    }

    /**
     * Read the installed merged manifest back from PackageManager. The allowlist is
     * intentionally repeated here instead of imported from Gradle: two independent
     * gates catch both an accidental permission and an accidental weakening of one gate.
     */
    @Test
    fun theInstalledPackageRequestsNothingOutsideTheReviewedAllowlist() {
        val allowedAndroid = setOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.WAKE_LOCK",
            "android.permission.READ_PHONE_STATE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_BIOMETRIC",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        )
        val allowed = allowedAndroid +
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val info = packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertThat(requested - allowed).isEmpty()
    }

    @Test
    fun internetAndNetworkStateRemainAbsentFromTheInstalledPackage() {
        val info = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertThat(requested).doesNotContain("android.permission.INTERNET")
        assertThat(requested).doesNotContain("android.permission.ACCESS_NETWORK_STATE")
    }
}
