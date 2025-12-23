/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliucord.gradle.task

import com.aliucord.gradle.getAndroid
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.sdklib.BuildToolInfo
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.internal.ExecActionFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URLClassLoader
import java.util.Locale
import javax.inject.Inject

/**
 * Compiles an Android project's resources using aapt, and outputs an apk containing no code.
 */
public abstract class CompileResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    public abstract val input: DirectoryProperty

    @get:InputFile
    public abstract val manifestFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    public abstract val aaptExecutable: Property<File>

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @get:Inject
    protected abstract val execActionFactory: ExecActionFactory

    private val androidJar: Provider<File>

    private var discordJar: File? = null
    private var customAapt: Boolean = false

    init {
        val classPathConfig = project.configurations.findByName("debugCompileClasspath")

        val files = classPathConfig?.incoming?.artifactView {
            lenient(true)
        }?.files

        discordJar = files?.find { file ->
            val dep = classPathConfig.resolvedConfiguration.resolvedArtifacts.find { it.file == file }
            dep?.moduleVersion?.id?.group == "com.discord" && dep.name == "discord"
        }

        val android = project.extensions.getAndroid()
        val sdkService =
            getBuildService<SdkComponentsBuildService, SdkComponentsBuildService.Parameters>(buildServiceRegistry = project.gradle.sharedServices)
        val sdkLoader = sdkService.map {
            it.sdkLoader(
                compileSdkVersion = project.provider { android.compileSdkVersion },
                buildToolsRevision = project.provider { android.buildToolsRevision },
            )
        }

        usesService(sdkService)
        androidJar = sdkLoader.flatMap { it.androidJarProvider }

        aaptExecutable.set(project.providers.provider {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

            val os = when {
                osName.contains("linux") -> "linux"
                else -> return@provider null
            }

            val arch = when (System.getProperty("os.arch")) {
                "aarch64" -> "arm64-v8a"
                "amd64" -> "x86_64"
                else -> return@provider null
            }
            // hacky but it works
            val aapt2 = this::class.java.classLoader.getResourceAsStream("$os/$arch/aapt2")
                ?: return@provider null

            val outFile = File.createTempFile("custom-aapt2", "").apply { setExecutable(true) }

            FileOutputStream(outFile).use { output ->
                aapt2.copyTo(output)
            }

            outFile
        })
        // use normal aapt2 if custom is not found
        if (!aaptExecutable.isPresent) {
            aaptExecutable.set(sdkLoader.flatMap { it.buildToolInfoProvider }
                .map { File(it.getPath(BuildToolInfo.PathId.AAPT2)) })
        } else {
            customAapt = true
        }
    }

    @TaskAction
    public fun compile() {
        val tmpRes = File.createTempFile("res", ".zip")

        execActionFactory.newExecAction().apply {
            executable = aaptExecutable.get().path
            args("compile")
            args("--dir", input.asFile.get().path)
            args("-o", tmpRes.path)
            execute()
        }

        execActionFactory.newExecAction().apply {
            executable = aaptExecutable.get().path
            args("link")
            args("-I", androidJar.get().path)
            if (customAapt) {
                args("-I", discordJar!!.path)
            }
            args("-R", tmpRes.path)
            args("--manifest", manifestFile.get().asFile.path)
            args("-o", outputFile.get().asFile.path)
            args("--auto-add-overlay")
            args("-v")
            args("--remap-package=app|com.discord") // todo: add extension fields for this
            execute()
        }

        tmpRes.delete()
    }
}
