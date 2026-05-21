package com.aliucord.gradle.generator

import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

internal class ArscGenerator(val input: File) {
    var table: ResourceTableChunk
    var types: Map<Int, String> = hashMapOf()

    init {
        val apkInput = ZipFile(input)
        val arscFile = apkInput.getEntry("resources.arsc") ?: error("resources.arsc not found in discord apk")
        val arscStream = apkInput.getInputStream(arscFile)
        val byteBuffer = ByteBuffer.wrap(arscStream.readAllBytes()).apply { order(ByteOrder.LITTLE_ENDIAN) }
        table = ResourceTableChunk.newInstance(byteBuffer) as ResourceTableChunk
    }
    fun generateTableArsc(): ByteArray {
        table.packages.forEach { chunk ->
            chunk.typeChunks.forEach { chunk ->
                var i = 0
                generateSequence { chunk.getEntry(i) }.take(chunk.totalEntryCount).forEach { entry ->
                    chunk.overrideEntry(i++, TypeChunk.Entry(
                        entry.headerSize(),
                        entry.flags() or ENTRY_PUBLIC_FLAG,
                        entry.keyIndex(),
                        if (!entry.isComplex) entry.value() else null,
                        if (entry.isComplex) entry.values() else null,
                        entry.parentEntry(),
                        entry.parent()
                    ))
                }
            }
            chunk.typeSpecChunks.forEach {
                for (n in 0 until it.resourceCount) {
                    it.resourceFlags[n] = ENTRY_PUBLIC_SPEC_FLAG
                }
            }
        }
        return table.toByteArray()
    }

    private companion object {
        /**
         * Set if this is a public entry.
         */
        const val ENTRY_PUBLIC_FLAG = 0x0002

        /**
         * Additional flag indicating this is a public entry.
         */
        const val ENTRY_PUBLIC_SPEC_FLAG = 0x40000000

        fun TypeSpecChunk.findFlag(i: Int): Int? = try {
            this.getResourceFlags(i)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }
}
