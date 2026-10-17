@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven {
            name = "aliucord-release"
            url = uri("https://maven.aliucord.com/releases")
        }
        maven {
            name = "aliucord-snapshots"
            url = uri("https://maven.aliucord.com/snapshots")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            name = "aliucord-release"
            url = uri("https://maven.aliucord.com/releases")
        }
        maven {
            name = "aliucord-snapshots"
            url = uri("https://maven.aliucord.com/snapshots")
        }
    }
}

rootProject.name = "gradle-plugin"
