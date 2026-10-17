@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    alias(libs.plugins.publish)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradle)
    compileOnly(libs.android.repository)
    compileOnly(libs.android.sdk)
    compileOnly(libs.android.sdklib)

    implementation(libs.dex2jar)
    implementation(libs.binaryResources)
    implementation(libs.androidx.collections)
    implementation(libs.kotlinx.serialization)
}

gradlePlugin {
    plugins {
        create("aliucore-plugin") {
            id = "io.github.canny1913.plugin"
            implementationClass = "com.aliucord.gradle.plugins.AliucordPluginGradle"
        }
        create("aliucore-core") {
            id = "io.github.canny1913.core"
            implementationClass = "com.aliucord.gradle.plugins.AliucordCoreGradle"
        }
        create("aliucore-injector") {
            id = "io.github.canny1913.injector"
            implementationClass = "com.aliucord.gradle.plugins.AliucordInjectorGradle"
        }
    }
}

version = "2.3.1"

mavenPublishing {
    coordinates("io.github.canny1913", "gradle")
    configureBasedOnAppliedPlugins()
}

publishing {
    repositories {
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
            credentials {
                username = System.getenv("MAVEN_RELEASES_USERNAME")
                password = System.getenv("MAVEN_RELEASES_PASSWORD")
            }
        }
        maven {
            name = "aliucordSnapshots"
            url = uri("https://maven.aliucord.com/snapshots")
            credentials {
                username = System.getenv("MAVEN_SNAPSHOTS_USERNAME")
                password = System.getenv("MAVEN_SNAPSHOTS_PASSWORD")
            }
        }
    }
}
