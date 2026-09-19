import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "sushi.hardcore.droidfs"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        // Match DroidFS' original universal build explicitly instead of relying on
        // AGP's default native ABI set.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        buildConfigField("boolean", "CRYFS_DISABLED", "true")
        buildConfigField("boolean", "GOCRYPTFS_DISABLED", "false")
        resValue("string", "versionName", "2.3.2")

        externalNativeBuild {
            cmake {
                arguments += listOf("-DCRYFS=OFF")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../third_party/droidfs/app/src/main/java")
            res.srcDir("../third_party/droidfs/app/src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
            // DroidFS upstream keeps androidx/camera/video/originals/** only as a
            // reference snapshot of CameraX. The upstream app excludes it from compilation;
            // VaultShelf mirrors that rule below at the JavaCompile task level.
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        // This module compiles a pinned upstream DroidFS source snapshot. Keep lint reports
        // available for review, but do not make VaultShelf CI own DroidFS' pre-existing
        // lint backlog. The final app module remains warningsAsErrors + abortOnError.
        checkDependencies = false
        abortOnError = false
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

// DroidFS upstream excludes this reference snapshot from its own source set. Our Android
// library source-set API does not expose Groovy's exclude DSL directly, so apply the same
// exclusion to every Java compile task. This prevents duplicate CameraX classes (for example
// androidx.camera.video.Recording) from entering the library jar used by release R8.
tasks.withType<JavaCompile>().configureEach {
    exclude("androidx/camera/video/originals/**")
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.material:material:1.14.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    implementation("io.coil-kt.coil3:coil:3.4.0")
    implementation("io.coil-kt.coil3:coil-video:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-datasource:1.10.1")

    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-extensions:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    implementation("com.google.auto.value:auto-value-annotations:1.11.1")
    annotationProcessor("com.google.auto.value:auto-value:1.11.1")
}


val applyVaultShelfDroidFsPatch = tasks.register("applyVaultShelfDroidFsPatch") {
    val upstreamDir = rootProject.file("third_party/droidfs")
    val patchFile = rootProject.file("patches/droidfs-vaultshelf-file-routing.patch")
    inputs.file(patchFile)

    doLast {
        // This task can run more than once in a single Gradle invocation (tests, lint,
        // debug, release). Probe the already-applied state first and silence expected
        // git-apply check failures so CI logs only contain real patch errors.
        val reverseOutput = ByteArrayOutputStream()
        val alreadyApplied = project.exec {
            workingDir(upstreamDir)
            commandLine("git", "apply", "--reverse", "--check", patchFile.absolutePath)
            isIgnoreExitValue = true
            standardOutput = reverseOutput
            errorOutput = reverseOutput
        }
        if (alreadyApplied.exitValue != 0) {
            val checkOutput = ByteArrayOutputStream()
            val check = project.exec {
                workingDir(upstreamDir)
                commandLine("git", "apply", "--check", patchFile.absolutePath)
                isIgnoreExitValue = true
                standardOutput = checkOutput
                errorOutput = checkOutput
            }
            check(check.exitValue == 0) {
                "Pinned DroidFS source no longer matches the reviewed VaultShelf routing patch\n" + checkOutput.toString()
            }
            project.exec {
                workingDir(upstreamDir)
                commandLine("git", "apply", patchFile.absolutePath)
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(applyVaultShelfDroidFsPatch)
}
