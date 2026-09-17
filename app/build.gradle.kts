import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.arjun.gander"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arjun.gander"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["timeout_msec"] = "180000"
    }

    val releaseKeystore = rootProject.file("keystore/gander.jks")
    if (releaseKeystore.exists()) {
        val storePass = (findProperty("GANDER_STORE_PASSWORD") as String?) ?: "gander-local"
        val keyPass = (findProperty("GANDER_KEY_PASSWORD") as String?) ?: storePass
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = storePass
                keyAlias = "gander"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.inputs.files(
                    rootProject.file("CHANGELOG.md"),
                    file("proguard-rules.pro"),
                    rootProject.fileTree("fastlane/metadata/android") { include("**/*.txt") },
                    fileTree("src/main/res") { include("values*/strings.xml") },
                ).withPropertyName("releaseMetadata")
            }
        }
    }

    sourceSets {
        getByName("test") { resources.srcDir("../tests/fixtures/files") }
        getByName("androidTest") { assets.srcDir("../tests/fixtures/files") }
    }
}

val permissionAllowlistSuffixes = setOf(
    ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
)

androidComponents.onVariants { variant ->
    val suffix = variant.name.replaceFirstChar { it.uppercase() }
    val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
    val appId = variant.applicationId

    val checkPermissions = tasks.register("check${suffix}Permissions") {
        description = "Fails if the merged manifest requests any permission we did not sign off on."
        val manifestFile = mergedManifest
        val allowedSuffixes = permissionAllowlistSuffixes
        val applicationId = appId
        val stamp = layout.buildDirectory.file("reports/permissions/$suffix.txt")
        inputs.file(manifestFile)
        outputs.file(stamp)
        doLast {
            val requested = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
                .findAll(manifestFile.get().asFile.readText())
                .map { it.groupValues[1] }
                .toList()
            val allowed = allowedSuffixes.map { applicationId.get() + it }.toSet()
            val unexpected = requested.filterNot { it in allowed }
            if (unexpected.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Gander ships with no permissions, but $suffix requests:")
                        unexpected.forEach { appendLine("    $it") }
                        appendLine()
                        appendLine("A dependency added these. Either strip each one with")
                        appendLine("tools:node=\"remove\" in AndroidManifest.xml, or add it to")
                        append("permissionAllowlist in app/build.gradle.kts with a reason.")
                    }
                )
            }
            stamp.get().asFile.apply {
                parentFile.mkdirs()
                writeText(requested.joinToString("\n"))
            }
        }
    }

    tasks.matching { it.name == "assemble$suffix" || it.name == "bundle$suffix" }
        .configureEach { dependsOn(checkPermissions) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(project(":legado-reader"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("org.readium.kotlin-toolkit:readium-shared:3.1.2")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.1.2")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.1.2")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")

    androidTestImplementation("com.google.truth:truth:1.4.5")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.7.0")
}
