package com.aliucord.gradle.generator

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class JarGenerator(val inputFile: File, val outputFile: File) {
    fun generate() {
        val outputJar = ZipWriter(outputFile.outputStream())
        val arscGenerator = ArscGenerator(inputFile)
        val stubGenerator = StubJarGenerator(listOf(inputFile), outputJar)
        outputJar.saveFile("resources.arsc", arscGenerator.generateTableArsc())
        stubGenerator.use(StubJarGenerator::generateStub)
        outputJar.close()
    }

}
public class ZipWriter(
    outputStream: OutputStream
): ZipOutputStream(outputStream) {

    public fun saveFile(fileName: String, data: ByteArray) {
        val entry = ZipEntry(fileName)
        createDirEntryRecursively(fileName)
        this.putNextEntry(entry)
        this.write(data)
        this.closeEntry()
    }
    private fun createDirEntryRecursively(filePath: String) {
        val parts = filePath.split('/').dropLast(1)
        if (parts.isEmpty()) return

        val sb = StringBuilder()

        for (i in parts.indices) {
            sb.append(parts[i]).append('/')

            val dirPath = sb.toString()

            if (!createdDirs.add(dirPath)) continue

            this.putNextEntry(ZipEntry(dirPath))
            this.closeEntry()
        }
    }
    private val createdDirs = mutableSetOf<String>()
}
