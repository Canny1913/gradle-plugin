@file:Suppress("UnstableApiUsage")

import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.publish)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
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
    website.set("https://github.com/Canny1913/gradle-plugin")
    vcsUrl.set("https://github.com/Canny1913/gradle-plugin")
    plugins {
        create("aliucore-plugin") {
            id = "io.github.canny1913.aliucore.plugin"
            implementationClass = "com.aliucord.gradle.plugins.AliucordPluginGradle"
            description = "Plugin for compiling Aliucord plugins"
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
        create("aliucore-core") {
            id = "io.github.canny1913.aliucore.core"
            implementationClass = "com.aliucord.gradle.plugins.AliucordCoreGradle"
            description = "Plugin for compiling Aliucore core"
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
        create("aliucore-injector") {
            id = "io.github.canny1913.aliucore.injector"
            implementationClass = "com.aliucord.gradle.plugins.AliucordInjectorGradle"
            description = "Plugin for compiling Aliucore injector"
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

version = "2.3.1"
group = "io.github.canny1913"

tasks.shadowJar {
    archiveClassifier.set("")
}
