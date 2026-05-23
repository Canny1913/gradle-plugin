package com.aliucord.gradle.generator

import jadx.api.*
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.annotations.*
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.*
import jadx.plugins.input.dex.DexInputPlugin
import org.objectweb.asm.*
import java.io.Closeable
import java.io.File


internal class StubJarGenerator(
    val inputFiles: List<File>,
    val classesOutputJar: ZipWriter
): Closeable {

    private var decompiler: JadxDecompiler
    private lateinit var innerClasses: HashMap<String, ClassNode>

    private companion object {
        const val ACC_VISIBILITY_FLAGS: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED
        const val ACC_DEX_HIDDEN_BIT: Int = 0x00000020 // field, method (not native)
        const val ACC_DEX_HIDDEN_BIT_NATIVE: Int = 0x00000200 // method (native)
        const val ACC_CONSTRUCTOR: Int = 0x10000 // constructor method (class or instance initializer)
        const val ACC_DECLARED_SYNCHRONIZED: Int = 0x20000
    }

    // todo: this is extremely slow
    init {
        val jadxArgs = JadxArgs()
        jadxArgs.inputFiles = inputFiles
        jadxArgs.isDeobfuscationOn = false
        jadxArgs.isSkipResources = true
        jadxArgs.isSkipSources = true
        jadxArgs.isExportAsGradleProject = false
        jadxArgs.decompilationMode = DecompilationMode.FALLBACK
        jadxArgs.pluginLoader = object : JadxPluginLoader {
            override fun load(): List<JadxPlugin?> = listOf(DexInputPlugin())
            override fun close() {}
        }
        decompiler = JadxDecompiler(jadxArgs)
        decompiler.load()
    }

    fun generateStub() {
        val rootNode = decompiler.root
        val classes = rootNode.getClasses(true)
        innerClasses = HashMap.newHashMap(classes.size)
        innerClasses.putAll(classes.filter(ClassNode::isInner).associateBy { it.rawName })
        classes.forEach(::generateStubFromClassNode)
    }

    override fun close() {
        decompiler.close()
    }

    private fun argTypeToDescriptor(type: ArgType): String {
        if (type.isPrimitive) {
            return type.primitiveType.shortName
        } else if (type.isArray) {
            return "[" + argTypeToDescriptor(type.arrayRootElement)
        }

        return "L" + type.getObject().replace('.', '/') + ";"
    }


    private fun resolveInnerName(node: ClassNode): String? {
        val attr = node.get(JadxAttrType.INNER_CLASSES)
        if (attr != null) {
            return attr.map.values.firstOrNull()?.name
        }
        if (!node.isAnonymous) return node.name
        return null
    }

    private fun emitEncodedValue(av: AnnotationVisitor, name: String?, value: EncodedValue) {
        when (value.type) {
            EncodedType.ENCODED_NULL -> { /* nothing to emit */ }
            EncodedType.ENCODED_ARRAY -> {
                val arr = av.visitArray(name) ?: return
                @Suppress("UNCHECKED_CAST")
                val list = value.value as List<EncodedValue>
                for (el in list) emitEncodedValue(arr, null, el)
                arr.visitEnd()
            }
            EncodedType.ENCODED_ANNOTATION -> {
                val ann = value.value as IAnnotation
                val nested = av.visitAnnotation(name, ann.annotationClass) ?: return
                for ((k, v) in ann.values) emitEncodedValue(nested, k, v)
                nested.visitEnd()
            }
            EncodedType.ENCODED_TYPE -> {
                av.visit(name, Type.getType(value.value as String))
            }
            EncodedType.ENCODED_ENUM, EncodedType.ENCODED_FIELD -> {
                val ref = value.value as IFieldRef
                av.visitEnum(name, ref.parentClassType, ref.name)
            }
            EncodedType.ENCODED_METHOD,
            EncodedType.ENCODED_METHOD_TYPE,
            EncodedType.ENCODED_METHOD_HANDLE -> {
                // Not representable as annotation values; ignore silently.
            }
            else -> {
                av.visit(name, value.value)
            }
        }
    }

    private fun emitAnnotations(
        attr: AnnotationsAttr?,
        visit: (descriptor: String, visible: Boolean) -> AnnotationVisitor?
    ) {
        if (attr == null) return
        for (ann in attr.all) {
            if (ann.visibility == AnnotationVisibility.SYSTEM) continue
            if (ann.annotationClass == "Lkotlin/Metadata;") continue
            val visible = ann.visibility != AnnotationVisibility.BUILD
            val av = visit(ann.annotationClass, visible) ?: continue
            for ((k, v) in ann.values) emitEncodedValue(av, k, v)
            av.visitEnd()
        }
    }

    private fun clearClassAccess(isInner: Boolean, access: Int): Int {
        var access = access
        if ((access and Opcodes.ACC_INTERFACE) == 0) { // issue 55
            access = access or Opcodes.ACC_SUPER // 解决生成的class文件使用dx重新转换时使用的指令与原始指令不同的问题
        }
        // access in classes have no acc_static or acc_private
        access = access and (Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE).inv()
        if (isInner && (access and Opcodes.ACC_PROTECTED) != 0) {
            // protected inner classes are public
            access = access and Opcodes.ACC_PROTECTED.inv()
            access = access or Opcodes.ACC_PUBLIC
        }
        access = access and ACC_DECLARED_SYNCHRONIZED.inv() // clean ACC_DECLARED_SYNCHRONIZED
        access = access and Opcodes.ACC_SYNTHETIC.inv() // clean ACC_SYNTHETIC
        access = access and Opcodes.ACC_FINAL.inv()
        return access
    }

    private fun clearInnerAccess(access: Int): Int {
        var access = access
        access = access and (Opcodes.ACC_SUPER.inv()) // inner class attr has no acc_super
        if (0 != (access and Opcodes.ACC_PRIVATE)) { // clear public/protected if it is private
            access = access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED).inv()
        } else if (0 != (access and Opcodes.ACC_PROTECTED)) { // clear public if it is protected
            access = access and (Opcodes.ACC_PUBLIC).inv()
        }
        access = access and Opcodes.ACC_SYNTHETIC.inv() // clean ACC_SYNTHETIC
        return access
    }

    private fun isPowerOfTwo(i: Int): Boolean {
        return (i and (i - 1)) == 0
    }

    private fun removeHiddenAccess(accessFlags: Int): Int {
        // Refer to art/libdexfile/dex/hidden_api_access_flags.h
        var accessFlags = accessFlags
        if (!isPowerOfTwo(accessFlags and ACC_VISIBILITY_FLAGS)) {
            accessFlags = accessFlags xor ACC_VISIBILITY_FLAGS
        }
        accessFlags =
            accessFlags and (if ((accessFlags and Opcodes.ACC_NATIVE) != 0) ACC_DEX_HIDDEN_BIT_NATIVE else ACC_DEX_HIDDEN_BIT).inv()
        return accessFlags
    }

    private fun generateStubFromClassNode(classNode: ClassNode)
    {
        val enclosingAnn = classNode.disassembledCode.contains("EnclosingMethod")
        val fields = classNode.fields
        val methods = classNode.methods


        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val isInner = classNode.isInner
        val internalName = classNode.rawName.replace('.', '/')
        val outerClass = classNode.parentClass

        val ownDalvikInnerName = if (isInner) resolveInnerName(classNode) else null
        val hasEnclosingMethod = isInner && enclosingAnn
        val isClosure = isInner && hasEnclosingMethod && ownDalvikInnerName == null

        val interfaces: MutableList<String> = ArrayList()
        classNode.interfaces.forEach {
            interfaces.add(it.getObject().replace(".", "/"))
        }

        val superClass = classNode.superClass ?: if (isInner) { ArgType.OBJECT } else null

        val interfacesArray = interfaces.toTypedArray<String>()
        val signature = classNode.get(JadxAttrType.SIGNATURE)

        val rawAccess = classNode.accessFlags.rawValue()
        val isInterfaceLike = (rawAccess and Opcodes.ACC_INTERFACE) != 0
        val access = clearClassAccess(isInner, rawAccess)

        cw.visit(
            Opcodes.V1_6,
            access,
            internalName,
            signature?.signature,
            superClass?.`object`?.replace('.', '/'),
            interfacesArray
        )

        emitAnnotations(classNode.get(JadxAttrType.ANNOTATION_LIST)) { d, v ->
            cw.visitAnnotation(d, v)
        }

        if (!isClosure) {
            val emitted = LinkedHashMap<String, Triple<String?, String?, Int>>()

            fun record(node: ClassNode) {
                val nodeInternal = node.rawName.replace('.', '/')
                if (emitted.containsKey(nodeInternal)) return
                val nodeName = resolveInnerName(node)
                val nodeAccess = clearInnerAccess(node.accessFlags.rawValue())
                val parent = node.parentClass
                if (nodeName != null && parent != null) {
                    emitted[nodeInternal] = Triple(parent.rawName.replace('.', '/'), nodeName, nodeAccess)
                } else {
                    emitted[nodeInternal] = Triple(null, null, nodeAccess)
                }
            }

            if (isInner) {
                var cur: ClassNode? = classNode
                while (cur != null && cur.isInner) {
                    record(cur)
                    cur = cur.parentClass
                }
            }


            val stack = ArrayDeque<ClassNode>()
            for (child in classNode.innerClasses) stack.addLast(child)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                record(cur)
                for (child2 in cur.innerClasses) stack.addLast(child2)
            }

            if (classNode.superClass?.`object` != ArgType.OBJECT.`object`) {
                val innerSuper = innerClasses[classNode.superClass?.`object`]?.takeIf(ClassNode::isInner)
                innerSuper?.let { record(it) }
            }

            if (classNode.interfaces.isNotEmpty()) {
                classNode.interfaces.map(ArgType::getObject).mapNotNull(innerClasses::get).filter(ClassNode::isInner).forEach {
                    record(it)
                }
            }
            for ((inner, triple) in emitted) {
                cw.visitInnerClass(inner, triple.first, triple.second, triple.third)
            }
        }
        cw.visitSource((if (isInner) outerClass else classNode)!!.alias + ".java", null)

        for (m in methods) {
            addMethodStub(m, cw, classNode)
        }

        for (f in fields) {
            addFieldStub(f, cw)
        }

        if (!isInterfaceLike && classNode.searchMethodByShortName("<init>") == null)
        {
            val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            ctor.visitCode()
            ctor.visitVarInsn(Opcodes.ALOAD, 0)
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            ctor.visitInsn(Opcodes.RETURN)
            ctor.visitMaxs(1, 1)
            ctor.visitEnd()
        }

        cw.visitEnd()

        val fileName = internalName.replace(".", "/") + ".class"
        classesOutputJar.saveFile(fileName, cw.toByteArray())
    }

    private fun addFieldStub(fieldNode: FieldNode, writer: ClassWriter) {
        var access = removeHiddenAccess(fieldNode.accessFlags.rawValue())
        access = access and (ACC_DECLARED_SYNCHRONIZED or Opcodes.ACC_SYNTHETIC).inv()

        val value = fieldNode.get(JadxAttrType.CONSTANT_VALUE)
        val signature = fieldNode.get(JadxAttrType.SIGNATURE)
        val desc: String = argTypeToDescriptor(fieldNode.type)
        val fv = writer.visitField(access, fieldNode.name, desc, signature?.signature, value?.value)
        emitAnnotations(fieldNode.get(JadxAttrType.ANNOTATION_LIST)) { d, v ->
            fv.visitAnnotation(d, v)
        }
        fv.visitEnd()
    }

    private fun addMethodStub(method: MethodNode, writer: ClassWriter, owner: ClassNode) {
        var access = removeHiddenAccess(method.rawAccessFlags)
        val cleanFlag: Int =
            (ACC_DECLARED_SYNCHRONIZED or ACC_CONSTRUCTOR or Opcodes.ACC_SYNTHETIC).inv()
        access = access and cleanFlag

        val name = method.name
        var methodDesc: String = method.methodInfo.makeSignature(true)
        methodDesc = methodDesc.substring(methodDesc.indexOf(name) + name.length)
        val signature = method.get(JadxAttrType.SIGNATURE)

        val exceptionsAttr = method.get(JadxAttrType.EXCEPTIONS)
        val exceptions: Array<String>? = exceptionsAttr?.list?.map { desc ->
            if (desc.startsWith("L") && desc.endsWith(";")) desc.substring(1, desc.length - 1)
            else desc
        }?.toTypedArray()

        val mv = writer.visitMethod(access, method.name, methodDesc, signature?.signature, exceptions)

        if ((owner.accessFlags.rawValue() and Opcodes.ACC_ANNOTATION) != 0) {
            val def = method.get(JadxAttrType.ANNOTATION_DEFAULT)
            if (def != null) {
                val dav = mv.visitAnnotationDefault()
                if (dav != null) {
                    emitEncodedValue(dav, null, def.value)
                    dav.visitEnd()
                }
            }
        }

        emitAnnotations(method.get(JadxAttrType.ANNOTATION_LIST)) { desc, visible ->
            mv.visitAnnotation(desc, visible)
        }

        val params = method.get(JadxAttrType.ANNOTATION_MTH_PARAMETERS)
        if (params != null) {
            val list = params.paramList
            for (i in list.indices) {
                emitAnnotations(list[i]) { desc, visible ->
                    mv.visitParameterAnnotation(i, desc, visible)
                }
            }
        }

        if ((access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE)) != 0) {
            mv.visitEnd()
            return
        }

        // add stub exception
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("stub")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }
}
