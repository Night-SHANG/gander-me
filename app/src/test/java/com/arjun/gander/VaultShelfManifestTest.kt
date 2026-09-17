package com.arjun.gander

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** Protects the activity split that lets VaultShelf evolve without rewriting Gander's viewer. */
class VaultShelfManifestTest {

    private companion object {
        val MANIFEST = File("../app/src/main/AndroidManifest.xml")
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    @Test
    fun vaultShelfOwnsTheLauncherAndLegacyBrowserRemainsDeclared() {
        val activities = activities()
        val vaultShelf = activities.singleOrNull { it.name == ".VaultShelfActivity" }

        assertWithMessage("VaultShelfActivity must be declared").that(vaultShelf).isNotNull()
        assertThat(vaultShelf!!.actions).contains("android.intent.action.MAIN")
        assertThat(vaultShelf.categories).contains("android.intent.category.LAUNCHER")

        val legacyBrowser = activities.singleOrNull { it.name == ".MainActivity" }
        assertWithMessage("MainActivity must remain available as the mature file browser")
            .that(legacyBrowser)
            .isNotNull()
        assertThat(legacyBrowser!!.actions).doesNotContain("android.intent.action.MAIN")
    }

    @Test
    fun externalViewAndShareIntentsStillBelongToViewerActivity() {
        val viewer = activities().single { it.name == ".ViewerActivity" }

        assertThat(viewer.actions).contains("android.intent.action.VIEW")
        assertThat(viewer.actions).contains("android.intent.action.SEND")
    }

    @Test
    fun importedBookReadersAreNotExported() {
        val activities = activities()
        val txtReader = activities.singleOrNull { it.name == ".TxtReaderActivity" }
        val epubReader = activities.singleOrNull { it.name == ".EpubReaderActivity" }

        assertWithMessage("TxtReaderActivity must be declared").that(txtReader).isNotNull()
        assertThat(txtReader!!.exported).isFalse()
        assertThat(txtReader.actions).isEmpty()

        assertWithMessage("EpubReaderActivity must be declared").that(epubReader).isNotNull()
        assertThat(epubReader!!.exported).isFalse()
        assertThat(epubReader.actions).isEmpty()
    }

    private fun activities(): List<ActivityContract> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(MANIFEST)
        val nodes = document.getElementsByTagName("activity")
        return (0 until nodes.length).map { index ->
            val activity = nodes.item(index) as Element
            val actions = activity.getElementsByTagName("action")
            val categories = activity.getElementsByTagName("category")
            ActivityContract(
                name = activity.getAttributeNS(ANDROID_NS, "name"),
                exported = activity.getAttributeNS(ANDROID_NS, "exported") == "true",
                actions = (0 until actions.length)
                    .map { actions.item(it) as Element }
                    .map { it.getAttributeNS(ANDROID_NS, "name") }
                    .toSet(),
                categories = (0 until categories.length)
                    .map { categories.item(it) as Element }
                    .map { it.getAttributeNS(ANDROID_NS, "name") }
                    .toSet(),
            )
        }
    }

    private data class ActivityContract(
        val name: String,
        val exported: Boolean,
        val actions: Set<String>,
        val categories: Set<String>,
    )
}