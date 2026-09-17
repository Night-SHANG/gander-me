plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vaultshelf.legado.reader"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.15.0")

    // Legado's standalone EPUB/local-book parser module. Pin to the exact upstream
    // commit audited for this reader migration instead of following a moving branch.
    implementation("com.github.LegadoTeam.legado:book:62003ce732a7e30602754d28996da7f98b9ea296")
    // Match the jsoup release used by the pinned Legado source tree.
    implementation("org.jsoup:jsoup:1.23.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
