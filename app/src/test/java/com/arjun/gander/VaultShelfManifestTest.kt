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
    fun externalViewAndShareIntentsGoThroughTheUnifiedDispatcher() {
        val activities = activities()
        val dispatcher = activities.single { it.name == ".FileDispatchActivity" }
        val viewer = activities.single { it.name == ".ViewerActivity" }

        assertThat(dispatcher.exported).isTrue()
        assertThat(dispatcher.actions).contains("android.intent.action.VIEW")
        assertThat(dispatcher.actions).contains("android.intent.action.SEND")

        assertThat(viewer.exported).isFalse()
        assertThat(viewer.actions).isEmpty()
    }

    @Test
    fun internalBrowserAndVaultBridgeAreNotExported() {
        val activities = activities()
        val browser = activities.single { it.name == ".MainActivity" }
        val vaultBridge = activities.single { it.name == ".vault.VaultContentActivity" }

        assertThat(browser.exported).isFalse()
        assertThat(browser.actions).isEmpty()
        assertThat(vaultBridge.exported).isFalse()
        assertThat(vaultBridge.actions).isEmpty()
    }

    @Test
    fun dispatcherBridgesStayAliveAsTranslucentActivitiesRatherThanNoDisplay() {
        val manifest = MANIFEST.readText()

        assertThat(manifest).doesNotContain("@android:style/Theme.NoDisplay")
        assertThat(manifest).contains("@style/Theme.Gander.TransparentBridge")
        assertThat(manifest).doesNotContain("@android:style/Theme.Translucent.NoTitleBar")
    }

    @Test
    fun explorersUseOrdinaryOpaqueActivityWindowsAndKeepBottomNavigation() {
        val externalExplorer = activities().single { it.name == ".files.ExternalExplorerActivity" }
        val vaultExplorer = activities().single { it.name == ".files.VaultExplorerActivity" }
        assertThat(externalExplorer.theme).isEmpty()
        assertThat(vaultExplorer.theme).isEmpty()

        val manifest = MANIFEST.readText()
        val themes = File("../app/src/main/res/values/themes.xml").readText()
        assertThat(manifest).doesNotContain("Theme.Gander.ExplorerOverlay")
        assertThat(manifest).doesNotContain("Theme.Gander.PeerOverlay")
        assertThat(themes).doesNotContain("Theme.Gander.ExplorerOverlay")
        assertThat(themes).doesNotContain("Theme.Gander.PeerOverlay")

        val layout = File("../app/src/main/res/layout/activity_explorer.xml").readText()
        val bottomNavigation = layout.indexOf("@+id/vaultshelf_explorer_bottom_nav")
        assertThat(bottomNavigation).isAtLeast(0)
        assertThat(layout).contains("android:background=\"?attr/colorSurface\"")
        assertThat(layout).doesNotContain("@android:color/transparent")
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
                theme = activity.getAttributeNS(ANDROID_NS, "theme"),
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
        val theme: String,
        val actions: Set<String>,
        val categories: Set<String>,
    )
}