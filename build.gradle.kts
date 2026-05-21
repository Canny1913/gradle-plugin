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

    implementation(libs.jadx.core)
    implementation(libs.jadx.dexInput)
    implementation(libs.kotlinx.serialization)

    implementation(libs.asm)
    implementation(libs.asm.commons)
    implementation(libs.binaryResources)
    implementation(libs.androidx.collections)
}

gradlePlugin {
    plugins {
        create("aliucore-plugin") {
            id = "com.aliucore.plugin"
            implementationClass = "com.aliucord.gradle.plugins.AliucordPluginGradle"
        }
        create("aliucore-core") {
            id = "com.aliucore.core"
            implementationClass = "com.aliucord.gradle.plugins.AliucordCoreGradle"
        }
        create("aliucore-injector") {
            id = "com.aliucore.injector"
            implementationClass = "com.aliucord.gradle.plugins.AliucordInjectorGradle"
        }
    }
}

version = "2.4.0"

mavenPublishing {
    coordinates("com.aliucore", "gradle")
    configureBasedOnAppliedPlugins()
}

publishing {
    repositories {
        maven {
            name = "aliucore"
            url = uri("https://mvn.janisslsm.id.lv/canny")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
