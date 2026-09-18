pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("$rootDir/third_party/legado/third_party/maven")
            content {
                includeModule("org.htmlunit", "htmlunit-core-js")
            }
        }
        google()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroupByRegex("com\\.github.*") }
        }
        mavenCentral()
    }
}

rootProject.name = "Gander"
include(":app")
include(":legado-reader")
include(":legado-upstream")
