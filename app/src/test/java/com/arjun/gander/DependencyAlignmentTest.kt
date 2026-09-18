package com.arjun.gander

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The three source-integrated Android applications must compile and package against the
 * same versions of shared UI/runtime libraries. Otherwise Gradle's highest-version-wins
 * resolution silently makes the APK runtime different from a source module's compile view.
 */
class DependencyAlignmentTest {

    private val repo = File("..")
    private val app = File(repo, "app/build.gradle.kts").readText()
    private val droidFs = File(repo, "droidfs-upstream/build.gradle.kts").readText()
    private val legado = File(repo, "legado-upstream/build.gradle.kts").readText()

    @Test
    fun media3IsAlignedAcrossAllThreeModules() {
        listOf(app, droidFs, legado).forEach { build ->
            assertThat(build).contains("androidx.media3:media3-exoplayer:1.10.1")
        }
        assertThat(app).contains("androidx.media3:media3-ui:1.10.1")
        assertThat(droidFs).contains("androidx.media3:media3-ui:1.10.1")
    }

    @Test
    fun sharedAndroidUiLibrariesUseReviewedVersions() {
        listOf(app, droidFs, legado).forEach { build ->
            assertThat(build).contains("androidx.core:core-ktx:1.18.0")
            assertThat(build).contains("androidx.appcompat:appcompat:1.7.1")
            assertThat(build).contains("com.google.android.material:material:1.14.0")
            assertThat(build).contains("androidx.recyclerview:recyclerview:1.4.0")
        }
        assertThat(app).contains("androidx.webkit:webkit:1.16.0")
        assertThat(legado).contains("androidx.webkit:webkit:1.16.0")
    }

    @Test
    fun droidFsAndLegadoUseCompatibleCurrentLifecycleGeneration() {
        assertThat(droidFs).contains("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
        assertThat(droidFs).contains("androidx.lifecycle:lifecycle-process:2.10.0")
        assertThat(legado).contains("androidx.lifecycle:lifecycle-common-java8:2.10.0")
        assertThat(legado).contains("androidx.lifecycle:lifecycle-service:2.10.0")
    }
    @Test
    fun legadoReleaseRulesKeepLocalRhinoReflectionWithoutKeepingOnlineStacks() {
        val rules = File(repo, "legado-upstream/consumer-rules.pro").readText()

        assertThat(rules).contains("implements io.legado.app.help.JsExtensions")
        assertThat(rules).contains("android.app.privatecompute.PccSandboxManager")
        assertThat(rules).contains("com.gemalto.jp2.JP2Decoder")
        assertThat(rules).doesNotContain("-keep class okhttp3.")
        assertThat(rules).doesNotContain("GSYBaseVideoPlayer")
    }

    @Test
    fun droidFsNativeModulePinsAllFourReviewedAndroidAbis() {
        listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").forEach { abi ->
            assertThat(droidFs).contains("\"$abi\"")
        }
        assertThat(droidFs).contains("ndkVersion = \"28.2.13676358\"")
    }

    @Test
    fun finalApplicationOwnsLegadoPackagingRules() {
        assertThat(app).contains("\"META-INF/*\"")
        assertThat(app).contains("\"tables/Transcoder_*.bin\"")
        assertThat(app).contains("\"kotlin/**/*.kotlin_builtins\"")
        assertThat(app).contains("keepDebugSymbols")
        assertThat(app).contains("\"**/libarchive-jni.so\"")
        assertThat(app).contains("\"**/librenderscript-toolkit.so\"")
    }


}
