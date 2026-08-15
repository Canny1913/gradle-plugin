@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven {
            name = "aliucore"
            url = uri("https://mvn.janisslsm.id.lv/canny")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
        }
        maven {
            name = "aliucore"
            url = uri("https://mvn.janisslsm.id.lv/canny")
        }
    }
}

rootProject.name = "gradle-plugin"
