@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
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
        val ghUsername = System.getenv("GITHUB_ACTOR")
            ?: providers.gradleProperty("gpr.user").orNull
        val ghPassword = System.getenv("GITHUB_TOKEN")
            ?: providers.gradleProperty("gpr.key").orNull
        if (ghUsername != null && ghPassword != null) {
            maven {
                name = "GitHubPackages"
                url = uri(
                    "https://maven.pkg.github.com/Canny1913/binary-resources"
                )

                credentials {
                    username = ghUsername
                    password = ghPassword
                }
            }
        }
        mavenLocal()
    }
}

rootProject.name = "gradle-plugin"
