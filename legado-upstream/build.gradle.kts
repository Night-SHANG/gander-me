plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.legado.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "Cronet_Version", "\"153.0.8010.27\"")
        buildConfigField("String", "Cronet_Main_Version", "\"153.0.0.0\"")

    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "../third_party/legado/app/src/main/java",
                "../third_party/legado/modules/book/src/main/java",
                "../third_party/legado/modules/rhino/src/main/java",
                "src/main/java",
            )
            res.srcDirs("../third_party/legado/app/src/main/res")
            assets.srcDirs("../third_party/legado/app/src/main/assets")
            // EPUB/XHTML parsing in modules/book loads these DTDs from the classpath.
            resources.srcDirs("../third_party/legado/modules/book/src/main/resources")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }

    packaging {
        resources.excludes += "META-INF/*"
    }

    lint {
        checkDependencies = false
        disable += setOf(
            "MissingTranslation",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.collection:collection:1.6.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.media:media:1.8.0")

    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("io.github.carguo:gsyvideoplayer-java:13.2.1")
    implementation("io.github.carguo:gsyvideoplayer-exo2:13.2.1") {
        exclude(group = "androidx.media3", module = "media3-cast")
    }
    implementation("com.github.CarGuo.DanmakuFlameMaster:DanmakuFlameMaster:0.9.25")

    implementation("com.louiscad.splitties:splitties-appctx:3.0.0")
    implementation("com.louiscad.splitties:splitties-systemservices:3.0.0")
    implementation("com.louiscad.splitties:splitties-views:3.0.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.github.michaellee123:LiveEventBus:1.8.14")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.jayway.jsonpath:json-path:3.0.0")
    implementation("cn.wanghaomiao:JsoupXpath:2.5.3")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.brotli:dec:0.1.2")
    implementation("com.google.protobuf:protobuf-javalite:3.25.9")

    implementation("org.htmlunit:htmlunit-core-js:5.3.0-legado.4")

    implementation("com.github.bumptech.glide:glide:5.0.7")
    implementation("com.github.bumptech.glide:okhttp3-integration:5.0.7")
    implementation("com.github.bumptech.glide:recyclerview-integration:5.0.7")
    ksp("com.github.bumptech.glide:ksp:5.0.7")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.github.qoqa:glide-svg:4.0.2")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.15.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }
    implementation("io.ktor:ktor-server-cio:3.5.2") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }

    implementation("com.github.jenly1314:zxing-lite:3.5.0")
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("me.zhanghai.android.libarchive:library:1.1.6")
    implementation("org.apache.commons:commons-text:1.13.1")

    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")

    implementation("com.github.liuyueyi.quick-chinese-transfer:quick-transfer-core:0.2.17")
    implementation("cn.hutool:hutool-crypto:5.8.22")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        exclude(group = "org.bouncycastle")
    }

    implementation("com.github.Moriafly:LyricViewX:1.3.2")
    implementation(platform("io.github.rosemoe:editor-bom:0.24.6"))
    implementation("io.github.rosemoe:editor")
    implementation("io.github.rosemoe:language-textmate")
    implementation("com.github.TomasValenta:renderscript-intrinsics-replacement-toolkit:8eaa829ddd")

    implementation(fileTree("../third_party/legado/app/cronetlib") {
        include("*.jar")
    })

    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.generateKotlin", "false")
    arg("room.schemaLocation", rootProject.file("third_party/legado/app/schemas").absolutePath)
}

val applyVaultShelfLegadoPatch = tasks.register("applyVaultShelfLegadoPatch") {
    val upstreamDir = rootProject.file("third_party/legado")
    val patchFile = rootProject.file("patches/legado-vaultshelf-runtime.patch")
    inputs.file(patchFile)

    doLast {
        val check = project.exec {
            workingDir(upstreamDir)
            commandLine("git", "apply", "--check", patchFile.absolutePath)
            isIgnoreExitValue = true
        }
        if (check.exitValue == 0) {
            project.exec {
                workingDir(upstreamDir)
                commandLine("git", "apply", patchFile.absolutePath)
            }
        } else {
            val alreadyApplied = project.exec {
                workingDir(upstreamDir)
                commandLine("git", "apply", "--reverse", "--check", patchFile.absolutePath)
                isIgnoreExitValue = true
            }
            check(alreadyApplied.exitValue == 0) {
                "Pinned Legado source no longer matches the reviewed VaultShelf runtime patch"
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(applyVaultShelfLegadoPatch)
}
