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

    kotlinOptions {
        jvmTarget = "21"
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../third_party/droidfs/app/src/main/java")
            res.srcDir("../third_party/droidfs/app/src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.exclude("androidx/camera/video/originals/**")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../third_party/droidfs/app/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    lint {
        checkDependencies = false
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

dependencies {
    implementation(project(":droidfs-pdfviewer"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.material:material:1.13.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")

    implementation("io.coil-kt.coil3:coil:3.4.0")
    implementation("io.coil-kt.coil3:coil-video:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")

    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-ui:1.8.1")
    implementation("androidx.media3:media3-datasource:1.8.1")

    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-extensions:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    implementation("com.google.auto.value:auto-value-annotations:1.11.1")
}
