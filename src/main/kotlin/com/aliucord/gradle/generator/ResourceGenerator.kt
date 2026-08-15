package com.aliucord.gradle.generator

import com.aliucord.gradle.transformers.ZipWriter
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

internal class ResourceGenerator(val input: File, val output: ZipWriter) {
    var table: ResourceTableChunk
    var types: Map<Int, String> = hashMapOf()

    init {
        val apkInput = ZipFile(input)
        val arscFile = apkInput.getEntry("resources.arsc") ?: error("resources.arsc not found in discord apk")
        val arscStream = apkInput.getInputStream(arscFile)
        val byteBuffer = ByteBuffer.wrap(arscStream.readAllBytes()).apply { order(ByteOrder.LITTLE_ENDIAN) }
        table = ResourceTableChunk.newInstance(byteBuffer) as ResourceTableChunk
    }
    fun generateTableArsc() {
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
        output.saveFile("resources.arsc", table.toByteArray())
    }

    fun generateRClass() {
        val resCw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL
        val typesAndEntries = hashMapOf<String, Map<String, Int>>()
        table.packages.forEach { packageChunk ->
            packageChunk.typeChunks.filter { it.configuration.isDefault }.forEach { typeChunk ->
                val typeName = typeChunk.typeName
                val entryMap = hashMapOf<String, Int>()
                for (i in 0..typeChunk.totalEntryCount) {
                    val entry = typeChunk.getEntry(i) ?: continue
                    val id = (packageChunk.id shl 24) or (typeChunk.id shl 16) or entry.keyIndex()
                    entryMap[entry.key()] = id
                }
                typesAndEntries[typeName] = entryMap
            }
        }

        // we don't need to create a new base R class since it already exists for some reason

        val rClassName = "com/discord/R"
        resCw.visit(
            Opcodes.V1_6,
            access,
            rClassName,
            null,
            "java/lang/Object",
            null
        )
        typesAndEntries.forEach { (type, entries) ->
            resCw.visitInnerClass($$"com/discord/R$$$type", "com/discord/R", type, access)
            val rClassTypeName = $$"com/discord/R$$$type"

            val resTypeCw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            resTypeCw.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                rClassTypeName,
                null,
                "java/lang/Object",
                null
            )
            resTypeCw.visitInnerClass($$"com/discord/R$$$type", "com/discord/R", type, access)
            // constructor
            val mv = resTypeCw.visitMethod(
                Opcodes.ACC_PRIVATE,
                "<init>",
                "()V",
                null,
                null
            )
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false
            )
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
            // res fields
            entries.forEach { (name, value) ->
                resTypeCw.visitField(access, name, "I", null, value).visitEnd()
            }
            resTypeCw.visitEnd()
            output.saveFile("$rClassTypeName.class", resTypeCw.toByteArray())
        }
        resCw.visitEnd()
        output.saveFile("$rClassName.class", resCw.toByteArray())
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
