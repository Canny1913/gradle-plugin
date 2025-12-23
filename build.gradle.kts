@file:Suppress("UnstableApiUsage")

import java.util.Locale


plugins {
    `kotlin-dsl`
    alias(libs.plugins.publish)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradle)
    compileOnly(libs.android.repository)
    compileOnly(libs.android.sdk)
    compileOnly(libs.android.sdklib)

    implementation(libs.dex2jar)
    implementation(libs.jadx.core)
    implementation(libs.jadx.dexInput)
    implementation(libs.kotlinx.serialization)

    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

    when {
        osName.contains("linux") -> implementation("com.canny1913:aapt2-linux:+")
        else -> logger.warn("Cannot enable custom AAPT2 on this OS!")
    }


}

gradlePlugin {
    plugins {
        create("aliucord-plugin") {
            id = "com.aliucord.plugin"
            implementationClass = "com.aliucord.gradle.plugins.AliucordPluginGradle"
        }
        create("aliucord-core") {
            id = "com.aliucord.core"
            implementationClass = "com.aliucord.gradle.plugins.AliucordCoreGradle"
        }
        create("aliucord-injector") {
            id = "com.aliucord.injector"
            implementationClass = "com.aliucord.gradle.plugins.AliucordInjectorGradle"
        }
    }
}

version = "2.3.0"

mavenPublishing {
    coordinates("com.aliucord", "gradle")
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
