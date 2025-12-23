@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        maven {
            name = "janisslsmCanny"
            url = uri("https://mvn.janisslsm.id.lv/canny")
        }
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "janisslsmCanny"
            url = uri("https://mvn.janisslsm.id.lv/canny")
        }
        mavenCentral()
        google()
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
        }
    }
}

rootProject.name = "gradle-plugin"
